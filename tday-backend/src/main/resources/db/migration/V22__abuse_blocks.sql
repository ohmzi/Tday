-- Adaptive abuse blocking.
--
-- `auththrottle` already rate-limits: a caller over quota gets a 429 and may retry next
-- window, forever. This table is the longer-lived escalation on top of it — a caller that
-- demonstrates SUSTAINED abuse stops being served the path at all for hours or days, so it
-- stops consuming database round-trips and PBKDF2 cycles every window.
--
-- One row per (subject, scope). The row doubles as the signal accumulator, which is why
-- blocked_until is NULLABLE: a row usually exists long before any block is applied, holding
-- the running counters for the current window. Nothing here identifies a person — subject_hash
-- is the HMAC-SHA256 of "ip:<addr>" under AUTH_SECRET, never the address itself.
--
-- scope is a PATH, not the whole app:
--   register — POST /api/auth/register only
--   auth     — sign-in plus the security-question / reset endpoints
-- Kept separate on purpose: a registration flood must never cost the owner their own login.
CREATE TABLE IF NOT EXISTS abuse_blocks (
    id VARCHAR(30) PRIMARY KEY,
    subject_hash VARCHAR(255) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    -- NULL when the row is only accumulating signals; a past value means the block expired.
    blocked_until TIMESTAMP,
    -- How many blocks this subject has earned; drives the 1h -> 24h -> 7d escalation.
    strikes INTEGER NOT NULL DEFAULT 0,
    -- Scope-generic velocity counter: register throttle violations, or auth lockout events.
    signal_count INTEGER NOT NULL DEFAULT 0,
    -- register scope only: accounts created from this subject that are still PENDING.
    pending_signups INTEGER NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL,
    reason VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS abuse_blocks_subject_scope_key ON abuse_blocks (subject_hash, scope);
-- Every unauthenticated request on a guarded path reads this, and the retention sweep scans it.
CREATE INDEX IF NOT EXISTS abuse_blocks_blocked_until_idx ON abuse_blocks (blocked_until);
