package pe.edu.utp.icastay.ai;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiChatService {
    private final OllamaClient ollama;

    public AiChatService(OllamaClient ollama) {
        this.ollama = ollama;
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()
                || request.message().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "message debe contener entre 1 y 1000 caracteres y no estar en blanco");
        }
        return new AiChatResponse(ollama.chat(request.message().strip()));
    }
}
