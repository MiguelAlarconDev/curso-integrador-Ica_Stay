ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 CHECK (token_version >= 0);
