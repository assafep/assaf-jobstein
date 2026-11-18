package io.github.assafep.jobstein.provisioning;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceManifestRepositoryTest {
    @TempDir Path root;

    @Test void rejectsMalformedPersistedManifestEntries() throws Exception {
        var repository = new ConfigurationRepository(root);
        repository.save("resource-manifest", Map.of("schemaVersion", 1, "resourceIds", Map.of("user/metadata", " ")));
        assertThrows(java.io.IOException.class, () -> new ResourceManifestRepository(repository).load());

        repository.save("resource-manifest", Map.of("schemaVersion", 1, "resourceIds", Map.of("user/../metadata", "valid-id")));
        assertThrows(java.io.IOException.class, () -> new ResourceManifestRepository(repository).load());
    }

    @Test void acceptsNormalDriveIdsAndRefusesInvalidNewEntries() throws Exception {
        var repository = new ResourceManifestRepository(new ConfigurationRepository(root));
        repository.remember("user/metadata", "Drive_ID-123");
        assertEquals("Drive_ID-123", repository.load().resourceIds().get("user/metadata"));
        assertThrows(IllegalArgumentException.class, () -> repository.remember("user/other", "bad/id"));
    }
}
