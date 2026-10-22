package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OllamaHttpContractTest {
    @Test
    void sendsConfiguredJsonToChatEndpoint() throws Exception {
        var captured = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] result = "{\"message\":{\"content\":\"No tengo datos verificados.\"},\"done\":true}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, result.length);
            try (var output = exchange.getResponseBody()) { output.write(result); }
        });
        server.start();
        try {
            var properties = new AiProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "test-model", Duration.ofSeconds(2), Duration.ofSeconds(5), 42, 1);
            var mapper = JsonMapper.builder().build();
            try (var http = new AiConfiguration().ollamaHttpClient(properties)) {
                assertEquals(properties.connectTimeout(), http.connectTimeout().orElseThrow());
                assertEquals(java.net.http.HttpClient.Redirect.NEVER, http.followRedirects());
                var client = new OllamaClient(http, mapper, properties);
                assertEquals("No tengo datos verificados.", client.chat("¿Hoteles en Ica?"));
            }
            var body = mapper.readTree(captured.get());
            assertEquals("POST", method.get());
            assertEquals("application/json", contentType.get());
            assertEquals("test-model", body.path("model").stringValue());
            assertFalse(body.path("stream").asBoolean(true));
            assertEquals(42, body.path("options").path("num_predict").asInt());
            assertEquals(2, body.path("messages").size());
            assertEquals("system", body.path("messages").get(0).path("role").stringValue());
            assertEquals(OllamaClient.SYSTEM_PROMPT, body.path("messages").get(0).path("content").stringValue());
            assertEquals("user", body.path("messages").get(1).path("role").stringValue());
            assertEquals("¿Hoteles en Ica?", body.path("messages").get(1).path("content").stringValue());
        } finally {
            server.stop(0);
        }
    }
}
