package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AiChatServiceTest {
    @Test
    void nullRequestAndNullMessageAreRejectedBeforeClient() {
        var client = mock(OllamaClient.class);
        var service = new AiChatService(client);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.chat(null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.chat(new AiChatRequest(null))).getStatusCode().value());
        verifyNoInteractions(client);
    }
}
