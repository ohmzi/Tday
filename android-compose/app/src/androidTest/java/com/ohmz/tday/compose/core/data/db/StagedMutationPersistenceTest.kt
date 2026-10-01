package com.ohmz.tday.compose.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A staged mutation (a delayed-commit complete/delete parked for the Undo window) is a database
 * fact, not an in-memory one: SyncManager starts every pass from `loadOfflineStateBlocking()`, a
 * Room read, and skips only the mutations that come back `staged`. These tests run against real
 * Room databases (framework SQLite, not the production SQLCipher helper) because the bug lived in
 * the gap between the record and the row, which no JVM test of either half could see.
 */
@RunWith(AndroidJUnit4::class)
class StagedMutationPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Nullable backing field + non-null accessor instead of `lateinit`: @Before opens it before any
    // test body runs, but a missed setup then fails with this message and not an opaque
    // UninitializedPropertyAccessException.
    private var memoryOrNull: TdayDatabase? = null
    private val memory: TdayDatabase
        get() = checkNotNull(memoryOrNull) { "openInMemory() must run before memory is used" }

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TdayDatabase::class.java,
    )

    @Before
    fun openInMemory() {
        memoryOrNull = Room.inMemoryDatabaseBuilder(context, TdayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        context.deleteDatabase(RELEASE_DB)
        context.deleteDatabase(MIGRATION_DB)
    }

    @After
    fun cleanUp() {
        memoryOrNull?.close()
        context.deleteDatabase(RELEASE_DB)
        context.deleteDatabase(MIGRATION_DB)
    }

    @Test
    fun a_staged_mutation_reads_back_staged_through_the_cache_managers_persistence_path() {
        val staged = stagedCompletion()
        val ordinary = ordinaryDelete()

        // OfflineCacheManager.persistStateToDaos: one transaction, the whole queue replaced.
        memory.runInTransaction {
            memory.mutationDao().deleteAll()
            memory.mutationDao().insertAll(listOf(staged, ordinary).map { it.toEntity() })
        }

        // OfflineCacheManager.loadOfflineStateBlocking: `mutationDao.getAll().map { toRecord() }`,
        // which is the queue SyncManager walks.
        val restored = memory.mutationDao().getAll().map { it.toRecord() }

        assertEquals(listOf(staged, ordinary), restored)
        assertEquals(true, restored.first { it.mutationId == staged.mutationId }.staged)
        assertEquals(false, restored.first { it.mutationId == ordinary.mutationId }.staged)
    }

    @Test
    fun opening_the_database_releases_a_staged_marker_a_dead_process_left_behind() {
        val staged = stagedCompletion().copy(title = "Pay rent", rrule = "FREQ=MONTHLY")
        val ordinary = ordinaryDelete()

        // The process that staged the marker is killed inside the Undo window: its row is on disk
        // with the flag set, and the commit or Undo that would have cleared it never runs.
        openFile(releaseOnOpen = false).closing { killed ->
            killed.mutationDao().insertAll(listOf(staged, ordinary).map { it.toEntity() })
        }
        // Control: with nothing to release it, the marker survives a reopen. This is what the
        // callback exists to prevent, and what makes the assertion below mean something.
        openFile(releaseOnOpen = false).closing { plain ->
            assertEquals(true, plain.mutationDao().getAll().first { it.mutationId == "m-staged" }.staged)
        }

        openFile(releaseOnOpen = true).closing { relaunched ->
            val restored = relaunched.mutationDao().getAll().map { it.toRecord() }

            // Released, so SyncManager replays it and the user's complete is honoured; every other
            // field of the mutation, and every other mutation, is untouched.
            assertEquals(listOf(staged.copy(staged = false), ordinary), restored)

            // Once per open, not once per query: a marker the live process stages afterwards is
            // its own Undo window and must still be there.
            val liveMarker = stagedCompletion().copy(mutationId = "m-live", timestampEpochMs = 3_000L)
            relaunched.mutationDao().insertAll(listOf(liveMarker.toEntity()))
            val live = relaunched.mutationDao().getAll().first { it.mutationId == "m-live" }
            assertEquals(true, live.staged)
        }
    }

    @Test
    fun the_production_sqlcipher_helper_runs_the_release_on_open_too() {
        val staged = stagedCompletion()

        openFile(releaseOnOpen = false, encrypted = true).closing { killed ->
            killed.mutationDao().insertAll(listOf(staged.toEntity()))
        }

        openFile(releaseOnOpen = true, encrypted = true).closing { relaunched ->
            assertEquals(
                listOf(staged.copy(staged = false)),
                relaunched.mutationDao().getAll().map { it.toRecord() },
            )
        }
    }

    @Test
    fun migrating_from_13_keeps_the_queue_and_reads_it_back_unstaged() {
        migrationHelper.createDatabase(MIGRATION_DB, 13).use { v13 ->
            v13.execSQL(
                "INSERT INTO pending_mutations (mutationId, kind, targetId, timestampEpochMs, title, " +
                    "completed, reusable, defaultPriority, defaultPriorityChanged) " +
                    "VALUES ('m-old', 'UPDATE_FLOATER_LIST', 'fl-1', 1000, 'Packing', 0, 1, 'High', 1)",
            )
        }

        migrationHelper.runMigrationsAndValidate(MIGRATION_DB, 14, true, Migration13To14()).use { v14 ->
            v14.query("SELECT staged FROM pending_mutations WHERE mutationId = 'm-old'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                // A pre-v14 row has no value, which the mapper must read as "not staged".
                assertTrue(cursor.isNull(0))
            }
        }

        // And through the real Room mapping: the migrated file opens under the v14 schema's
        // identity hash, and the surviving row comes back whole.
        val migrated = Room.databaseBuilder(context, TdayDatabase::class.java, MIGRATION_DB)
            .addMigrations(Migration13To14())
            .allowMainThreadQueries()
            .build()
        migrated.closing { database ->
            val restored = database.mutationDao().getAll().map { it.toRecord() }

            assertEquals(
                listOf(
                    PendingMutationRecord(
                        mutationId = "m-old",
                        kind = MutationKind.UPDATE_FLOATER_LIST,
                        targetId = "fl-1",
                        timestampEpochMs = 1_000L,
                        title = "Packing",
                        completed = false,
                        reusable = true,
                        defaultPriority = "High",
                        defaultPriorityChanged = true,
                        staged = false,
                    ),
                ),
                restored,
            )
        }
    }

    // File-backed so a close/open cycle stands in for a process restart. Framework SQLite unless
    // [encrypted], which swaps in the SQLCipher helper DatabaseModule installs in production.
    private fun openFile(releaseOnOpen: Boolean, encrypted: Boolean = false): TdayDatabase =
        Room.databaseBuilder(context, TdayDatabase::class.java, RELEASE_DB)
            .allowMainThreadQueries()
            .apply {
                if (encrypted) {
                    System.loadLibrary("sqlcipher")
                    // SQLCipher zeroes the array it is given, hence a fresh one per open.
                    openHelperFactory(SupportOpenHelperFactory("itest-passphrase".toByteArray()))
                }
                if (releaseOnOpen) addCallback(ReleaseStagedMutationsOnOpen())
            }
            .build()

    private fun stagedCompletion() = PendingMutationRecord(
        mutationId = "m-staged",
        kind = MutationKind.COMPLETE_TODO,
        targetId = "todo-1",
        timestampEpochMs = 1_000L,
        completed = true,
        staged = true,
    )

    private fun ordinaryDelete() = PendingMutationRecord(
        mutationId = "m-plain",
        kind = MutationKind.DELETE_LIST,
        targetId = "list-1",
        timestampEpochMs = 2_000L,
    )

    // RoomDatabase is not Closeable in this Room version, so `use` is not available.
    private inline fun <R> TdayDatabase.closing(block: (TdayDatabase) -> R): R =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        const val RELEASE_DB = "staged-release-on-open-test.db"
        const val MIGRATION_DB = "staged-migration-test.db"
    }
}
