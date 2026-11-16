package io.github.assafep.jobstein.environment;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;
import java.io.StringReader;

public final class EnvironmentService {
    private final Path root;

    public EnvironmentService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path directory(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9-]{0,62}")) {
            throw new IllegalArgumentException("Invalid environment name");
        }
        return root.resolve(name);
    }

    public Path create(String name) throws IOException {
        Path dir = directory(name);
        Files.createDirectories(dir);
        Path config = dir.resolve("environment.properties");
        try {
            Files.writeString(config, "schemaVersion=1\nname=" + name + "\n",
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
            validate(name);
        }
        return dir;
    }

    public List<String> list() throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                .filter(path -> Files.isRegularFile(path.resolve("environment.properties")))
                .map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    public Path validate(String name) throws IOException {
        Path config = directory(name).resolve("environment.properties");
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(config)));
        if (!"1".equals(properties.getProperty("schemaVersion"))
                || !name.equals(properties.getProperty("name"))) {
            throw new IOException("Invalid environment metadata: " + config);
        }
        return config;
    }
}
