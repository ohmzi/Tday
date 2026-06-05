CREATE TABLE IF NOT EXISTS user_api_keys (
    id VARCHAR(30) PRIMARY KEY,
    "userID" VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    key_hash TEXT NOT NULL,
    key_preview VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    "createdAt" TIMESTAMP NOT NULL,
    "revokedAt" TIMESTAMP
);

CREATE INDEX IF NOT EXISTS user_api_keys_userid_idx ON user_api_keys ("userID");
CREATE INDEX IF NOT EXISTS user_api_keys_enabled_idx ON user_api_keys (enabled);
