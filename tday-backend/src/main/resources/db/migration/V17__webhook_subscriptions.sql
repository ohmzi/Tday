-- Outbound webhook subscriptions.
--
-- Self-hosters can register HTTP endpoints that receive an ID-only "something
-- changed" ping (mirroring the realtime /ws events) signed with a per-subscription
-- HMAC secret. Delivery outcomes are recorded; a subscription auto-disables after
-- repeated consecutive failures. The secret is stored (encrypted at rest) rather than
-- hashed, because the server must reproduce it to sign every payload.
CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id VARCHAR(30) PRIMARY KEY,
    "userID" VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    secret TEXT NOT NULL,
    -- Comma-separated event types (e.g. "todo.changed,list.changed"); NULL/empty = all.
    event_filter TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    last_status INTEGER,
    last_attempt_at TIMESTAMP,
    "createdAt" TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS webhook_subscriptions_userid_idx ON webhook_subscriptions ("userID");
