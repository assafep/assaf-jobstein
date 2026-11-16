package io.github.assafep.jobstein.ai;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
public interface AiProviderAdapter {
    List<String> models(AiIntegrationConfig configuration) throws Exception;
    JsonNode generateJson(AiIntegrationConfig configuration, String instructions, String input) throws Exception;
    default void validate(AiIntegrationConfig configuration) throws Exception {
        if(!models(configuration).contains(configuration.configuration().get("model")))
            throw new IllegalArgumentException("Selected model is not available");
    }
}
