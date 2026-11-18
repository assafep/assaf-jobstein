package io.github.assafep.jobstein.config;

import io.github.assafep.jobstein.provisioning.*;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FoundationTest {
    @TempDir Path root;
    public record Example(String name, int version) {}

    @Test void yamlRoundTripAndMissingConfiguration() throws Exception {
        var repo = new ConfigurationRepository(root);
        assertTrue(repo.load("example", Example.class).isEmpty());
        repo.save("example", new Example("test", 1));
        assertEquals(new Example("test", 1), repo.load("example", Example.class).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> repo.save("../escape", new Example("test", 1)));
    }

    @Test void checkpointsSurviveRepositoryRecreation() throws Exception {
        var state = new ProvisioningStateRepository(new ConfigurationRepository(root));
        state.record("google-configure", ProvisioningStateRepository.Status.COMPLETED);
        var reopened = new ProvisioningStateRepository(new ConfigurationRepository(root));
        assertTrue(reopened.completed("google-configure"));
        assertFalse(reopened.completed("ai"));
    }

    @Test void manifestRejectsAccidentalReplacement() throws Exception {
        var repo = new ResourceManifestRepository(new ConfigurationRepository(root));
        repo.remember("drive/root", "id-1");
        repo.remember("drive/root", "id-1");
        assertThrows(IllegalStateException.class, () -> repo.remember("drive/root", "id-2"));
        assertEquals(Map.of("drive/root", "id-1"), repo.load().resourceIds());
    }

    @Test @EnabledOnOs({OS.LINUX, OS.MAC})
    void secretsArePrivateAndIsolated() throws Exception {
        var prod = new PosixFileSecretStore(root.resolve("prod"));
        var dev = new PosixFileSecretStore(root.resolve("dev"));
        byte[] synthetic = {1, 2, 3};
        prod.put("secret://ai/xai/default", synthetic);
        assertArrayEquals(synthetic, prod.get("secret://ai/xai/default").orElseThrow());
        assertTrue(dev.get("secret://ai/xai/default").isEmpty());
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(
            Files.getPosixFilePermissions(root.resolve("prod/ai/xai/default"))));
        assertThrows(IllegalArgumentException.class, () -> prod.put("secret://../escape", synthetic));
        prod.remove("secret://ai/xai/default");
        assertTrue(prod.get("secret://ai/xai/default").isEmpty());
    }
}
