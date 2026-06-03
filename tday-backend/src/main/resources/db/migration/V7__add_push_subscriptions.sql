CREATE TABLE IF NOT EXISTS push_subscriptions (
    id VARCHAR(30) PRIMARY KEY,
    "userID" VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth VARCHAR(64) NOT NULL,
    "createdAt" TIMESTAMP NOT NULL,
    CONSTRAINT push_subscriptions_user_endpoint_unique UNIQUE ("userID", endpoint)
);

CREATE INDEX IF NOT EXISTS push_subscriptions_userid_idx ON push_subscriptions ("userID");
