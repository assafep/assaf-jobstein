package io.github.assafep.jobstein.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

public final class ConfigurationRepository {
    private final Path directory;
    private final ObjectMapper mapper;

    public ConfigurationRepository(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper(new YAMLFactory())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private Path path(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid configuration name");
        }
        return directory.resolve(name + ".yaml");
    }

    public <T> Optional<T> load(String name, Class<T> type) throws IOException {
        Path file = path(name);
        if (Files.notExists(file)) return Optional.empty();
        T value = mapper.readValue(Files.readAllBytes(file), type);
        if (value == null) throw new IOException("Empty configuration: " + name);
        return Optional.of(value);
    }

    public void remove(String name)throws IOException{Files.deleteIfExists(path(name));}

    public <T> void save(String name, T configuration) throws IOException {
        java.util.Objects.requireNonNull(configuration);
        io.github.assafep.jobstein.security.PrivateState.directory(directory);
        AtomicFiles.write(path(name), mapper.writeValueAsBytes(configuration),io.github.assafep.jobstein.security.PrivateState.filePermissions());
    }
}
