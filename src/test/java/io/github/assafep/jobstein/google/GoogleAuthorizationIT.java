package io.github.assafep.jobstein.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import io.github.assafep.jobstein.testing.MockHttp;
import io.github.assafep.jobstein.validation.DiagnosticException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import io.github.assafep.jobstein.validation.Diagnostics;

class GoogleAuthorizationIT {
    @TempDir Path root;
    private static final String REF="secret://google/refresh";
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static Map<String,String> parameters(String query){
        var result=new HashMap<String,String>();
        for(String pair:query.split("&")){
            var parts=pair.split("=",2);
            result.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
        }
        return result;
    }
    private static void client(PosixFileSecretStore secrets)throws Exception{
        secrets.put("secret://google/client",bytes("{\"client_id\":\"synthetic-client\",\"client_secret\":\"synthetic-secret\"}"));
    }

    @Test void reportsAuthorizationDenied()throws Exception{
        var secrets=new PosixFileSecretStore(root.resolve("secrets")); client(secrets);
        try(var server=new MockHttp(exchange->{ fail("Token endpoint must not be called"); })){
            var oauth=new GoogleOAuth(secrets,server.origin());
            var error=assertThrows(DiagnosticException.class,()->oauth.authorize(url->{
                var auth=parameters(URI.create(url).getRawQuery());
                try{var connection=(HttpURLConnection)URI.create(auth.get("redirect_uri")+"?state="+auth.get("state")+"&error=access_denied").toURL().openConnection(); connection.getResponseCode();}
                catch(Exception e){throw new AssertionError(e);}
            },Optional.empty(),0,Duration.ofSeconds(1)));
            assertEquals(DiagnosticException.Code.GOOGLE_AUTH_DENIED,error.code());
        }
    }

    @Test void reportsCallbackTimeout()throws Exception{
        var secrets=new PosixFileSecretStore(root.resolve("secrets")); client(secrets);
        try(var server=new MockHttp(exchange->{ fail("Token endpoint must not be called"); })){
            var oauth=new GoogleOAuth(secrets,server.origin());
            var error=assertThrows(DiagnosticException.class,()->oauth.authorize(url->{},Optional.empty(),0,Duration.ofMillis(10)));
            assertEquals(DiagnosticException.Code.GOOGLE_AUTH_TIMEOUT,error.code());
        }
    }

    @Test void reportsTokenExchangeFailure()throws Exception{
        var secrets=new PosixFileSecretStore(root.resolve("secrets")); client(secrets);
        try(var server=new MockHttp(exchange->MockHttp.reply(exchange,400,"{\"error\":\"invalid_grant\"}"))){
            var oauth=new GoogleOAuth(secrets,server.origin());
            var error=assertThrows(DiagnosticException.class,()->oauth.authorize(url->{
                var auth=parameters(URI.create(url).getRawQuery());
                try{var connection=(HttpURLConnection)URI.create(auth.get("redirect_uri")+"?state="+auth.get("state")+"&code=synthetic-code").toURL().openConnection(); connection.getResponseCode();}
                catch(Exception e){throw new AssertionError(e);}
            },Optional.empty(),0,Duration.ofSeconds(1)));
            assertEquals(DiagnosticException.Code.GOOGLE_TOKEN_EXCHANGE_FAILED,error.code());
            var message=Diagnostics.describe(error);
            assertTrue(message.contains("invalid_grant"));
            assertTrue(message.contains("authorization code is expired"));
            assertFalse(message.contains("synthetic-secret"));
        }
    }
    @Test void verifiesPkceAndPreservesExistingTokenOnAccountMismatch()throws Exception{
        var secrets=new PosixFileSecretStore(root.resolve("secrets"));
        secrets.put("secret://google/client",bytes("{\"client_id\":\"synthetic-client\",\"client_secret\":\"synthetic-secret\"}"));
        secrets.put(REF,bytes("original-refresh"));
        var subject=new AtomicReference<>("wrong-account");
        var authorization=new AtomicReference<Map<String,String>>();
        var exchanges=new AtomicInteger();
        try(var server=new MockHttp(exchange->{
            if(exchange.getRequestURI().getPath().equals("/token")){
                exchanges.incrementAndGet();
                var form=parameters(MockHttp.body(exchange));
                var auth=authorization.get();
                assertEquals("authorization_code",form.get("grant_type"));
                assertEquals("synthetic-code",form.get("code"));
                assertEquals(auth.get("redirect_uri"),form.get("redirect_uri"));
                try{
                    var computed=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes(form.get("code_verifier"))));
                    assertEquals(auth.get("code_challenge"),computed);
                }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
                MockHttp.reply(exchange,200,new ObjectMapper().createObjectNode().put("access_token","synthetic-access").put("refresh_token","new-refresh").put("scope",GoogleOAuth.SCOPES).toString());
            }else{
                assertEquals("/v1/userinfo",exchange.getRequestURI().getPath());
                assertEquals("Bearer synthetic-access",exchange.getRequestHeaders().getFirst("Authorization"));
                MockHttp.reply(exchange,200,new ObjectMapper().createObjectNode().put("sub",subject.get()).toString());
            }
        })){
            var oauth=new GoogleOAuth(secrets,server.origin());
            java.util.function.Consumer<String> browser=url->{
                try{
                    var auth=parameters(URI.create(url).getRawQuery());authorization.set(auth);
                    assertEquals("S256",auth.get("code_challenge_method"));
                    var client=HttpClient.newHttpClient();
                    var wrong=client.send(HttpRequest.newBuilder(URI.create(auth.get("redirect_uri")+"?state=wrong&code=synthetic-code")).GET().build(),HttpResponse.BodyHandlers.ofString());
                    assertEquals(400,wrong.statusCode());
                    var valid=client.send(HttpRequest.newBuilder(URI.create(auth.get("redirect_uri")+"?state="+auth.get("state")+"&code=synthetic-code")).GET().build(),HttpResponse.BodyHandlers.ofString());
                    assertEquals(200,valid.statusCode());
                    assertEquals("no-store",valid.headers().firstValue("Cache-Control").orElseThrow());
                }catch(Exception e){throw new AssertionError(e);}
            };
            var error=assertThrows(DiagnosticException.class,()->oauth.authorize(browser,Optional.of("expected-account")));
            assertEquals(DiagnosticException.Code.ACCOUNT_MISMATCH,error.code());
            assertArrayEquals(bytes("original-refresh"),secrets.get(REF).orElseThrow());
            subject.set("expected-account");
            oauth.authorize(browser,Optional.of("expected-account"));
            assertArrayEquals(bytes("new-refresh"),secrets.get(REF).orElseThrow());
            assertEquals(2,exchanges.get());
        }
    }
}
