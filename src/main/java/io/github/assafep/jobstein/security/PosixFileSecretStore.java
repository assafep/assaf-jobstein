package io.github.assafep.jobstein.security;

import io.github.assafep.jobstein.config.AtomicFiles;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

public final class PosixFileSecretStore implements SecretStore {
    private static final String PREFIX = "secret://";
    private final Path root;

    public PosixFileSecretStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private String[] segments(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid secret reference");
        }
        String[] parts = reference.substring(PREFIX.length()).split("/", -1);
        for (String part : parts) {
            if (!part.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))
                throw new IllegalArgumentException("Invalid secret reference");
        }
        return parts;
    }

    private void ensureDirectory(Path path, boolean create) throws IOException {
        if (create && Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(path, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
            } catch (FileAlreadyExistsException ignored) {}
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
            || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                .equals(PosixFilePermissions.fromString("rwx------"))) {
            throw new IOException("Secret directory must be a real directory with mode 0700");
        }
    }

    private Optional<Path> resolve(String reference, boolean create) throws IOException {
        String[] parts = segments(reference);
        if (!validateAncestors(create)) return Optional.empty();
        if (!create && Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        ensureDirectory(root, create);
        Path directory = root;
        for (int index = 0; index < parts.length - 1; index++) {
            directory = directory.resolve(parts[index]);
            if (!create && Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            ensureDirectory(directory, create);
        }
        Path file = directory.resolve(parts[parts.length - 1]);
        if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
                    .equals(PosixFilePermissions.fromString("rw-------"))) {
                throw new IOException("Secret file must be a regular file with mode 0600");
            }
            // Permissions alone do not protect a credential exposed through another hard link.
            // Fail closed on filesystems that cannot establish exclusive link ownership.
            try {
                Object links = Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
                if (!(links instanceof Number count) || count.longValue() != 1) {
                    throw new IOException("Secret file must have exactly one hard link");
                }
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw new IOException("Secret file hard-link validation is unavailable");
            }
        }
        return Optional.of(file);
    }

    private boolean validateAncestors(boolean create) throws IOException {
        Path parent = root.getParent();
        if (parent == null) throw new IOException("Secret root must have a parent directory");
        Path current = parent.getRoot();
        for (Path component : parent) {
            current = current.resolve(component);
            if (create && Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
                } catch (FileAlreadyExistsException ignored) {}
            }
            if (Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) return false;
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Secret path ancestors must be real directories");
            }
        }
        return true;
    }

    @Override public void put(String reference, byte[] value) throws IOException {
        java.util.Objects.requireNonNull(value);
        Path file = resolve(reference, true).orElseThrow();
        AtomicFiles.write(file, value, PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rw-------")));
    }

    @Override public Optional<byte[]> get(String reference) throws IOException {
        var file = resolve(reference, false);
        if (file.isEmpty() || Files.notExists(file.get(), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(Files.readAllBytes(file.get()));
    }

    @Override public void remove(String reference) throws IOException {
        var file = resolve(reference, false);
        if (file.isPresent()) Files.deleteIfExists(file.get());
    }
}
