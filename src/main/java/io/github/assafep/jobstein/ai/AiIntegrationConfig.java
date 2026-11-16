package io.github.assafep.jobstein.ai;

import java.util.*;

public record AiIntegrationConfig(String provider, Map<String, String> configuration) {
    public AiIntegrationConfig {
        if (provider == null || !provider.matches("[a-z][a-z0-9-]*"))
            throw new IllegalArgumentException("Invalid provider ID");
        configuration = Map.copyOf(configuration);
    }
}
