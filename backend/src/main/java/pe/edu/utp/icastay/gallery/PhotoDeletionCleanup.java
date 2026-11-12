package pe.edu.utp.icastay.gallery;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PhotoDeletionCleanup {
    private static final Logger log = LoggerFactory.getLogger(PhotoDeletionCleanup.class);
    private final JdbcTemplate jdbc;
    private final PhotoStorage storage;
    private final TransactionTemplate transaction;

    PhotoDeletionCleanup(JdbcTemplate jdbc, PhotoStorage storage, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.storage = storage;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${icastay.photos.cleanup-delay:60000}", initialDelayString = "${icastay.photos.cleanup-delay:60000}")
    public void retryPending() {
        try {
            List<String> keys = jdbc.query("SELECT storage_key FROM photo_file_deletions ORDER BY last_attempt_at NULLS FIRST,created_at,storage_key LIMIT 20",
                    (row, index) -> row.getString(1));
            for (String key : keys) {
                transaction.executeWithoutResult(status -> {
                    var claimed = jdbc.query("SELECT storage_key FROM photo_file_deletions WHERE storage_key=? FOR UPDATE SKIP LOCKED",
                            (row, index) -> row.getString(1), key);
                    if (claimed.isEmpty()) return;
                    jdbc.update("UPDATE photo_file_deletions SET last_attempt_at=CURRENT_TIMESTAMP WHERE storage_key=?", key);
                    try {
                        storage.delete(key); // Idempotent: a missing file also completes the intent.
                        jdbc.update("DELETE FROM photo_file_deletions WHERE storage_key=?", key);
                    } catch (IOException error) {
                        log.warn("Photo file deletion pending for key {}", key, error);
                    }
                });
            }
        } catch (RuntimeException error) {
            // Keep committed deletion intents for the next attempt; never turn a successful mutation into 500.
            log.warn("Photo cleanup deferred", error);
        }
    }
}
