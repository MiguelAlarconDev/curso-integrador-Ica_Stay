package pe.edu.utp.icastay.ai;

public record AiChatResponse(String answer, @com.fasterxml.jackson.annotation.JsonIgnore String source,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        pe.edu.utp.icastay.ai.catalog.AiCatalogData catalog) {
    public AiChatResponse(String answer) { this(answer, null, null); }
    public AiChatResponse(String answer, String source) { this(answer, source, null); }
}
