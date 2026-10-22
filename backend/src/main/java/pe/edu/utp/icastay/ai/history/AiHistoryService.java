package pe.edu.utp.icastay.ai.history;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AiHistoryService {
    private static final Logger LOG = LoggerFactory.getLogger(AiHistoryService.class);
    private static final Duration RETENTION = Duration.ofDays(30);
    private final AiHistoryRepository repository;
    private final Clock clock;

    @Autowired
    public AiHistoryService(AiHistoryRepository repository) { this(repository, Clock.systemUTC()); }

    AiHistoryService(AiHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public enum StoreResult { STORED, UNAVAILABLE }

    public record HistoryPage(boolean available, List<AiConversationDocument> content, int page, int size, boolean hasNext) {
        public HistoryPage { content = List.copyOf(content); }
        @Override public String toString() { return "HistoryPage[contenido omitido]"; }
    }

    public StoreResult storeCompleted(String conversationId, String sessionSecret, String question, String answer, String model) {
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var document = new AiConversationDocument(UUID.randomUUID().toString(), AiConversationDocument.SCHEMA_VERSION,
                conversationId, AnonymousOwnerReference.derive(sessionSecret), question, answer, model, now, now.plus(RETENTION));
        try {
            repository.insertAndRenew(document);
            return StoreResult.STORED;
        } catch (DataAccessException exception) {
            LOG.warn("Historial IA temporalmente no disponible para escritura");
            return StoreResult.UNAVAILABLE;
        }
    }

    public HistoryPage findActive(String conversationId, String sessionSecret, int page, int size) {
        AiConversationDocument.requireUuid(conversationId);
        AiHistoryRepository.validatePage(page, size);
        String owner = AnonymousOwnerReference.derive(sessionSecret);
        try {
            var documents = repository.findActive(owner, conversationId, clock.instant(), page, size);
            return new HistoryPage(true, documents.subList(0, Math.min(documents.size(), size)), page, size, documents.size() > size);
        } catch (DataAccessException exception) {
            LOG.warn("Historial IA temporalmente no disponible para lectura");
            return new HistoryPage(false, List.of(), page, size, false);
        }
    }
}
