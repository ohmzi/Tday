-- Security alerts pushed to the admin, plus the coalescing state that keeps an attack from
-- turning into a notification flood.
--
-- Two tables rather than one, deliberately:
--
-- `security_alerts` is append-only history — what the admin was actually told, and whether the
-- push left the building. It is the record the owner reads when a notification was missed, so it
-- must never be mutated after the fact.
--
-- `security_alert_state` is one mutable row per alert type holding the cooldown clock and the
-- count of events folded into the next alert. It lives in Postgres rather than in memory
-- precisely so a container restart cannot reset the cooldown — otherwise a crash-loop turns into
-- an alert-loop, which is the exact failure this feature exists to prevent.
--
-- The eventLog table is not reused for either: it is append-only (no per-type row to update) and
-- the retention sweep trims it, which would silently reset a cooldown.
CREATE TABLE IF NOT EXISTS security_alerts (
    id VARCHAR(30) PRIMARY KEY,
    alert_type VARCHAR(64) NOT NULL,
    detail TEXT NOT NULL,
    -- Events coalesced into this alert while the previous one was inside its cooldown.
    suppressed_count INTEGER NOT NULL DEFAULT 0,
    -- False when no admin push target existed or delivery threw; the row is still kept.
    pushed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS security_alerts_created_at_idx ON security_alerts (created_at);

CREATE TABLE IF NOT EXISTS security_alert_state (
    alert_type VARCHAR(64) PRIMARY KEY,
    last_sent_at TIMESTAMP,
    pending_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);
