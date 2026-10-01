-- Reusable floater lists.
--
-- A "reusable" floater list (e.g. a packing checklist or a weekly-cleaning list) can be
-- reset — all its floaters un-completed in one go — so the same list can be run again.
-- The flag just decides whether the Reset action is offered; existing lists default to
-- non-reusable. The table is unquoted floaterproject (Postgres folds it to lowercase;
-- the Exposed mapping Table("FloaterProject") is likewise unquoted at query time).
--
-- floaterproject is entirely Exposed-owned (SchemaUtils.createMissingTablesAndColumns, never a
-- Flyway CREATE TABLE), so a database that has never booted the app before does not have it
-- yet at Flyway-migrate time. IF EXISTS makes this a no-op there; Exposed then creates the
-- table moments later with `reusable` already declared on FloaterLists. (The unguarded form
-- made a clean install fail here with `relation "floaterproject" does not exist`.)
ALTER TABLE IF EXISTS floaterproject
    ADD COLUMN IF NOT EXISTS reusable BOOLEAN NOT NULL DEFAULT FALSE;
