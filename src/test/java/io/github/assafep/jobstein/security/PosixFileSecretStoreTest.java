package io.github.assafep.jobstein.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs({OS.LINUX, OS.MAC})
class PosixFileSecretStoreTest {
    @TempDir Path root;

    @BeforeEach void canonicalTemporaryDirectory() throws IOException {
        root = root.toRealPath();
    }

    @Test void ancestorSymlinkRejectsEveryOperationWithoutChangingCredentials() throws Exception {
        Path actual = Files.createDirectory(root.resolve("actual"));
        var store = new PosixFileSecretStore(actual.resolve("secrets"));
        String reference = "secret://synthetic/token";
        byte[] original = {1, 2, 3};
        store.put(reference, original);
        Path alias = Files.createSymbolicLink(root.resolve("alias"), actual);
        var aliasedStore = new PosixFileSecretStore(alias.resolve("secrets"));

        assertThrows(IOException.class, () -> aliasedStore.get(reference));
        assertThrows(IOException.class, () -> aliasedStore.put(reference, new byte[]{4}));
        assertThrows(IOException.class, () -> aliasedStore.remove(reference));
        assertArrayEquals(original, store.get(reference).orElseThrow());

        var missingStore = new PosixFileSecretStore(alias.resolve("missing/secrets"));
        assertThrows(IOException.class, () -> missingStore.get(reference));
        assertThrows(IOException.class, () -> missingStore.put(reference, new byte[]{5}));
        assertThrows(IOException.class, () -> missingStore.remove(reference));
        assertFalse(Files.exists(actual.resolve("missing")));
    }

    @Test void missingRealAncestorsAllowAbsentReadsAndPrivateCreation() throws Exception {
        var store = new PosixFileSecretStore(root.resolve("new/parent/secrets"));
        String reference = "secret://synthetic/token";
        assertTrue(store.get(reference).isEmpty());
        store.remove(reference);
        assertFalse(Files.exists(root.resolve("new")));
        store.put(reference, new byte[]{7});
        assertArrayEquals(new byte[]{7}, store.get(reference).orElseThrow());
        for (String directory : new String[]{"new", "new/parent"}) {
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(root.resolve(directory)));
        }
    }

    @Test void nonDirectoryAncestorFailsClosed() throws Exception {
        Path obstruction = Files.write(root.resolve("file"), new byte[]{8});
        var store = new PosixFileSecretStore(obstruction.resolve("secrets"));
        String reference = "secret://synthetic/token";
        assertThrows(IOException.class, () -> store.get(reference));
        assertThrows(IOException.class, () -> store.put(reference, new byte[]{9}));
        assertThrows(IOException.class, () -> store.remove(reference));
        assertArrayEquals(new byte[]{8}, Files.readAllBytes(obstruction));
    }

    @Test void hardLinkedCredentialRejectsEveryOperationWithoutChangingEitherLink() throws Exception {
        var store = new PosixFileSecretStore(root.resolve("secrets"));
        String reference = "secret://synthetic/token";
        byte[] original = {1, 2, 3};
        store.put(reference, original);
        Path credential = root.resolve("secrets/synthetic/token");
        Path alias = root.resolve("alias");
        Files.createLink(alias, credential);

        assertThrows(IOException.class, () -> store.get(reference));
        assertThrows(IOException.class, () -> store.put(reference, new byte[]{4, 5, 6}));
        assertThrows(IOException.class, () -> store.remove(reference));
        assertTrue(Files.isSameFile(credential, alias));
        assertArrayEquals(original, Files.readAllBytes(credential));
        assertArrayEquals(original, Files.readAllBytes(alias));

        Files.delete(alias);
        assertArrayEquals(original, store.get(reference).orElseThrow());
        store.put(reference, new byte[]{7});
        assertArrayEquals(new byte[]{7}, store.get(reference).orElseThrow());
        store.remove(reference);
        assertTrue(store.get(reference).isEmpty());
    }

    @Test void createdCredentialPathsAreOwnedByCurrentUser() throws Exception {
        var store = new PosixFileSecretStore(root.resolve("owned-secrets"));
        store.put("secret://synthetic/token", new byte[]{1});
        var expected = Files.getOwner(root);
        assertEquals(expected.getName(), Files.getOwner(root.resolve("owned-secrets")).getName());
        assertEquals(expected.getName(), Files.getOwner(root.resolve("owned-secrets/synthetic")).getName());
        assertEquals(expected.getName(), Files.getOwner(root.resolve("owned-secrets/synthetic/token")).getName());
    }
}
