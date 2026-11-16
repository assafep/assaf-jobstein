package io.github.assafep.jobstein.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.sandbox.MockAiExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {
    @TempDir Path root;
    @Test void validatesAndPersistsBindingsUsingInjectedAdapter()throws Exception{
        var definition=new ObjectMapper().createObjectNode().put("id","mock-xai").put("adapterType","mock-chat");
        definition.putObject("fields").putObject("model").put("type","STRING").put("required",true);
        var service=new AiService(new ConfigurationRepository(root),new ProviderCatalog(Map.of("mock-xai",definition)),Map.of("mock-chat",MockAiExecution.providerAdapter()),"mock-ai");
        var standard=new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok"));
        var advanced=new AiIntegrationConfig("mock-xai",Map.of("model","mock-grok-advanced"));
        service.put("standard",standard,true,false);
        service.put("advanced",advanced,false,false);
        service.bind("job-matching","advanced");
        assertEquals("advanced",new AiIntegrationResolver(service.load()).resolve("job-matching").id());
        assertThrows(IllegalArgumentException.class,()->service.remove("advanced"));
        assertThrows(IllegalArgumentException.class,()->service.put("bad",new AiIntegrationConfig("mock-xai",Map.of("model","unknown")),false,false));
        assertFalse(service.load().integrations().containsKey("bad"));
        service.bind("job-matching",null);
        service.remove("advanced");
        service.validate();
        assertEquals(Set.of("standard"),service.load().integrations().keySet());
    }
}
