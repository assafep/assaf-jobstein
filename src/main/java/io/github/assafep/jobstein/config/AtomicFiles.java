package io.github.assafep.jobstein.config;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;

public final class AtomicFiles {
    private AtomicFiles() {}

    public static void write(Path target, byte[] data, FileAttribute<?>... attrs) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".jobstein-", ".tmp", attrs);
        try {
            Files.write(temporary, data, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(temporary, target.toAbsolutePath().normalize(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
