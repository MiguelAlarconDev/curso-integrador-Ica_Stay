package pe.edu.utp.icastay.ai.history;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class AiHistoryRepository {
    private final MongoTemplate mongo;

    public AiHistoryRepository(MongoTemplate mongo) { this.mongo = mongo; }

    void insertAndRenew(AiConversationDocument document) {
        mongo.insert(document);
        mongo.updateMulti(scope(document.ownerReference(), document.conversationId(), document.createdAt()),
                new Update().max("expiresAt", document.expiresAt()), AiConversationDocument.class);
    }

    List<AiConversationDocument> findActive(String owner, String conversation, Instant now, int page, int size) {
        validatePage(page, size);
        return mongo.find(scope(owner, conversation, now)
                .with(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .skip((long) page * size).limit(size + 1), AiConversationDocument.class);
    }

    static void validatePage(int page, int size) {
        if (page < 0 || page > 1000 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Paginación de historial inválida");
        }
    }

    private Query scope(String owner, String conversation, Instant now) {
        if (owner == null || !owner.matches("[0-9a-f]{64}") || now == null) {
            throw new IllegalArgumentException("Consulta de historial inválida");
        }
        AiConversationDocument.requireUuid(conversation);
        return Query.query(Criteria.where("ownerReference").is(owner).and("conversationId").is(conversation)
                .and("schemaVersion").is(AiConversationDocument.SCHEMA_VERSION).and("expiresAt").gt(now));
    }
}
