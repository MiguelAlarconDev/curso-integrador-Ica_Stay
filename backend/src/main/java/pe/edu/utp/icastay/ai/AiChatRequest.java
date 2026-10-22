package pe.edu.utp.icastay.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

public record AiChatRequest(String message) {
    // Reject JSON coercion (for example, a numeric message becoming a String).
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AiChatRequest fromJson(JsonNode body) {
        JsonNode message = body == null ? null : body.get("message");
        if (body == null || !body.isObject() || message == null || !message.isTextual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensaje inválido");
        }
        return new AiChatRequest(message.stringValue());
    }
}
