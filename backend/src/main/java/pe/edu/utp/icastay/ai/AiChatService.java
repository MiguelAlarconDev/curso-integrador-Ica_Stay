package pe.edu.utp.icastay.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiChatService {
    private final OllamaClient ollama;
    private final pe.edu.utp.icastay.ai.history.AiHistoryService history;
    private final AiProperties properties;

    public AiChatService(OllamaClient ollama, pe.edu.utp.icastay.ai.history.AiHistoryService history, AiProperties properties) {
        this.ollama = ollama;
        this.history = history;
        this.properties = properties;
    }

    public AiChatResponse chat(AiChatRequest request, AiSessionService.Session session) {
        if (request == null || request.message() == null || request.message().isBlank()
                || request.message().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "message debe contener entre 1 y 1000 caracteres y no estar en blanco");
        }
        checkInterrupted();
        var previous = history.findActive(session.conversationId(), session.secret(), 0, 5);
        var turns = previous.available() ? previous.content().stream()
                .filter(item -> AiHistoryPrivacy.mayStore(item.question(), item.answer()))
                .map(item -> new OllamaContextTurn(item.question(), item.answer())).toList() : java.util.List.<OllamaContextTurn>of();
        checkInterrupted();
        String message = request.message().strip();
        var answer = new AiChatResponse(turns.isEmpty() ? ollama.chat(message) : ollama.chat(message, turns));
        checkInterrupted();
        return answer;
    }

    public void completed(AiChatRequest request, AiChatResponse response, AiSessionService.Session session) {
        if (Thread.currentThread().isInterrupted() || !AiHistoryPrivacy.mayStore(request.message(), response.answer())) return;
        history.storeCompleted(session.conversationId(), session.secret(), request.message().strip(), response.answer(), properties.model());
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La consulta fue interrumpida");
    }
}
