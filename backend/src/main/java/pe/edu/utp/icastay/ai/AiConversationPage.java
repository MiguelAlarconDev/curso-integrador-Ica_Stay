package pe.edu.utp.icastay.ai;

import java.time.Instant;
import java.util.List;

public record AiConversationPage(String conversationId, List<Exchange> content, int page, int size, boolean hasNext) {
    public record Exchange(String question, String answer, Instant createdAt, Instant expiresAt) {
        @Override public String toString() { return "Exchange[omitido]"; }
    }
    @Override public String toString() { return "AiConversationPage[omitida]"; }
}
