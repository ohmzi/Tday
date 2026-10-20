-- Instance-wide settings an admin changes at runtime, as opposed to the env vars that configure
-- a deploy. A key/value table rather than a column per setting: each new switch is a row, not a
-- migration, and a missing row simply means "the default".
--
-- First key: `telemetry.sentry.enabled` ("true"/"false"). Whether this server may send its own
-- error reports to Sentry. A missing row means off, so a deploy that sets SENTRY_DSN still sends
-- nothing until an admin turns it on. Nothing here identifies a person.
--
-- A plain Exposed Table, not part of createMissingTablesAndColumns (same as abuse_blocks): Flyway
-- creates it, and the bootstrap never has to reconcile it.
CREATE TABLE IF NOT EXISTS instance_settings (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
