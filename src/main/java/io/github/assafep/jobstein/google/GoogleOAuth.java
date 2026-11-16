package io.github.assafep.jobstein.google;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import io.github.assafep.jobstein.security.SecretStore;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class GoogleOAuth {
    private static final String CLIENT = "secret://google/client";
    private static final String REFRESH = "secret://google/refresh";
    public static final String SCOPES = "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.compose https://www.googleapis.com/auth/calendar.events.readonly";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SecretStore secrets;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    public GoogleOAuth(SecretStore secrets) { this.secrets = secrets; }
    public void importClient(Path path, String expectedProject) throws IOException {
        JsonNode client = JSON.readTree(path.toFile()).path("installed");
        if (!client.path("client_id").isTextual() || !client.path("client_secret").isTextual()
            || !expectedProject.equals(client.path("project_id").asText()))
            throw new IOException("Expected desktop OAuth client for selected GCP project");
        if (secrets.get(REFRESH).isPresent()) {
            var previous = secrets.get(CLIENT);
            if (previous.isEmpty() || !JSON.readTree(previous.get()).path("client_id").asText().equals(client.path("client_id").asText()))
                throw new IOException("Cannot replace OAuth client while a refresh token exists");
        }
        secrets.put(CLIENT, JSON.writeValueAsBytes(client));
    }
    private JsonNode client() throws IOException {
        return JSON.readTree(secrets.get(CLIENT).orElseThrow(() -> new IOException("Import desktop OAuth client first")));
    }
    private static String random() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String form(Map<String,String> pairs) {
        return pairs.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    private JsonNode exchange(Map<String,String> fields) throws IOException, InterruptedException {
        var response = http.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
            .timeout(Duration.ofSeconds(40)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200) throw new IOException("Google token endpoint rejected request (HTTP "+response.statusCode()+")");
        return JSON.readTree(response.body());
    }
    private static String required(JsonNode node,String key) throws IOException {
        String value=node.path(key).asText("");
        if(value.isBlank()) throw new IOException("Missing OAuth response field: "+key);
        return value;
    }
    public void authorize(Consumer<String> display) throws Exception {
        var client=client(); String verifier=random(), state=random();
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var result=new CompletableFuture<String>();
        String redirect="http://127.0.0.1:"+server.getAddress().getPort()+"/callback";
        server.createContext("/callback", exchange -> {
            int status=400; String message="Authorization was not accepted.";
            try {
                var args=new HashMap<String,String>();
                String query=exchange.getRequestURI().getRawQuery();
                if(query!=null) for(String pair:query.split("&")) {
                    String[] parts=pair.split("=",2);
                    if(parts.length==2) args.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
                }
                if(state.equals(args.get("state"))) {
                    if(args.containsKey("code") && !args.get("code").isBlank()) {
                        result.complete(args.get("code")); status=200; message="Authorization received. Return to Jobstein.";
                    } else if(args.containsKey("error")) result.completeExceptionally(new IOException("Google authorization denied"));
                }
                byte[] bytes=message.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
                exchange.sendResponseHeaders(status,bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            String url="https://accounts.google.com/o/oauth2/v2/auth?"+form(Map.of(
                "client_id",required(client,"client_id"),"redirect_uri",redirect,"response_type","code","scope",SCOPES,
                "access_type","offline","prompt","consent","state",state,"code_challenge",challenge,"code_challenge_method","S256"));
            display.accept(url);
            String code=result.get(5,TimeUnit.MINUTES);
            JsonNode token=exchange(Map.of("client_id",required(client,"client_id"),"client_secret",required(client,"client_secret"),
                "code",code,"code_verifier",verifier,"redirect_uri",redirect,"grant_type","authorization_code"));
            Set<String> granted=Set.copyOf(Arrays.asList(required(token,"scope").split(" ")));
            var requiredScopes=Arrays.stream(SCOPES.split(" ")).filter(s->s.startsWith("https://") && !s.endsWith("userinfo.email")).toList();
            if(!granted.containsAll(requiredScopes)) throw new IOException("Required Google permissions were not granted");
            secrets.put(REFRESH,required(token,"refresh_token").getBytes(StandardCharsets.UTF_8));
        } finally { server.stop(0); }
    }
    public String accessToken() throws IOException, InterruptedException {
        var client=client();
        String refresh=new String(secrets.get(REFRESH).orElseThrow(()->new IOException("Run google authorize first")),StandardCharsets.UTF_8);
        return required(exchange(Map.of("client_id",required(client,"client_id"),"client_secret",required(client,"client_secret"),
            "refresh_token",refresh,"grant_type","refresh_token")),"access_token");
    }
}
