package io.github.assafep.jobstein.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs({OS.LINUX, OS.MAC})
class PosixFileSecretStoreTest {
    @TempDir Path root;

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
}
