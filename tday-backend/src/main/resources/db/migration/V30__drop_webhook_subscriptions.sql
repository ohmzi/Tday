-- Webhooks are gone from the product: the settings card that created a subscription, the routes
-- that served them, the dispatcher that fanned every mutation out to them, and the guide topic
-- that explained them. This drops the data they were.
--
-- V17 stays exactly as it was. Flyway validates the checksum of every applied migration, so
-- editing or deleting it would fail the next boot of every existing database — a fresh install
-- therefore creates this table at V17 and drops it here.
--
-- `webhook_subscriptions` has never been in `DatabaseConfig`'s `createMissingTablesAndColumns`
-- list (see "Tables absent from the createMissingTablesAndColumns list" in docs/DATA_MODEL.md),
-- so Exposed never reconciled its columns or foreign keys and will not recreate it now that the
-- table object is gone from `db/tables/`. The drop is the whole story.
--
-- Destructive and irreversible: Flyway has no down-migrations, so there is no restoring these
-- rows by rolling the image back. What they held was outbound URLs and signing secrets, so an
-- integration pointed at T'Day stops receiving its "something changed" ping. Account purge
-- deleted from this table as one of its child deletes and no longer does; the rows leave with it.

DROP TABLE IF EXISTS webhook_subscriptions;
