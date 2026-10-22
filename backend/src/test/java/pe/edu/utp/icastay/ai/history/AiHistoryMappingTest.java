package pe.edu.utp.icastay.ai.history;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class AiHistoryMappingTest {
    @Test
    void roundTripsStringsDatesAndSchemaWithoutJpaOrSecret() throws Exception {
        var conversions = MongoCustomConversions.create(adapter -> {});
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(AiConversationDocument.class));
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions); converter.afterPropertiesSet();
        var record = new AiConversationDocument(UUID.randomUUID().toString(), 1, UUID.randomUUID().toString(),
                AnonymousOwnerReference.derive(AiHistoryServiceTest.token(3)), "Pregunta", "Respuesta", "model",
                Instant.parse("2026-10-21T12:00:00Z"), Instant.parse("2026-11-20T12:00:00Z"));
        var bson = new Document(); converter.write(record, bson);
        assertEquals(record.id(), bson.getString("_id"));
        assertInstanceOf(Date.class, bson.get("createdAt"));
        assertInstanceOf(Date.class, bson.get("expiresAt"));
        assertEquals(1, bson.getInteger("schemaVersion"));
        assertFalse(bson.containsKey("sessionSecret"));
        assertFalse(bson.toJson().contains(AiHistoryServiceTest.token(3)));
        assertEquals(record, converter.read(AiConversationDocument.class, bson));
        assertEquals("ai_conversations", context.getRequiredPersistentEntity(AiConversationDocument.class).getCollection());
    }

    @Test
    void indexPlanIsScopedAndTtlUsesAbsoluteExpiration() {
        var definitions = AiHistoryIndexes.definitions();
        assertEquals(Document.parse("{ownerReference:1,conversationId:1,createdAt:-1,_id:-1}"), definitions.get(0).getIndexKeys());
        assertEquals(new Document("expiresAt", 1), definitions.get(1).getIndexKeys());
        assertEquals(0L, ((Number) definitions.get(1).getIndexOptions().get("expireAfterSeconds")).longValue());
    }
}
