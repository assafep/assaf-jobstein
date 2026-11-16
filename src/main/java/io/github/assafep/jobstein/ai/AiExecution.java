package io.github.assafep.jobstein.ai;

import java.io.IOException;

public interface AiExecution {
    AiConfiguration load() throws IOException;
    AiProviderAdapter adapter(AiIntegrationConfig config);
}
