package io.github.assafep.jobstein.ai;
import com.fasterxml.jackson.databind.*;
import io.github.assafep.jobstein.security.SecretStore;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.*;
import java.util.*;
import io.github.assafep.jobstein.validation.DiagnosticException;
import io.github.assafep.jobstein.validation.Diagnostics;

public final class XaiAdapter implements AiProviderAdapter {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final SecretStore secrets;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final URI origin;
    public XaiAdapter(SecretStore secrets){this.secrets=secrets;this.origin=URI.create("https://api.x.ai");}
    XaiAdapter(SecretStore secrets,URI testOrigin){this.secrets=secrets;this.origin=io.github.assafep.jobstein.testing.LoopbackOrigin.validate(testOrigin);}
    private static JsonNode parseObject(String body)throws IOException{
        try{
            var parsed=JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if(parsed==null||!parsed.isObject())throw new DiagnosticException(DiagnosticException.Code.AI_RESPONSE_INVALID);
            return parsed;
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new DiagnosticException(DiagnosticException.Code.AI_RESPONSE_INVALID,e);}
    }
    private JsonNode request(AiIntegrationConfig config,String endpoint,JsonNode body) throws Exception {
        var reference=config.configuration().get("apiKey");
        String key=new String(secrets.get(reference).orElseThrow(()->new DiagnosticException(DiagnosticException.Code.AI_CREDENTIAL_REQUIRED)),StandardCharsets.UTF_8);
        var builder=HttpRequest.newBuilder(origin.resolve("/v1/"+endpoint)).timeout(Duration.ofMinutes(3)).header("Authorization","Bearer "+key);
        if(body==null)builder.GET();
        else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300)throw new DiagnosticException(Diagnostics.aiHttp(response.statusCode()));
        return parseObject(response.body());
    }
    public List<String> models(AiIntegrationConfig config)throws Exception{
        var result=new ArrayList<String>();
        var models=request(config,"models",null).path("data");
        if(!models.isArray())throw new DiagnosticException(DiagnosticException.Code.AI_RESPONSE_INVALID);
        for(var model:models) if(model.path("id").isTextual())result.add(model.path("id").asText());
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
        if(result.isBlank())throw new DiagnosticException(DiagnosticException.Code.AI_RESPONSE_INVALID);
        return parseObject(result);
    }
}
