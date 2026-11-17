package io.github.assafep.jobstein.ai;
import io.github.assafep.jobstein.testing.MockHttp;
import io.github.assafep.jobstein.security.PosixFileSecretStore;
import io.github.assafep.jobstein.validation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class XaiHttpIT {
    @TempDir Path root;
    @Test void validatesPayloadAndRejectsMalformedAndRateLimitedResponses()throws Exception{
        var calls=new AtomicInteger();
        var config=new AiIntegrationConfig("xai",Map.of("apiKey","secret://ai/key","model","synthetic-model"));
        var secrets=new PosixFileSecretStore(root.resolve("secrets"));
        secrets.put("secret://ai/key","synthetic-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(var server=new MockHttp(exchange->{
            assertEquals("Bearer synthetic-key",exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("/v1/chat/completions",exchange.getRequestURI().getPath());
            assertEquals("POST",exchange.getRequestMethod());
            var body=new ObjectMapper().readTree(MockHttp.body(exchange));
            assertEquals("synthetic-model",body.path("model").asText());
            assertEquals("json_object",body.path("response_format").path("type").asText());
            switch(calls.incrementAndGet()){
                case 1 -> MockHttp.reply(exchange,200,"{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}");
                case 2 -> MockHttp.reply(exchange,200,"{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}");
                default -> MockHttp.reply(exchange,429,"{\"error\":\"synthetic-private-detail\"}");
            }
        })){
            var adapter=new XaiAdapter(secrets,server.origin());
            assertTrue(adapter.generateJson(config,"instruction","input").path("ok").asBoolean());
            var malformed=assertThrows(DiagnosticException.class,()->adapter.generateJson(config,"instruction","input"));
            assertEquals(DiagnosticException.Code.AI_RESPONSE_INVALID,malformed.code());
            var limited=assertThrows(DiagnosticException.class,()->adapter.generateJson(config,"instruction","input"));
            assertEquals(DiagnosticException.Code.AI_RATE_LIMIT,limited.code());
            assertFalse(Diagnostics.describe(limited).contains("synthetic-private-detail"));
            assertEquals(3,calls.get());
        }
    }
}
