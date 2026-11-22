package io.github.assafep.jobstein.google;

import io.github.assafep.jobstein.security.SecretStore;
import io.github.assafep.jobstein.validation.DiagnosticException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleOAuthTest {
    @Test
    void rejectsClientFromDifferentProjectWithActionableDiagnostic() throws Exception {
        var clientFile = Files.createTempFile("jobstein-oauth-client-", ".json");
        Files.writeString(clientFile, """
            {"installed":{"project_id":"other-project","client_id":"client","client_secret":"secret"}}
            """);
        var oauth = new GoogleOAuth(new MemorySecretStore());

        var error = assertThrows(DiagnosticException.class,
            () -> oauth.importClient(clientFile, "selected-project"));

        assertEquals(DiagnosticException.Code.GOOGLE_OAUTH_CLIENT_MISMATCH, error.code());
    }

    @Test
    void resolvesProjectIdFromDesktopClient() throws Exception {
        var clientFile = Files.createTempFile("jobstein-oauth-client-", ".json");
        Files.writeString(clientFile, """
            {"installed":{"project_id":"selected-project","client_id":"client","client_secret":"secret"}}
            """);

        assertEquals("selected-project", new GoogleOAuth(new MemorySecretStore()).projectId(clientFile));
    }

    private static final class MemorySecretStore implements SecretStore {
        private final Map<String, byte[]> values = new HashMap<>();

        @Override public void put(String reference, byte[] value) { values.put(reference, value); }
        @Override public Optional<byte[]> get(String reference) { return Optional.ofNullable(values.get(reference)); }
        @Override public void remove(String reference) { values.remove(reference); }
    }
}
