package pe.edu.utp.icastay.ai;

public record AiChatResponse(String answer, @com.fasterxml.jackson.annotation.JsonIgnore String source) {
    public AiChatResponse(String answer) { this(answer, null); }
}
