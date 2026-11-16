package io.github.assafep.jobstein.ai;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AiIntegrationResolverTest {
    private final AiIntegrationConfig standard = new AiIntegrationConfig("xai", Map.of("apiKey", "secret://ai/xai/default"));
    private final AiIntegrationConfig dedicated = new AiIntegrationConfig("future-provider",
        Map.of("apiKey", "secret://ai/other/dedicated", "model", "example"));

    @Test void defaultsAndWholeIntegrationOverrides() {
        var resolver = new AiIntegrationResolver(new AiConfiguration(1, "standard",
            Map.of("standard", standard, "dedicated", dedicated),
            Map.of("application-form-submission", "dedicated")));
        assertEquals(standard, resolver.resolve("job-matching").configuration());
        assertEquals(dedicated, resolver.resolve("application-form-submission").configuration());
        assertEquals("dedicated", resolver.resolve("application-form-submission").id());
    }
    @Test void rejectsDanglingOverride() {
        assertThrows(IllegalArgumentException.class, () -> new AiConfiguration(1, "standard",
            Map.of("standard", standard), Map.of("job-matching", "missing")));
    }
    @Test void rejectsMissingDefault() {
        assertThrows(IllegalArgumentException.class, () -> new AiConfiguration(1, "missing",
            Map.of("standard", standard), Map.of()));
    }
}
