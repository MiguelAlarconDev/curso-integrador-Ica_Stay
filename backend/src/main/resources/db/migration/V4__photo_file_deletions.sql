-- Durable deletion intent: file deletion happens only after metadata commit.
CREATE TABLE photo_file_deletions (
    storage_key VARCHAR(512) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMPTZ,
    CONSTRAINT ck_photo_file_deletions_key CHECK (length(btrim(storage_key)) > 0 AND storage_key = btrim(storage_key))
);
CREATE INDEX idx_photo_file_deletions_retry ON photo_file_deletions(last_attempt_at NULLS FIRST, created_at, storage_key);
