package pe.edu.utp.icastay.ai.history;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = AiConversationDocument.COLLECTION)
public record AiConversationDocument(@MongoId(FieldType.STRING) String id, int schemaVersion,
        String conversationId, String ownerReference, String question, String answer,
        String model, Instant createdAt, Instant expiresAt) {
    public static final String COLLECTION = "ai_conversations";
    public static final int SCHEMA_VERSION = 1;

    public AiConversationDocument {
        requireUuid(id);
        requireUuid(conversationId);
        if (schemaVersion != SCHEMA_VERSION || ownerReference == null || !ownerReference.matches("[0-9a-f]{64}")
                || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) throw invalid();
        requireText(question, 1000);
        requireText(answer, 16000);
        requireText(model, 128);
    }

    static void requireUuid(String value) {
        if (value == null) throw invalid();
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value) || id.version() != 4 || id.variant() != 2) throw invalid();
        } catch (IllegalArgumentException exception) { throw invalid(); }
    }

    private static void requireText(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Datos de historial inválidos");
    }

    @Override public String toString() { return "AiConversationDocument[contenido omitido]"; }
}
