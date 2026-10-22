package pe.edu.utp.icastay.ai;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

record OllamaChatRequest(String model, List<Message> messages, boolean stream, Options options) {
    record Message(String role, String content) {}
    record Options(@JsonProperty("num_predict") int maxTokens) {}
}
