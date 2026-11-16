package io.github.assafep.jobstein.ai;
import com.fasterxml.jackson.databind.*;
import io.github.assafep.jobstein.security.SecretStore;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.*;
import java.util.*;

public final class XaiAdapter implements AiProviderAdapter {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final SecretStore secrets;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    public XaiAdapter(SecretStore secrets){this.secrets=secrets;}
    private JsonNode request(AiIntegrationConfig config,String endpoint,JsonNode body) throws Exception {
        var reference=config.configuration().get("apiKey");
        String key=new String(secrets.get(reference).orElseThrow(()->new IOException("xAI secret missing")),StandardCharsets.UTF_8);
        var builder=HttpRequest.newBuilder(URI.create("https://api.x.ai/v1/"+endpoint)).timeout(Duration.ofMinutes(3)).header("Authorization","Bearer "+key);
        if(body==null)builder.GET();
        else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300)throw new IOException("xAI request failed (HTTP "+response.statusCode()+")");
        return JSON.readTree(response.body());
    }
    public List<String> models(AiIntegrationConfig config)throws Exception{
        var result=new ArrayList<String>();
        for(var model:request(config,"models",null).path("data")) if(model.path("id").isTextual())result.add(model.path("id").asText());
        return result.stream().sorted().toList();
    }
    public JsonNode generateJson(AiIntegrationConfig config,String instructions,String input)throws Exception {
        var body=JSON.createObjectNode().put("model",config.configuration().get("model"));
        var messages=body.putArray("messages");
        messages.addObject().put("role","system").put("content",instructions);
        messages.addObject().put("role","user").put("content",input);
        body.putObject("response_format").put("type","json_object");
        String effort=config.configuration().get("reasoningEffort");
        if(effort!=null&&!effort.isBlank())body.put("reasoning_effort",effort);
        String result=request(config,"chat/completions",body).path("choices").path(0).path("message").path("content").asText("");
        if(result.isBlank())throw new IOException("Empty AI response");
        var parsed=JSON.readTree(result);
        if(!parsed.isObject())throw new IOException("Expected JSON object");
        return parsed;
    }
}
