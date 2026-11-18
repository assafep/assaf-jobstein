package io.github.assafep.jobstein.provisioning;

import io.github.assafep.jobstein.config.ConfigurationRepository;
import java.io.IOException;
import java.util.*;

public final class ResourceManifestRepository {
    public record Manifest(int schemaVersion, Map<String, String> resourceIds) {
        public Manifest {
            if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported manifest schema");
            Objects.requireNonNull(resourceIds, "resourceIds");
            resourceIds.forEach((logicalId, resourceId) -> {
                if (!validLogicalId(logicalId) || !validResourceId(resourceId))
                    throw new IllegalArgumentException("Invalid resource manifest entry");
            });
            resourceIds = Map.copyOf(resourceIds);
        }
    }

    private static boolean validLogicalId(String value) {
        return value != null && value.matches("[a-zA-Z0-9][a-zA-Z0-9._/-]{0,255}")
            && Arrays.stream(value.split("/", -1)).noneMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."));
    }

    private static boolean validResourceId(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,256}");
    }

    private final ConfigurationRepository repository;
    private final String configName;
    public ResourceManifestRepository(ConfigurationRepository repository) {
        this(repository,"resource-manifest");
    }
    public ResourceManifestRepository(ConfigurationRepository repository,String configName) {
        this.repository = Objects.requireNonNull(repository);
        this.configName=Objects.requireNonNull(configName);
    }
    public Manifest load() throws IOException {
        return repository.load(configName, Manifest.class).orElseGet(() -> new Manifest(1, Map.of()));
    }
    public synchronized void remember(String logicalId, String resourceId) throws IOException {
        if (!validLogicalId(logicalId) || !validResourceId(resourceId)) throw new IllegalArgumentException("Invalid resource ID");
        var ids = new HashMap<>(load().resourceIds());
        String previous = ids.putIfAbsent(logicalId, resourceId);
        if (previous != null && !previous.equals(resourceId))
            throw new IllegalStateException("Resource already registered: " + logicalId);
        repository.save(configName, new Manifest(1, ids));
    }
}
