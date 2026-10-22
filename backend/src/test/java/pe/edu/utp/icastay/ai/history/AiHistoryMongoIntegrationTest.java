package pe.edu.utp.icastay.ai.history;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

@EnabledIfEnvironmentVariable(named = "ICASTAY_TEST_MONGODB_URI", matches = ".+")
class AiHistoryMongoIntegrationTest {
    private static MongoClient client;
    private static MongoTemplate mongo;
    private static String database;
    private final String conversation = UUID.randomUUID().toString();
    private final String secret = AiHistoryServiceTest.token(5);
    private final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    @BeforeAll
    static void connectOnlyToIsolatedDatabase() {
        var uri = new ConnectionString(System.getenv("ICASTAY_TEST_MONGODB_URI"));
        if (!uri.getHosts().equals(java.util.List.of("127.0.0.1:27028"))
                || uri.getDatabase() == null || !uri.getDatabase().matches("icastay_ai_test_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Tests requieren Mongo aislado en 127.0.0.1:27028 y base icastay_ai_test_*");
        }
        database = uri.getDatabase() + "_" + UUID.randomUUID().toString().replace("-", "");
        client = MongoClients.create(uri);
        mongo = new MongoTemplate(client, database);
    }

    @BeforeEach void cleanTestCollection() { mongo.dropCollection(AiConversationDocument.class); }
    @AfterAll static void cleanTestDatabase() {
        if (client != null) { try { client.getDatabase(database).drop(); } finally { client.close(); } }
    }

    @Test
    void savesMapsPagesAndIsolatesOwnersAndConversations() {
        var repository = new AiHistoryRepository(mongo);
        var service = new AiHistoryService(repository, Clock.fixed(now, ZoneOffset.UTC));
        for (int i = 0; i < 3; i++) assertEquals(AiHistoryService.StoreResult.STORED,
                service.storeCompleted(conversation, secret, "Pregunta " + i, "Respuesta", "model"));
        var first = service.findActive(conversation, secret, 0, 2);
        var next = service.findActive(conversation, secret, 1, 2);
        assertTrue(first.available()); assertTrue(first.hasNext()); assertEquals(2, first.content().size());
        assertFalse(next.hasNext()); assertEquals(1, next.content().size());
        assertFalse(first.content().stream().anyMatch(item -> item.id().equals(next.content().getFirst().id())));
        assertTrue(service.findActive(conversation, AiHistoryServiceTest.token(6), 0, 20).content().isEmpty());
        assertTrue(service.findActive(UUID.randomUUID().toString(), secret, 0, 20).content().isEmpty());
    }

    @Test
    void activityExtendsLiveHistoryButNeverResurrectsExpiredHistoryOrOtherOwners() {
        var repository = new AiHistoryRepository(mongo);
        var old = document(secret, now.minus(Duration.ofDays(10)), now.plus(Duration.ofDays(20)));
        var expired = document(secret, now.minus(Duration.ofDays(31)), now);
        var other = document(AiHistoryServiceTest.token(6), now.minus(Duration.ofDays(10)), now.plus(Duration.ofDays(20)));
        mongo.insert(old); mongo.insert(expired); mongo.insert(other);
        var service = new AiHistoryService(repository, Clock.fixed(now, ZoneOffset.UTC));
        service.storeCompleted(conversation, secret, "Nueva", "Respuesta", "model");
        assertEquals(now.plus(Duration.ofDays(30)), mongo.findById(old.id(), AiConversationDocument.class).expiresAt());
        assertEquals(now, mongo.findById(expired.id(), AiConversationDocument.class).expiresAt());
        assertEquals(other.expiresAt(), mongo.findById(other.id(), AiConversationDocument.class).expiresAt());
        assertEquals(2, service.findActive(conversation, secret, 0, 20).content().size());
        var earlier = new AiHistoryService(repository, Clock.fixed(now.minusSeconds(1), ZoneOffset.UTC));
        earlier.storeCompleted(conversation, secret, "Concurrente", "Respuesta", "model");
        assertEquals(now.plus(Duration.ofDays(30)), mongo.findById(old.id(), AiConversationDocument.class).expiresAt());
    }

    @Test
    void deploysIndexPlanOnlyInTestDatabase() {
        for (var index : AiHistoryIndexes.definitions()) mongo.indexOps(AiConversationDocument.class).createIndex(index);
        var indexes = mongo.getCollection(AiConversationDocument.COLLECTION).listIndexes().into(new ArrayList<>());
        assertEquals(3, indexes.size());
        var ttl = indexes.stream().filter(index -> "expires_at_ttl".equals(index.getString("name"))).findFirst().orElseThrow();
        assertEquals(0L, ((Number) ttl.get("expireAfterSeconds")).longValue());
        assertEquals(new org.bson.Document("expiresAt", 1), ttl.get("key"));
    }

    private AiConversationDocument document(String token, Instant created, Instant expires) {
        return new AiConversationDocument(UUID.randomUUID().toString(), 1, conversation,
                AnonymousOwnerReference.derive(token), "Q", "A", "model", created, expires);
    }
}
