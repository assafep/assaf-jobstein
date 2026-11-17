package io.github.assafep.jobstein.sandbox;

import com.fasterxml.jackson.databind.*;
import io.github.assafep.jobstein.ai.*;
import java.util.*;

public final class MockAiExecution implements AiExecution {
    private final AiConfiguration configuration;
    public MockAiExecution(AiConfiguration configuration){this.configuration=Objects.requireNonNull(configuration);}
    @Override public AiConfiguration load(){return configuration;}
    @Override public AiProviderAdapter adapter(AiIntegrationConfig config){
        if(!"mock-xai".equals(config.provider()))throw new IllegalArgumentException("Only simulated provider permitted");
        return providerAdapter();
    }
    public static AiProviderAdapter providerAdapter(){
        return new AiProviderAdapter(){
            public List<String> models(AiIntegrationConfig ignored){return List.of("mock-grok","mock-grok-advanced");}
            public JsonNode generateJson(AiIntegrationConfig selected,String instructions,String input)throws Exception{
                if(!"mock-xai".equals(selected.provider()))throw new IllegalArgumentException("Only simulated provider permitted");
                validate(selected);
                var mapper=new ObjectMapper();
                String excerpt=mapper.readTree(input).path("resume").asText().lines().filter(line->!line.isBlank()).findFirst().orElseThrow();
                var result=mapper.createObjectNode().put("professionalSummary",excerpt).put("synthetic",true);
                result.putArray("skills");result.putArray("skillEvidence");result.putArray("employmentHistory");result.putArray("education");
                return result;
            }
        };
    }
}
