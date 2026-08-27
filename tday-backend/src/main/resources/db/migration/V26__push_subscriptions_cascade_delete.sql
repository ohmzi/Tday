-- Realign push_subscriptions."userID" with the ON DELETE CASCADE V7 asked for.
--
-- V7 created the reference as ON DELETE CASCADE and V8 renamed it, but neither constraint is
-- live any more. DatabaseConfig runs Flyway first and then
-- SchemaUtils.createMissingTablesAndColumns, and push_subscriptions is in that list; Exposed
-- compares each live foreign key against the rule its Kotlin column declares and drops and
-- recreates the ones that differ. PushSubscriptions.userID declared no onDelete, which Exposed
-- reads as its RESTRICT default, so V7's CASCADE was replaced by
-- fk_push_subscriptions_userid__id ON DELETE RESTRICT. Deleting an account that had ever
-- enabled notifications failed on that constraint and rolled the whole purge back as a 500.
--
-- PushSubscriptions.userID now declares ReferenceOption.CASCADE, so this only has to bring the
-- live constraint up to what Exposed will assert from the next boot onwards. The definition
-- below matches that reconciliation exactly -- same name, and ON UPDATE RESTRICT because that
-- is what Exposed emits for an unspecified onUpdate on PostgreSQL -- so the comparison finds
-- nothing to change and no DDL is re-issued on startup.
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
