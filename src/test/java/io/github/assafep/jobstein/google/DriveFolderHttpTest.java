package io.github.assafep.jobstein.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import io.github.assafep.jobstein.testing.MockHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DriveFolderHttpTest {
    @TempDir Path root;
    private static final ObjectMapper JSON=new ObjectMapper();
    private WorkspaceClient client(MockHttp server)throws Exception{
        var secrets=new PosixFileSecretStore(root.resolve("secrets"));
        secrets.put("secret://google/client","{\"client_id\":\"fixture\",\"client_secret\":\"fixture\"}".getBytes(StandardCharsets.UTF_8));
        secrets.put("secret://google/refresh","fixture".getBytes(StandardCharsets.UTF_8));
        return new WorkspaceClient(new GoogleOAuth(secrets,server.origin()),server.origin());
    }
    private static ObjectNode folder(){
        var node=JSON.createObjectNode().put("id","allocated").put("name","fixture").put("mimeType","application/vnd.google-apps.folder").put("trashed",false);
        node.putArray("parents").add("actual-root");
        node.putObject("capabilities").put("canEdit",true);
        node.putObject("appProperties").put("jobsteinId","fixture-marker");
        return node;
    }
    @Test void recoversOnlyMatchingFolderAfterConflict()throws Exception{
        var metadata=new AtomicReference<>(folder());
        var posts=new AtomicInteger();
        try(var server=new MockHttp(exchange->{
            String path=exchange.getRequestURI().getPath();
            if(path.equals("/token"))MockHttp.reply(exchange,200,"{\"access_token\":\"fixture\",\"expires_in\":3600}");
            else if(exchange.getRequestMethod().equals("POST")){
                posts.incrementAndGet();
                var body=JSON.readTree(MockHttp.body(exchange));
                assertEquals("allocated",body.path("id").asText());
                assertFalse(body.has("parents"));
                assertEquals("fixture-marker",body.path("appProperties").path("jobsteinId").asText());
                MockHttp.reply(exchange,409,"{}");
            }else if(path.endsWith("/root"))MockHttp.reply(exchange,404,"{}");
            else if(path.equals("/drive/v3/files")){
                String query=java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                assertTrue(query.contains("'root' in parents"));
                assertTrue(query.contains("fixture-marker"));
                boolean inRoot=metadata.get().path("parents").path(0).asText().equals("actual-root");
                MockHttp.reply(exchange,200,inRoot?"{\"files\":[{\"id\":\"allocated\"}]}":"{\"files\":[]}");
            }
            else if(path.endsWith("/allocated"))MockHttp.reply(exchange,200,metadata.get().toString());
            else throw new AssertionError(path);
        })){
            var drive=client(server);
            assertEquals("allocated",drive.ensureFolderWithId(null,"fixture","fixture-marker","allocated"));
            for(String field:java.util.List.of("id","name","mimeType","parents","appProperties","capabilities","trashed")){
                var invalid=folder();invalid.remove(field);metadata.set(invalid);
                assertThrows(IOException.class,()->drive.ensureFolderWithId(null,"fixture","fixture-marker","allocated"),field);
            }
            var invalid=folder();invalid.putArray("parents").add("other-parent");metadata.set(invalid);
            assertThrows(IOException.class,()->drive.ensureFolderWithId(null,"fixture","fixture-marker","allocated"));
            invalid=folder();invalid.put("trashed",true);metadata.set(invalid);
            assertThrows(IOException.class,()->drive.ensureFolderWithId(null,"fixture","fixture-marker","allocated"));
            assertEquals(10,posts.get());
        }
    }
    @Test void reportsDriveOperationWithoutProviderBodyOrResourceId() throws Exception {
        try (var server = new MockHttp(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/token"))
                MockHttp.reply(exchange,200,"{\"access_token\":\"fixture\",\"expires_in\":3600}");
            else MockHttp.reply(exchange,404,"{\"error\":{\"message\":\"private-provider-detail\"}}");
        })) {
            var drive = client(server);
            var error = assertThrows(WorkspaceClient.WorkspaceHttpException.class,
                () -> drive.file("private-resource-id"));
            var diagnostic = io.github.assafep.jobstein.validation.Diagnostics.describe(error);
            assertTrue(diagnostic.startsWith("[GOOGLE_RESOURCE_MISSING] Read a Drive file or folder failed (HTTP 404)."));
            assertFalse(diagnostic.contains("private-resource-id"));
            assertFalse(diagnostic.contains("private-provider-detail"));
        }
    }
    @Test void rejectsAmbiguousOrMalformedDiscoveryAndUnexpectedCreationId()throws Exception{
        var response=new AtomicReference<>("{\"files\":[{\"id\":\"first\"},{\"id\":\"second\"}]}");
        try(var server=new MockHttp(exchange->{
            String path=exchange.getRequestURI().getPath();
            if(path.equals("/token"))MockHttp.reply(exchange,200,"{\"access_token\":\"fixture\",\"expires_in\":3600}");
            else if(exchange.getRequestMethod().equals("POST"))MockHttp.reply(exchange,200,"{\"id\":\"unexpected\"}");
            else MockHttp.reply(exchange,200,response.get());
        })){
            var drive=client(server);
            assertThrows(IOException.class,()->drive.findFolder("parent","fixture","marker"));
            response.set("{\"files\":[],\"nextPageToken\":\"more\"}");
            assertThrows(IOException.class,()->drive.findFolder("parent","fixture","marker"));
            response.set("{}");
            assertThrows(IOException.class,()->drive.findFolder("parent","fixture","marker"));
            response.set("{\"files\":[]}");
            assertTrue(drive.findFolder("parent","fixture","marker").isEmpty());
            assertThrows(IOException.class,()->drive.ensureFolderWithId("parent","fixture","marker","allocated"));
        }
    }
}
