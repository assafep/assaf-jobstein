package io.github.assafep.jobstein.validation;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {
    @Test void neverDisplaysUntrustedExceptionMessagesOrCause(){
        String secret="synthetic-secret-value";
        assertFalse(Diagnostics.describe(new IOException(secret)).contains(secret));
        assertFalse(Diagnostics.describe(new IllegalArgumentException(secret)).contains(secret));
        var safe=new DiagnosticException(DiagnosticException.Code.AI_AUTH_FAILED,new IOException(secret));
        assertTrue(Diagnostics.describe(safe).startsWith("[AI_AUTH_FAILED]"));
        assertFalse(Diagnostics.describe(safe).contains(secret));
    }
    @Test void mapsProviderStatusToRecoveryGuidance(){
        assertEquals(DiagnosticException.Code.AI_RATE_LIMIT,Diagnostics.aiHttp(429));
        assertEquals(DiagnosticException.Code.AI_AUTH_FAILED,Diagnostics.aiHttp(401));
        assertEquals(DiagnosticException.Code.GOOGLE_RESOURCE_MISSING,Diagnostics.googleHttp(404));
        assertEquals(DiagnosticException.Code.GOOGLE_UNAVAILABLE,Diagnostics.googleHttp(503));
    }

    @Test void describesOAuthClientProjectMismatch(){
        var diagnostic=new DiagnosticException(DiagnosticException.Code.GOOGLE_OAUTH_CLIENT_MISMATCH);
        assertTrue(Diagnostics.describe(diagnostic).contains("different Google Cloud project"));
        assertTrue(Diagnostics.describe(diagnostic).startsWith("[GOOGLE_OAUTH_CLIENT_MISMATCH]"));
    }

    @Test void describesEnvironmentProjectMismatch(){
        var diagnostic=new DiagnosticException(DiagnosticException.Code.ENVIRONMENT_PROJECT_MISMATCH);
        assertTrue(Diagnostics.describe(diagnostic).contains("already bound to a different"));
    }
}
