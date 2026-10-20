-- Give the two Exposed-reconciled foreign keys that should cascade the rule they claim to have.
--
-- Both tables here are in the SchemaUtils.createMissingTablesAndColumns list in DatabaseConfig,
-- which runs after Flyway: Exposed compares each live foreign key against the rule its Kotlin
-- column declares and drops and recreates the ones that differ. So changing only the SQL would
-- be reverted on the next boot, and changing only the Kotlin would leave that rewrite to happen
-- as an unmigrated ALTER TABLE against a live database. Each constraint below is therefore
-- paired with the matching ReferenceOption.CASCADE in db/tables/, and is spelled the way Exposed
-- spells it -- the fk_<table>_<column>__<targetcolumn> name, and an explicit ON UPDATE RESTRICT,
-- because Exposed emits RESTRICT for an unspecified onUpdate on PostgreSQL and an update-rule
-- difference on its own is enough to make it drop and recreate the constraint on every start.
--
-- ---------------------------------------------------------------------------------------------
-- 1. push_subscriptions."userID" -> "User".id: realign with the ON DELETE CASCADE V7 asked for.
--
-- V7 created the reference as ON DELETE CASCADE and V8 renamed it, but neither constraint is
-- live any more: PushSubscriptions.userID declared no onDelete, which Exposed reads as its
-- RESTRICT default, so the reconciliation above replaced V7's CASCADE with
-- fk_push_subscriptions_userid__id ON DELETE RESTRICT. Deleting an account that had ever
-- enabled notifications failed on that constraint and rolled the whole purge back as a 500.
--
-- PushSubscriptions.userID now declares ReferenceOption.CASCADE, so this only has to bring the
-- live constraint up to what Exposed will assert from the next boot onwards.
--
-- The explicit delete in AdminServiceImpl.purgeUser is what actually keeps account deletion
-- working; this migration stops the schema from claiming something it does not do.

ALTER TABLE IF EXISTS push_subscriptions
    DROP CONSTRAINT IF EXISTS fk_push_subscriptions_userid__id;

-- V7's original name and the one V8 renamed it to, dropped in case an instance still carries
-- either of them (an older deployment, or one restored from a pre-V8 dump).
ALTER TABLE IF EXISTS push_subscriptions
    DROP CONSTRAINT IF EXISTS "push_subscriptions_userID_fkey";

ALTER TABLE IF EXISTS push_subscriptions
    DROP CONSTRAINT IF EXISTS push_subscriptions_userid_fkey;

ALTER TABLE IF EXISTS push_subscriptions
    ADD CONSTRAINT fk_push_subscriptions_userid__id
    FOREIGN KEY ("userID") REFERENCES "User"(id) ON DELETE CASCADE ON UPDATE RESTRICT;

-- ---------------------------------------------------------------------------------------------
-- 2. todo_instances."todoId" -> todos.id: make the cascade the row's lifetime already implies.
--
-- Nothing is broken here today: the V2 baseline created this one RESTRICT and
-- TodoInstances.todoId declared no onDelete, so the schema and the code have always agreed. It
-- is a latent version of the push_subscriptions bug rather than the bug itself -- a todo_instance
-- is an override of a single occurrence of its todo and cannot outlive it, yet the only reason
-- deleting a todo works is that every caller (ListService.deleteLists, TodoService, and
-- AdminServiceImpl.purgeUser) happens to clear the instances first. The next delete path written
-- without that knowledge fails on fk_todo_instances_todoid__id.
--
-- Those explicit ordered deletes stay: they are what survives regardless of which rule the live
-- constraint carries, and AdminPurgeTest asserts on the purge, not on the schema. CascadeDeleteTest
-- covers the schema half -- it builds H2 from the Exposed declarations and deletes a todo with no
-- prior instance cleanup, so dropping either onDelete below fails the build.

ALTER TABLE IF EXISTS todo_instances
    DROP CONSTRAINT IF EXISTS fk_todo_instances_todoid__id;

ALTER TABLE IF EXISTS todo_instances
    ADD CONSTRAINT fk_todo_instances_todoid__id
    FOREIGN KEY ("todoId") REFERENCES todos(id) ON DELETE CASCADE ON UPDATE RESTRICT;
