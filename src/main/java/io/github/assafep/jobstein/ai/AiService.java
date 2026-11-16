package io.github.assafep.jobstein.ai;
import io.github.assafep.jobstein.config.ConfigurationRepository;
import io.github.assafep.jobstein.security.SecretStore;
import java.io.*;
import java.util.*;

public final class AiService implements AiExecution {
    private final ConfigurationRepository repository;
    private final ProviderCatalog catalog;
    private final Map<String,AiProviderAdapter> adapters;
    public AiService(ConfigurationRepository repository, SecretStore secrets)throws IOException{
        this.repository=repository;
        this.catalog=new ProviderCatalog();
        this.adapters=Map.of("xai-chat",new XaiAdapter(secrets));
    }
    public ProviderCatalog catalog(){return catalog;}
    public AiConfiguration load()throws IOException{return repository.load("ai",AiConfiguration.class).orElseThrow(()->new IOException("AI configuration missing"));}
    public Optional<AiConfiguration> existing()throws IOException{return repository.load("ai",AiConfiguration.class);}
    public AiProviderAdapter adapter(AiIntegrationConfig config){
        var adapter=adapters.get(catalog.definition(config.provider()).path("adapterType").asText());
        if(adapter==null)throw new IllegalArgumentException("Provider adapter unavailable");
        return adapter;
    }
    public void save(AiConfiguration configuration)throws IOException{
        configuration.integrations().values().forEach(catalog::validate);
        repository.save("ai",configuration);
    }
    public void put(String id,AiIntegrationConfig config,boolean makeDefault,boolean update)throws Exception{
        if(!id.matches("[a-z][a-z0-9-]*"))throw new IllegalArgumentException("Invalid integration ID");
        catalog.validate(config);adapter(config).validate(config);
        var previous=existing();
        var integrations=new HashMap<>(previous.map(AiConfiguration::integrations).orElse(Map.of()));
        if(update!=integrations.containsKey(id))throw new IllegalArgumentException(update?"Integration not found":"Integration already exists");
        integrations.put(id,config);
        save(new AiConfiguration(1,makeDefault?id:previous.map(AiConfiguration::defaultIntegration).orElse(id),integrations,previous.map(AiConfiguration::agentBindings).orElse(Map.of())));
    }
    public void bind(String agent,String integration)throws IOException{
        var current=load();var bindings=new HashMap<>(current.agentBindings());
        if(integration==null)bindings.remove(agent);else bindings.put(agent,integration);
        save(new AiConfiguration(1,current.defaultIntegration(),current.integrations(),bindings));
    }
    public void remove(String id)throws IOException{
        var current=load();var integrations=new HashMap<>(current.integrations());
        if(integrations.remove(id)==null)throw new IllegalArgumentException("Integration not found");
        save(new AiConfiguration(1,current.defaultIntegration(),integrations,current.agentBindings()));
    }
    public void validate()throws Exception{for(var integration:load().integrations().values()){catalog.validate(integration);adapter(integration).validate(integration);}}
}
