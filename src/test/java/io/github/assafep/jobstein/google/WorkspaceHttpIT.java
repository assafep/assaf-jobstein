package io.github.assafep.jobstein.google;
import io.github.assafep.jobstein.testing.MockHttp;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import io.github.assafep.jobstein.storage.ResumeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceHttpIT {
    @TempDir Path root;
    private GoogleOAuth oauth(MockHttp server)throws Exception{
        var secrets=new PosixFileSecretStore(root.resolve("secrets"));
        secrets.put("secret://google/client","{\"client_id\":\"synthetic-client\",\"client_secret\":\"synthetic-secret\"}".getBytes(StandardCharsets.UTF_8));
        secrets.put("secret://google/refresh","synthetic-refresh".getBytes(StandardCharsets.UTF_8));
        return new GoogleOAuth(secrets,server.origin());
    }
    @Test void refreshesOn401AndRetriesOnlyReads()throws Exception{
        var refreshes=new AtomicInteger();var reads=new AtomicInteger();var writes=new AtomicInteger();
        try(var server=new MockHttp(exchange->{
            String path=exchange.getRequestURI().getPath();
            if(path.equals("/token")){
                assertEquals("POST",exchange.getRequestMethod());
                assertTrue(MockHttp.body(exchange).contains("grant_type=refresh_token"));
                MockHttp.reply(exchange,200,"{\"access_token\":\"token-"+refreshes.incrementAndGet()+"\",\"expires_in\":3600}");
            }else if(path.equals("/v1/userinfo")){
                int count=reads.incrementAndGet();
                assertEquals("Bearer token-"+count,exchange.getRequestHeaders().getFirst("Authorization"));
                MockHttp.reply(exchange,count==1?401:200,count==1?"{}":"{\"sub\":\"synthetic\"}");
            }else{
                assertEquals("POST",exchange.getRequestMethod());writes.incrementAndGet();
                MockHttp.reply(exchange,401,"{}");
            }
        })){
            var client=new WorkspaceClient(oauth(server),server.origin());
            assertEquals("synthetic",client.userInfo().path("sub").asText());
            assertEquals(2,refreshes.get());
            assertThrows(WorkspaceClient.WorkspaceHttpException.class,()->client.sendDraft("draft-id"));
            assertEquals(1,writes.get());
            assertEquals(2,refreshes.get());
        }
    }
    @Test void matchesChecksumBeforeRecoveringConflict()throws Exception{
        byte[] data="synthetic-content".getBytes(StandardCharsets.UTF_8);
        var writes=new AtomicInteger();
        try(var server=new MockHttp(exchange->{
            String path=exchange.getRequestURI().getPath();
            if(path.equals("/token"))MockHttp.reply(exchange,200,"{\"access_token\":\"token\",\"expires_in\":3600}");
            else if(exchange.getRequestMethod().equals("POST")){
                writes.incrementAndGet();
                assertTrue(MockHttp.body(exchange).contains("\"id\":\"allocated\""));
                MockHttp.reply(exchange,409,"{}");
            }else{
                MockHttp.reply(exchange,200,"{\"id\":\"allocated\",\"name\":\"fixture.json\",\"mimeType\":\"application/json\",\"parents\":[\"parent\"],\"trashed\":false,\"capabilities\":{\"canEdit\":true},\"sha256Checksum\":\""+ResumeDocument.sha256(data)+"\"}");
            }
        })){
            var client=new WorkspaceClient(oauth(server),server.origin());
            assertEquals("allocated",client.ensureUpload("parent","fixture.json","application/json",data,"allocated"));
            assertThrows(IOException.class,()->client.ensureUpload("parent","fixture.json","application/json",new byte[]{9},"allocated"));
            assertEquals(2,writes.get());
        }
    }
}
