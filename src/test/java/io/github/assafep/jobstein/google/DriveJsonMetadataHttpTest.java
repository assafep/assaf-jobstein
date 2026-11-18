package io.github.assafep.jobstein.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import io.github.assafep.jobstein.testing.MockHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DriveJsonMetadataHttpTest {
    @TempDir Path root;
    private static final ObjectMapper JSON = new ObjectMapper();

    private WorkspaceClient client(MockHttp server) throws Exception {
        var secrets = new PosixFileSecretStore(root.resolve("secrets"));
        secrets.put("secret://google/client", "{\"client_id\":\"fixture\",\"client_secret\":\"fixture\"}".getBytes(StandardCharsets.UTF_8));
        secrets.put("secret://google/refresh", "fixture".getBytes(StandardCharsets.UTF_8));
        return new WorkspaceClient(new GoogleOAuth(secrets, server.origin()), server.origin());
    }

    private static String validMetadata() {
        var node = JSON.createObjectNode().put("id", "seed-id").put("name", "user.json")
            .put("mimeType", "application/json").put("trashed", false);
        node.putArray("parents").add("user-folder");
        node.putObject("capabilities").put("canEdit", true);
        node.putArray("owners").addObject().put("emailAddress", "applicant@example.invalid").put("me", true);
        return node.toString();
    }

    @Test void validatesJsonIdentityParentTypeEditabilityTrashAndOwner() throws Exception {
        var metadata = new AtomicReference<>(validMetadata());
        try (var server = new MockHttp(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/token")) MockHttp.reply(exchange, 200, "{\"access_token\":\"fixture\",\"expires_in\":3600}");
            else if (path.equals("/drive/v3/files/seed-id")) {
                assertTrue(exchange.getRequestURI().getQuery().contains("owners"));
                MockHttp.reply(exchange, 200, metadata.get());
            } else if (path.equals("/v1/userinfo")) {
                MockHttp.reply(exchange, 200, "{\"sub\":\"subject\",\"email\":\"applicant@example.invalid\"}");
            } else throw new AssertionError(path);
        })) {
            var drive = client(server);
            drive.validateJsonResource("seed-id", "user-folder", "user.json");
            ObjectNode invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.put("id", "other-id");
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
            invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.putArray("parents").add("other-folder");
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
            invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.put("mimeType", "text/plain");
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
            invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.put("name", "other.json");
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
            invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.put("trashed", true);
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
            invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.putObject("capabilities").put("canEdit", false);
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
            invalid = (ObjectNode) JSON.readTree(validMetadata());
            invalid.putArray("owners").addObject().put("emailAddress", "someone-else@example.invalid").put("me", false);
            metadata.set(invalid.toString());
            assertThrows(IOException.class, () -> drive.validateJsonResource("seed-id", "user-folder", "user.json"));
        }
    }

    @Test void detectsNamedResourcesAndRejectsMalformedDiscovery() throws Exception {
        var response = new AtomicReference<>("{\"files\":[]}");
        try (var server = new MockHttp(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/token")) MockHttp.reply(exchange, 200, "{\"access_token\":\"fixture\",\"expires_in\":3600}");
            else if (path.equals("/drive/v3/files")) MockHttp.reply(exchange, 200, response.get());
            else throw new AssertionError(path);
        })) {
            var drive = client(server);
            assertFalse(drive.hasActiveNamedResource("parent-id", "application-registry.json"));
            response.set("{\"files\":[{\"id\":\"existing\"}]}");
            assertTrue(drive.hasActiveNamedResource("parent-id", "application-registry.json"));
            response.set("{\"files\":[],\"nextPageToken\":\"more\"}");
            assertTrue(drive.hasActiveNamedResource("parent-id", "application-registry.json"));
            response.set("{}");
            assertThrows(IOException.class, () -> drive.hasActiveNamedResource("parent-id", "application-registry.json"));
        }
    }
}
