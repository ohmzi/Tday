-- Read-only iCalendar (ICS) feed tokens.
--
-- A user can publish a personal calendar-subscription URL (Apple Calendar, Google
-- Calendar, etc.) backed by an opaque token embedded in the path. Kept in its own
-- table (mirroring user_api_keys) rather than reusing an API key so that revoking a
-- leaked feed URL never disturbs a dashboard integration, and vice versa. Only a
-- SHA-256 hash of the token secret is stored; the plaintext is shown once at creation.
CREATE TABLE IF NOT EXISTS calendar_feed_tokens (
    id VARCHAR(30) PRIMARY KEY,
    "userID" VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    token_preview VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    "createdAt" TIMESTAMP NOT NULL,
    "revokedAt" TIMESTAMP
);

CREATE INDEX IF NOT EXISTS calendar_feed_tokens_userid_idx ON calendar_feed_tokens ("userID");
