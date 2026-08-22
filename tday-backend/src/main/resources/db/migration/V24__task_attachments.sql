-- Pictures attached to a task. Both task types are supported, and they are separate
-- entities with separate tables, so an attachment points at exactly one of them.
--
-- Bytes live on disk (TDAY_ATTACHMENT_DIR), not in Postgres: images would otherwise
-- bloat every pg_dump. This table holds only the metadata plus the relative storage
-- path, and the row is the record of truth — an orphaned file on disk is harmless,
-- an orphaned row is not.
--
-- Raw migrations must spell names exactly as the Exposed mapping does: `todos` is unquoted
-- lowercase, `"User"` is quoted CamelCase, and camelCase columns are quoted.
--
-- No foreign key on "floaterID", deliberately. Flyway runs BEFORE Exposed's
-- createMissingTablesAndColumns, and `floaters` is Exposed-managed — it is absent from the V2
-- baseline, so a FK to it fails the migration and crashes startup on any database that has not
-- already been through an Exposed boot. This mirrors floater_list_shares (V14), which drops its
-- FKs for the same reason; referential cleanup for floaters is owned by the services.
CREATE TABLE IF NOT EXISTS task_attachments (
    id VARCHAR(30) PRIMARY KEY,
    "userID" VARCHAR(30) NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "todoID" VARCHAR(30) REFERENCES todos(id) ON DELETE CASCADE,
    "floaterID" VARCHAR(30),
    "fileName" TEXT NOT NULL,
    "contentType" VARCHAR(100) NOT NULL,
    "sizeBytes" BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    "storageKey" TEXT NOT NULL,
    "thumbnailKey" TEXT,
    "createdAt" TIMESTAMP NOT NULL,
    -- Exactly one owner. Without this a row could attach to both feeds at once, or to
    -- neither, and the cascade deletes above would never reclaim it.
    CONSTRAINT task_attachments_one_owner CHECK (
        ("todoID" IS NOT NULL AND "floaterID" IS NULL)
        OR ("todoID" IS NULL AND "floaterID" IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS task_attachments_todoid_idx ON task_attachments ("todoID");
CREATE INDEX IF NOT EXISTS task_attachments_floaterid_idx ON task_attachments ("floaterID");
CREATE INDEX IF NOT EXISTS task_attachments_userid_idx ON task_attachments ("userID");
