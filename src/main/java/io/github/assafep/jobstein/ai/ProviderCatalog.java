package io.github.assafep.jobstein.ai;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.*;
import java.util.*;

public final class ProviderCatalog {
    private final Map<String, JsonNode> providers;
    public ProviderCatalog() throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        var values = new LinkedHashMap<String,JsonNode>();
        try(var index = ProviderCatalog.class.getResourceAsStream("/providers/index.txt")) {
            if(index==null) throw new IOException("Missing provider index");
            for(String id:new String(index.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                if(id.isBlank()) continue;
                if(!id.matches("[a-z][a-z0-9-]*")) throw new IOException("Invalid provider ID");
                try(var in=ProviderCatalog.class.getResourceAsStream("/providers/"+id+".yaml")) {
                    if(in==null) throw new IOException("Missing provider definition: "+id);
                    var definition=mapper.readTree(in);
                    if(!id.equals(definition.path("id").asText()) || !definition.path("fields").isObject()) throw new IOException("Invalid provider definition: "+id);
                    values.put(id,definition);
                }
            }
        }
        providers=Map.copyOf(values);
    }
    public ProviderCatalog(Map<String,JsonNode> definitions) {
        var copy=new LinkedHashMap<String,JsonNode>();
        definitions.forEach((id,definition)->{
            if(!id.matches("[a-z][a-z0-9-]*")||!id.equals(definition.path("id").asText())||!definition.path("fields").isObject())
                throw new IllegalArgumentException("Invalid provider definition: "+id);
            copy.put(id,definition.deepCopy());
        });
        providers=Map.copyOf(copy);
    }
    public Set<String> ids(){return providers.keySet();}
    public JsonNode definition(String id){
        var value=providers.get(id);
        if(value==null)throw new IllegalArgumentException("Unsupported provider: "+id);
        return value.deepCopy();
    }
    public void validate(AiIntegrationConfig integration){
        var fields=definition(integration.provider()).path("fields");
        fields.fields().forEachRemaining(e->{
            String value=integration.configuration().get(e.getKey());
            if(e.getValue().path("required").asBoolean() && (value==null||value.isBlank()))
                throw new IllegalArgumentException("Missing provider field: "+e.getKey());
            if(value!=null && "SECRET".equals(e.getValue().path("type").asText()) && !value.startsWith("secret://"))
                throw new IllegalArgumentException("Secret reference required: "+e.getKey());
        });
        for(String key:integration.configuration().keySet())if(!fields.has(key))throw new IllegalArgumentException("Unknown provider field: "+key);
    }
}
