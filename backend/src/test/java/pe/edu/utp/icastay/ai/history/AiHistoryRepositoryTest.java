package pe.edu.utp.icastay.ai.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class AiHistoryRepositoryTest {
    @Test
    void queryAlwaysIncludesOwnershipExpirationSchemaAndPageLimit() {
        var mongo = mock(MongoTemplate.class); var repository = new AiHistoryRepository(mongo);
        String owner = "a".repeat(64); String conversation = UUID.randomUUID().toString(); var now = Instant.now();
        repository.findActive(owner, conversation, now, 2, 20);
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(AiConversationDocument.class));
        assertEquals(owner, query.getValue().getQueryObject().get("ownerReference"));
        assertEquals(conversation, query.getValue().getQueryObject().get("conversationId"));
        assertEquals(1, query.getValue().getQueryObject().get("schemaVersion"));
        assertEquals(now, query.getValue().getQueryObject().get("expiresAt", org.bson.Document.class).get("$gt"));
        assertEquals(40, query.getValue().getSkip()); assertEquals(21, query.getValue().getLimit());
        assertEquals(-1, query.getValue().getSortObject().get("createdAt"));
        assertEquals(-1, query.getValue().getSortObject().get("id"));
    }

    @Test
    void renewalUsesMaxOnlyOnLiveOwnedRecordsAfterInsertion() {
        var mongo = mock(MongoTemplate.class); var repository = new AiHistoryRepository(mongo); var now = Instant.now();
        var document = new AiConversationDocument(UUID.randomUUID().toString(), 1, UUID.randomUUID().toString(), "a".repeat(64),
                "Q", "A", "model", now, now.plusSeconds(2592000));
        repository.insertAndRenew(document);
        var order = inOrder(mongo);
        order.verify(mongo).insert(document);
        var query = ArgumentCaptor.forClass(Query.class); var update = ArgumentCaptor.forClass(UpdateDefinition.class);
        order.verify(mongo).updateMulti(query.capture(), update.capture(), eq(AiConversationDocument.class));
        assertEquals(document.ownerReference(), query.getValue().getQueryObject().get("ownerReference"));
        assertEquals(document.conversationId(), query.getValue().getQueryObject().get("conversationId"));
        assertNotNull(query.getValue().getQueryObject().get("expiresAt"));
        assertEquals(document.expiresAt(), update.getValue().getUpdateObject().get("$max", org.bson.Document.class).get("expiresAt"));
        verifyNoMoreInteractions(mongo);
    }
}
