package pe.edu.utp.icastay.ai.history;

import java.time.Duration;
import java.util.List;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;

/** Deployment plan only. Never automatically applies indexes to an existing collection. */
public final class AiHistoryIndexes {
    private AiHistoryIndexes() {}

    public static List<IndexDefinition> definitions() {
        return List.of(new Index().on("ownerReference", Direction.ASC).on("conversationId", Direction.ASC)
                        .on("createdAt", Direction.DESC).on("_id", Direction.DESC).named("owner_conversation_created"),
                new Index().on("expiresAt", Direction.ASC).expire(Duration.ZERO).named("expires_at_ttl"));
    }
}
