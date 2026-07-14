-- Scoped, multi-key API credentials.
--
-- Before V15 a user had at most one API key (enforced in application code by the
-- generate() path deleting any prior key, not by a DB constraint — V9 declared none).
-- V15 lifts that restriction: a user may now hold several named keys, each with an
-- access scope and an optional expiry. Existing keys default to FULL scope with no
-- label and no expiry, preserving their current behaviour.
ALTER TABLE user_api_keys
    ADD COLUMN IF NOT EXISTS label VARCHAR(60),
    ADD COLUMN IF NOT EXISTS scope VARCHAR(10) NOT NULL DEFAULT 'FULL',
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
