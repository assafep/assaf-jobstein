package io.github.assafep.jobstein.ai;

import java.util.*;

public record AiConfiguration(int schemaVersion, String defaultIntegration,
    Map<String, AiIntegrationConfig> integrations, Map<String, String> agentBindings) {
    public AiConfiguration {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported AI configuration schema");
        Objects.requireNonNull(defaultIntegration);
        integrations = Map.copyOf(integrations);
        agentBindings = Map.copyOf(agentBindings);
        if (!integrations.containsKey(defaultIntegration))
            throw new IllegalArgumentException("Default integration does not exist");
        for (var entry : agentBindings.entrySet()) {
            if (!integrations.containsKey(entry.getValue()))
                throw new IllegalArgumentException("Unknown integration for agent: " + entry.getKey());
        }
    }
}
