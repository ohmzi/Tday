-- Let completed-floater history survive its source list being deleted, and
-- let FloaterService.uncompleteFloater() converge repeated undos of items
-- from the same deleted list onto one recreated list instead of duplicating
-- it.
--
-- completedfloaters and floaterproject are both entirely Exposed-managed --
-- see V19's note on floaterproject -- so this migration follows the same
-- discipline V26 used for push_subscriptions/todo_instances: change the live
-- constraint AND the matching Kotlin declaration in db/tables/ together.
-- SchemaUtils.createMissingTablesAndColumns runs after Flyway on every boot
-- and reconciles any foreign key whose live rule differs from what its
-- Kotlin column now declares, so changing only the SQL would be reverted on
-- the next boot, and changing only the Kotlin would leave that rewrite to
-- happen as an unmigrated ALTER TABLE against a live database.
--
-- Every statement below is guarded (IF EXISTS / IF NOT EXISTS) because a
-- database that has never booted the app before will not have either table
-- yet at Flyway-migrate time -- Exposed only creates them afterwards, from
-- whatever the Kotlin declarations say by then. On that path every statement
-- here is a no-op and the fresh tables come out right the first time.
--
-- The new partial unique index on floaterproject (userID, recreatedFromListID)
-- is declared in Kotlin only, not here: unlike a foreign key's onDelete rule,
-- an index Exposed's reconciliation pass finds missing is only ever added,
-- never dropped or rewritten out from under a differently-worded migration,
-- so there is no drift to guard against and no fresh-install ordering problem
-- to route around.

-- 1. completedfloaters."projectID" -> floaterproject.id: RESTRICT (Exposed's
-- default for an unspecified onDelete) to SET NULL. Today this FK carries no
-- override, which is *why* FloaterListService.deleteMany() has always had to
-- explicitly purge CompletedFloaters rows before deleting the list -- a
-- RESTRICT constraint would otherwise block the delete outright. SET NULL
-- lets the delete go through and Postgres detach the completion record on
-- its own, so that explicit purge goes away as of this change.
ALTER TABLE IF EXISTS completedfloaters
    ADD COLUMN IF NOT EXISTS "originalListID" character varying(30);

-- Backfill: every completedfloaters row that predates this column keeps its
-- list correlation, so a list deleted the day after this migration ships
-- still converges its undos correctly instead of landing every one of them
-- in a separate recreated list.
UPDATE completedfloaters
SET "originalListID" = "projectID"
WHERE "originalListID" IS NULL
  AND "projectID" IS NOT NULL;

ALTER TABLE IF EXISTS completedfloaters
    DROP CONSTRAINT IF EXISTS fk_completedfloaters_projectid__id;

ALTER TABLE IF EXISTS completedfloaters
    ADD CONSTRAINT fk_completedfloaters_projectid__id
    FOREIGN KEY ("projectID") REFERENCES floaterproject(id) ON DELETE SET NULL ON UPDATE RESTRICT;

-- 2. floaterproject."recreatedFromListID": the find-or-create marker used to
-- land a second undo of a different item from the same deleted list on the
-- list the first undo already recreated. Unconstrained like originalListID
-- above, and for the same reason -- by the time it is ever set, the list id
-- it names no longer exists, so it cannot be a foreign key.
ALTER TABLE IF EXISTS floaterproject
    ADD COLUMN IF NOT EXISTS "recreatedFromListID" character varying(30);
