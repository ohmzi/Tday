package com.ohmz.tday.compose.core.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives [runLegacyCacheMigration] across simulated launches.
 *
 * Mirrors the real environment in the one way that matters: whenever a launch does not migrate,
 * Room still goes on to build an encrypted database moments later ([roomOpensDatabase]). That is
 * the step the old file-existence check tripped over.
 */
private class MigrationHarness(
    var legacyExists: Boolean = true,
    var encryptedExists: Boolean = false,
    var encryptedDiscardable: Boolean = true,
    var exportFails: Boolean = false,
) {
    var record = LegacyCacheMigrationRecord()
    var exports = 0
    var discards = 0
    var legacyDeletions = 0
    var recordDuringExport: LegacyCacheMigrationRecord? = null

    fun launch(): LegacyMigrationOutcome {
        val outcome = runLegacyCacheMigration(
            record = record,
            legacyFileExists = legacyExists,
            encryptedFileExists = encryptedExists,
            encryptedCacheIsDiscardable = { encryptedDiscardable },
            discardEncryptedCache = {
                discards++
                encryptedExists = false
            },
            export = {
                exports++
                recordDuringExport = record
                if (exportFails) {
                    // The production lambda clears its own half-written target before rethrowing.
                    encryptedExists = false
                    throw IllegalStateException("no space left on device")
                }
                encryptedExists = true
            },
            deleteLegacyFile = {
                legacyDeletions++
                legacyExists = false
            },
            persist = { record = it },
        )
        if (outcome !is LegacyMigrationOutcome.Migrated) roomOpensDatabase()
        return outcome
    }

    /** Room creating the encrypted cache after a launch that did not migrate. */
    private fun roomOpensDatabase() {
        encryptedExists = true
    }
}

class LegacyCacheMigrationStateTest {

    @Test
    fun `fresh install has nothing to migrate and never touches the state`() {
        val harness = MigrationHarness(legacyExists = false)

        assertEquals(LegacyMigrationOutcome.Skipped, harness.launch())
        assertEquals(LegacyMigrationOutcome.Skipped, harness.launch())

        assertEquals(0, harness.exports)
        assertEquals(LegacyCacheMigrationRecord(), harness.record)
    }

    @Test
    fun `legacy file present migrates once and then never again`() {
        val harness = MigrationHarness()

        assertEquals(LegacyMigrationOutcome.Migrated, harness.launch())
        assertEquals(
            LegacyCacheMigrationRecord(LegacyCacheMigrationStatus.COMPLETED, attempts = 1),
            harness.record,
        )
        assertEquals(1, harness.legacyDeletions)

        repeat(3) { assertEquals(LegacyMigrationOutcome.Skipped, harness.launch()) }
        assertEquals(1, harness.exports)
    }

    @Test
    fun `the attempt is recorded before the export runs, so a kill mid-export still counts`() {
        val harness = MigrationHarness()

        harness.launch()

        assertEquals(
            LegacyCacheMigrationRecord(LegacyCacheMigrationStatus.FAILED, attempts = 1),
            harness.recordDuringExport,
        )
    }

    @Test
    fun `a failed export keeps the plaintext file and retries on the next launch`() {
        val harness = MigrationHarness(exportFails = true)

        val first = harness.launch()
        assertTrue(first is LegacyMigrationOutcome.Failed)
        assertFalse((first as LegacyMigrationOutcome.Failed).givenUp)
        assertEquals(0, harness.legacyDeletions)
        assertTrue(harness.legacyExists)
        // Room has since built the encrypted cache; the old existence check would stop here.
        assertTrue(harness.encryptedExists)

        harness.exportFails = false
        assertEquals(LegacyMigrationOutcome.Migrated, harness.launch())
        assertEquals(1, harness.discards)
        assertEquals(2, harness.exports)
        assertFalse(harness.legacyExists)
    }

    @Test
    fun `a retry waits when the encrypted cache has unsynced mutations of its own`() {
        val harness = MigrationHarness(exportFails = true)
        harness.launch()

        harness.exportFails = false
        harness.encryptedDiscardable = false

        assertEquals(LegacyMigrationOutcome.Deferred, harness.launch())
        assertEquals(0, harness.discards)
        assertEquals(1, harness.exports)
        // Neither copy of the user's unsynced work was touched.
        assertTrue(harness.legacyExists)
        assertTrue(harness.encryptedExists)
        assertEquals(1, harness.record.attempts)

        // Once the queue drains the retry proceeds.
        harness.encryptedDiscardable = true
        assertEquals(LegacyMigrationOutcome.Migrated, harness.launch())
    }

    @Test
    fun `retrying stops after the attempt bound and the plaintext file is never deleted`() {
        val harness = MigrationHarness(exportFails = true)

        repeat(MAX_LEGACY_MIGRATION_ATTEMPTS - 1) {
            val outcome = harness.launch()
            assertFalse((outcome as LegacyMigrationOutcome.Failed).givenUp)
        }

        val last = harness.launch() as LegacyMigrationOutcome.Failed
        assertTrue(last.givenUp)
        assertEquals(MAX_LEGACY_MIGRATION_ATTEMPTS, harness.exports)

        repeat(5) { assertEquals(LegacyMigrationOutcome.Skipped, harness.launch()) }
        assertEquals(MAX_LEGACY_MIGRATION_ATTEMPTS, harness.exports)
        assertEquals(0, harness.legacyDeletions)
        // The file survives so it can still be surfaced to the user and recovered by hand.
        assertTrue(harness.legacyExists)
    }

    @Test
    fun `an export that succeeds on the final allowed attempt still migrates`() {
        val harness = MigrationHarness(exportFails = true)
        repeat(MAX_LEGACY_MIGRATION_ATTEMPTS - 1) { harness.launch() }

        harness.exportFails = false

        assertEquals(LegacyMigrationOutcome.Migrated, harness.launch())
        assertEquals(
            LegacyCacheMigrationRecord(
                LegacyCacheMigrationStatus.COMPLETED,
                attempts = MAX_LEGACY_MIGRATION_ATTEMPTS,
            ),
            harness.record,
        )
    }

    @Test
    fun `a plaintext file left behind by a completed migration is removed on the next launch`() {
        val harness = MigrationHarness(encryptedExists = true)
        harness.record = LegacyCacheMigrationRecord(LegacyCacheMigrationStatus.COMPLETED, attempts = 1)

        assertEquals(LegacyMigrationOutcome.LegacyRemoved, harness.launch())
        assertEquals(1, harness.legacyDeletions)
        // Never re-exported into the live database.
        assertEquals(0, harness.exports)
        assertEquals(LegacyMigrationOutcome.Skipped, harness.launch())
    }

    @Test
    fun `a build that skipped on file existence alone is recovered`() {
        // NOT_STARTED with both files present: the previous release saw the encrypted file and
        // returned early, stranding the plaintext one.
        val harness = MigrationHarness(encryptedExists = true)

        assertEquals(LegacyMigrationOutcome.Migrated, harness.launch())
        assertEquals(1, harness.discards)
        assertFalse(harness.legacyExists)
    }

    @Test
    fun `deciding never exports over a live encrypted cache that could hold unsynced work`() {
        val action = decideLegacyMigration(
            record = LegacyCacheMigrationRecord(LegacyCacheMigrationStatus.FAILED, attempts = 1),
            legacyFileExists = true,
            encryptedFileExists = true,
            encryptedCacheIsDiscardable = { false },
        )

        assertEquals(LegacyMigrationAction.DEFER, action)
    }

    @Test
    fun `a local-mode cache with tasks in it is never discardable, however empty its queue is`() {
        // Local mode empties pending_mutations on every sync (SyncManager.syncCachedData), so an
        // empty queue there means "no server to sync to", not "everything is safely on a server".
        // These rows exist nowhere else in the world.
        val localCacheWithTasks = EncryptedCacheContents(pendingMutations = 0, userRows = 42)

        assertFalse(canDiscardEncryptedCache(serverBacked = false, contents = localCacheWithTasks))
        // The same cache in server mode is re-fetchable, so the retry may proceed.
        assertTrue(canDiscardEncryptedCache(serverBacked = true, contents = localCacheWithTasks))
    }

    @Test
    fun `an empty cache is discardable in either mode and an unreadable one in neither`() {
        val empty = EncryptedCacheContents(pendingMutations = 0, userRows = 0)
        assertTrue(canDiscardEncryptedCache(serverBacked = false, contents = empty))
        assertTrue(canDiscardEncryptedCache(serverBacked = true, contents = empty))

        // Unsynced work is never re-creatable, whoever is backing the data.
        val queued = EncryptedCacheContents(pendingMutations = 1, userRows = 0)
        assertFalse(canDiscardEncryptedCache(serverBacked = true, contents = queued))

        // Could not be inspected: not evidence of emptiness, so not a licence to delete.
        assertFalse(canDiscardEncryptedCache(serverBacked = true, contents = null))
        assertFalse(canDiscardEncryptedCache(serverBacked = false, contents = null))
    }

    @Test
    fun `a local-mode user keeps the tasks they entered after a failed export`() {
        val harness = MigrationHarness(exportFails = true)
        harness.launch()

        // Room built an empty encrypted cache; the user, staring at an empty app, re-entered work.
        // Local mode drains the mutation queue, so only the content rows show it is not disposable.
        harness.encryptedDiscardable = canDiscardEncryptedCache(
            serverBacked = false,
            contents = EncryptedCacheContents(pendingMutations = 0, userRows = 7),
        )
        harness.exportFails = false

        assertEquals(LegacyMigrationOutcome.Deferred, harness.launch())
        assertEquals(0, harness.discards)
        assertTrue(harness.legacyExists)
        assertTrue(harness.encryptedExists)
    }

    @Test
    fun `a local-mode retry still runs while the encrypted cache is untouched`() {
        val harness = MigrationHarness(exportFails = true)
        harness.launch()

        // Nothing was entered after the failure, so the fresh cache Room made is provably empty.
        harness.encryptedDiscardable = canDiscardEncryptedCache(
            serverBacked = false,
            contents = EncryptedCacheContents(pendingMutations = 0, userRows = 0),
        )
        harness.exportFails = false

        assertEquals(LegacyMigrationOutcome.Migrated, harness.launch())
        assertEquals(1, harness.discards)
        assertFalse(harness.legacyExists)
    }

    @Test
    fun `giving up is a function of the attempt count, not of the status word alone`() {
        assertFalse(LegacyCacheMigrationRecord(LegacyCacheMigrationStatus.FAILED, 1).isGivenUp())
        assertTrue(
            LegacyCacheMigrationRecord(
                LegacyCacheMigrationStatus.FAILED,
                MAX_LEGACY_MIGRATION_ATTEMPTS,
            ).isGivenUp(),
        )
        // A completed migration is done regardless of how many attempts it took.
        assertFalse(
            LegacyCacheMigrationRecord(
                LegacyCacheMigrationStatus.COMPLETED,
                MAX_LEGACY_MIGRATION_ATTEMPTS,
            ).isGivenUp(),
        )
    }
}
