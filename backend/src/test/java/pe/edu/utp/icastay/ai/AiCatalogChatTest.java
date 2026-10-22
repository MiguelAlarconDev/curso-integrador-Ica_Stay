package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.edu.utp.icastay.ai.catalog.AiCatalogTool;
import pe.edu.utp.icastay.ai.history.AiHistoryService;
import tools.jackson.databind.json.JsonMapper;

class AiCatalogChatTest {
    @Test void verifiedFactsBypassModelAndKeepAnswerContract() {
        var catalog = mock(AiCatalogTool.class); var ollama = mock(OllamaClient.class); var history = mock(AiHistoryService.class);
        when(catalog.answer(anyString())).thenReturn(Optional.of("Precio por noche: 100.00 PEN"));
        var service = new AiChatService(ollama, history, OllamaClientTest.properties(), catalog);
        var result = service.chat(new AiChatRequest("Precios en Ica"), null);
        assertEquals("{\"answer\":\"Precio por noche: 100.00 PEN\"}", JsonMapper.builder().build().writeValueAsString(result));
        verifyNoInteractions(ollama, history);
        var session = new AiSessionService("", false, "http://localhost:4200").create();
        service.completed(new AiChatRequest("Precios en Ica"), result, session);
        verify(history).storeCompleted(session.conversationId(), session.secret(), "Precios en Ica", result.answer(), "icastay-public-catalog-v1");
    }

    @Test void privacyAllowsIsoDatesButStillExcludesSecretsAndIdentifiers() {
        assertTrue(AiHistoryPrivacy.mayStore("Disponibilidad 2030-01-02 al 2030-01-04", "Respuesta"));
        assertFalse(AiHistoryPrivacy.mayStore("Mi contraseña 2030-01-02", "Respuesta"));
        assertFalse(AiHistoryPrivacy.mayStore("12345678", "Respuesta"));
        assertFalse(AiHistoryPrivacy.mayStore("2030-99-99", "Respuesta"));
    }

    @Test void oldCatalogFactsAreNeverSentBackToOllamaAsCurrentContext() {
        var catalog = mock(AiCatalogTool.class); var ollama = mock(OllamaClient.class); var history = mock(AiHistoryService.class);
        var session = new AiSessionService("", false, "http://localhost:4200").create();
        var old = new pe.edu.utp.icastay.ai.history.AiConversationDocument(java.util.UUID.randomUUID().toString(), 1,
                session.conversationId(), "0".repeat(64), "Precios en Ica", "Precio anterior 100 PEN", "icastay-public-catalog-v1",
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(60));
        when(history.findActive(anyString(), anyString(), eq(0), eq(5))).thenReturn(
                new AiHistoryService.HistoryPage(true, java.util.List.of(old), 0, 5, false));
        when(ollama.chat("Hola")).thenReturn("Hola");
        var service = new AiChatService(ollama, history, OllamaClientTest.properties(), catalog);
        assertEquals("Hola", service.chat(new AiChatRequest("Hola"), session).answer());
        verify(ollama).chat("Hola"); verify(ollama, never()).chat(anyString(), anyList());
    }
}
