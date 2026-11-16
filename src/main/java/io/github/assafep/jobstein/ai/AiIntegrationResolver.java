package io.github.assafep.jobstein.ai;

import java.util.Objects;

public final class AiIntegrationResolver {
    private final AiConfiguration configuration;
    public AiIntegrationResolver(AiConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }
    public record ResolvedIntegration(String id, AiIntegrationConfig configuration) {}
    public ResolvedIntegration resolve(String agentId) {
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("Agent ID required");
        String id = configuration.agentBindings().getOrDefault(agentId, configuration.defaultIntegration());
        return new ResolvedIntegration(id, configuration.integrations().get(id));
    }
}
