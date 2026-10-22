package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class OllamaClientTest {
    private HttpClient http;
    private HttpResponse<String> response;
    private OllamaClient client;

    static AiProperties properties() {
        return new AiProperties(URI.create("http://localhost:11434"), "qwen2.5:1.5b",
                Duration.ofSeconds(3), Duration.ofSeconds(60), 256, 1);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        http = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"message\":{\"content\":\" Orientación hotelera \"}}");
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        client = new OllamaClient(http, JsonMapper.builder().build(), properties());
    }

    @Test
    void extractsContentAndReleasesCapacity() {
        assertEquals("Orientación hotelera", client.chat("consulta"));
        assertEquals("Orientación hotelera", client.chat("otra"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuresResponseTimeoutOnRequest() throws Exception {
        client.chat("consulta");
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(properties().responseTimeout(), request.getValue().timeout().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "null", "{}", "[]", "{\"message\":null}",
            "{\"message\":{\"content\":null}}", "{\"message\":{\"content\":123}}",
            "{\"message\":{\"content\":\"  \"}}"})
    void invalidResponsesAreSanitizedAndReleaseCapacity(String body) {
        when(response.body()).thenReturn(body);
        assertFailure(502);
        when(response.body()).thenReturn("{\"message\":{\"content\":\"ok\"}}");
        assertEquals("ok", client.chat("consulta"));
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 400, 404, 429, 500, 503})
    void httpErrorsAreSanitized(int status) {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("private upstream details");
        assertFailure(502);
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectionFailureIsUnavailableAndReleasesCapacity() throws Exception {
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IOException("private upstream details")))
                .thenReturn(CompletableFuture.completedFuture(response));
        assertFailure(503);
        assertEquals("Orientación hotelera", client.chat("consulta"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timeoutIsGatewayTimeout() throws Exception {
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("private upstream details")));
        assertFailure(504);
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptionRestoresFlagAndReleasesCapacity() throws Exception {
        var pending = new CompletableFuture<HttpResponse<String>>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(pending).thenReturn(CompletableFuture.completedFuture(response));
        try {
            Thread.currentThread().interrupt();
            assertFailure(503);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(pending.isCancelled());
        } finally {
            Thread.interrupted();
        }
        assertEquals("Orientación hotelera", client.chat("consulta"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saturationRejectsImmediatelyWithoutCallingUpstream() throws Exception {
        var entered = new CountDownLatch(1);
        var pending = new CompletableFuture<HttpResponse<String>>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            entered.countDown();
            return pending;
        });
        var first = CompletableFuture.supplyAsync(() -> client.chat("primera"));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertFailure(503));
            verify(http, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        } finally {
            pending.complete(response);
        }
        assertEquals("Orientación hotelera", first.get(5, TimeUnit.SECONDS));
        assertEquals("Orientación hotelera", client.chat("tercera"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadlineCancelsStalledResponseAndReleasesCapacity() {
        var pending = new CompletableFuture<HttpResponse<String>>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(pending).thenReturn(CompletableFuture.completedFuture(response));
        var settings = properties();
        client = new OllamaClient(http, JsonMapper.builder().build(), new AiProperties(settings.baseUrl(),
                settings.model(), settings.connectTimeout(), Duration.ofMillis(50), settings.maxTokens(), 1));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertFailure(504));
        assertTrue(pending.isCancelled());
        assertEquals("Orientación hotelera", client.chat("consulta"));
    }

    private void assertFailure(int status) {
        var error = assertThrows(ResponseStatusException.class, () -> client.chat("consulta"));
        assertEquals(status, error.getStatusCode().value());
        assertNull(error.getCause());
        assertFalse(error.getReason().contains("private"));
        assertFalse(error.getReason().contains("localhost"));
    }
}
