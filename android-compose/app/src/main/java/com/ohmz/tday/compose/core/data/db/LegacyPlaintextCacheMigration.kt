package com.ohmz.tday.compose.core.data.db

import android.content.Context
import android.util.Log
import com.ohmz.tday.compose.core.data.AppDataMode
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile

/** Filename of the pre-encryption Room database. */
internal const val LEGACY_PLAINTEXT_DB_NAME = "tday_offline_cache.db"

/** Filename of the SQLCipher database that replaces it. */
internal const val ENCRYPTED_DB_NAME = "tday_offline_cache_encrypted.db"

/**
 * How many launches may attempt the export before the app stops trying.
 *
 * Bounded because every attempt costs a slow launch and, on a genuinely unreadable legacy file,
 * would never succeed. Generous because the realistic failure is transient — disk full, the process
 * killed mid-export — and each wasted attempt burns one of the user's chances to get unsynced edits
 * back.
 */
internal const val MAX_LEGACY_MIGRATION_ATTEMPTS = 5

private const val LOG_TAG = "CacheEncryption"

/**
 * Where the one-time migration got to, persisted across launches.
 *
 * File existence is deliberately *not* the source of truth. A failed export leaves the plaintext
 * file in place and Room then creates a fresh encrypted database moments later, so "the encrypted
 * file exists" would say "already migrated" from the next launch onwards — permanently stranding
 * the unsynced mutations in a plaintext file that also never gets removed.
 */
internal enum class LegacyCacheMigrationStatus { NOT_STARTED, FAILED, COMPLETED }

internal data class LegacyCacheMigrationRecord(
    val status: LegacyCacheMigrationStatus = LegacyCacheMigrationStatus.NOT_STARTED,
    val attempts: Int = 0,
)

/** True once the export has failed as often as it is allowed to, so no further attempt will run. */
internal fun LegacyCacheMigrationRecord.isGivenUp(): Boolean =
    status == LegacyCacheMigrationStatus.FAILED && attempts >= MAX_LEGACY_MIGRATION_ATTEMPTS

internal enum class LegacyMigrationAction {
    /** Nothing to migrate, or the app has stopped trying. */
    SKIP,

    /** Migration is done but the plaintext file survived its deletion; remove it now. */
    DELETE_LEGACY_ONLY,

    /** Export straight into a fresh encrypted file. */
    EXPORT,

    /**
     * A previous attempt failed and Room has since built an encrypted cache, but that cache is
     * provably disposable ([canDiscardEncryptedCache]), so it can be dropped to make room for the
     * retry.
     */
    DISCARD_ENCRYPTED_AND_EXPORT,

    /**
     * A retry is due, but the encrypted cache holds work of its own that exists nowhere else.
     * Dropping it would destroy that, and there is no way to merge two caches, so wait for a launch
     * where it has become disposable.
     */
    DEFER,
}

/**
 * What an encrypted cache actually holds, as row counts.
 *
 * `userRows` is every content table added together — the tasks, floaters, lists and completed
 * history. It is counted separately from [pendingMutations] because the two answer different
 * questions: pending mutations are *never* re-creatable, content rows are re-creatable only when a
 * server has a copy of them.
 */
internal data class EncryptedCacheContents(
    val pendingMutations: Int,
    val userRows: Int,
)

/**
 * Whether the encrypted cache may be deleted so a failed export can be retried into a clean file.
 *
 * [serverBacked] is the load-bearing input. In **local mode** there is no server: `SyncManager`
 * deliberately empties `pending_mutations` on every local-mode sync, so an empty queue there means
 * "nothing to sync", not "everything is safely on a server" — the content tables are the only copy
 * of those tasks in existence. Judging discardability on the queue alone would delete a local-mode
 * user's entire task list.
 *
 * So: an empty cache is disposable in any mode, a server-backed cache is disposable once its queue
 * has drained, and anything else is not. A null [contents] means the cache could not be inspected,
 * which is not proof of anything and therefore never a licence to delete.
 */
internal fun canDiscardEncryptedCache(
    serverBacked: Boolean,
    contents: EncryptedCacheContents?,
): Boolean {
    if (contents == null) return false
    if (contents.pendingMutations > 0) return false
    return serverBacked || contents.userRows == 0
}

internal sealed interface LegacyMigrationOutcome {
    data object Skipped : LegacyMigrationOutcome
    data object Deferred : LegacyMigrationOutcome
    data object Migrated : LegacyMigrationOutcome
    data object LegacyRemoved : LegacyMigrationOutcome
    data class Failed(val cause: Exception, val givenUp: Boolean) : LegacyMigrationOutcome
}

/**
 * Decides what this launch should do, from persisted state rather than from which files exist.
 *
 * [encryptedCacheIsDiscardable] is a lambda because answering it means opening the encrypted
 * database, which is only worth doing on the retry path.
 */
internal fun decideLegacyMigration(
    record: LegacyCacheMigrationRecord,
    legacyFileExists: Boolean,
    encryptedFileExists: Boolean,
    encryptedCacheIsDiscardable: () -> Boolean,
): LegacyMigrationAction {
    if (!legacyFileExists) return LegacyMigrationAction.SKIP
    // A completed export makes the plaintext file dead weight; nothing writes to it any more, so
    // removing it is always safe and worth re-trying if the first deletion did not take.
    if (record.status == LegacyCacheMigrationStatus.COMPLETED) {
        return LegacyMigrationAction.DELETE_LEGACY_ONLY
    }
    if (record.isGivenUp()) return LegacyMigrationAction.SKIP

    return when {
        !encryptedFileExists -> LegacyMigrationAction.EXPORT
        encryptedCacheIsDiscardable() -> LegacyMigrationAction.DISCARD_ENCRYPTED_AND_EXPORT
        else -> LegacyMigrationAction.DEFER
    }
}

/**
 * Drives one launch's worth of the migration state machine.
 *
 * Split from the Android plumbing so the ordering guarantees below can be tested directly: the only
 * thing standing between a user and their unsynced offline edits is that [deleteLegacyFile] is
 * unreachable unless [export] returned normally.
 */
internal fun runLegacyCacheMigration(
    record: LegacyCacheMigrationRecord,
    legacyFileExists: Boolean,
    encryptedFileExists: Boolean,
    encryptedCacheIsDiscardable: () -> Boolean,
    discardEncryptedCache: () -> Unit,
    export: () -> Unit,
    deleteLegacyFile: () -> Unit,
    persist: (LegacyCacheMigrationRecord) -> Unit,
): LegacyMigrationOutcome {
    val action = decideLegacyMigration(
        record = record,
        legacyFileExists = legacyFileExists,
        encryptedFileExists = encryptedFileExists,
        encryptedCacheIsDiscardable = encryptedCacheIsDiscardable,
    )
    when (action) {
        LegacyMigrationAction.SKIP -> return LegacyMigrationOutcome.Skipped
        LegacyMigrationAction.DEFER -> return LegacyMigrationOutcome.Deferred
        LegacyMigrationAction.DELETE_LEGACY_ONLY -> {
            deleteLegacyFile()
            return LegacyMigrationOutcome.LegacyRemoved
        }
        LegacyMigrationAction.EXPORT, LegacyMigrationAction.DISCARD_ENCRYPTED_AND_EXPORT -> Unit
    }

    // Count the attempt *before* running it, and persist it as a failure up front: a process killed
    // mid-export never reaches the catch, and an uncounted attempt would let a reproducible crash
    // retry itself on every launch forever.
    val attempted = LegacyCacheMigrationRecord(
        status = LegacyCacheMigrationStatus.FAILED,
        attempts = record.attempts + 1,
    )
    persist(attempted)

    return try {
        if (action == LegacyMigrationAction.DISCARD_ENCRYPTED_AND_EXPORT) discardEncryptedCache()
        export()
        // Reached only on a clean export, which is the whole safety argument: from here the rows —
        // including the pending mutations the server has never seen — exist in the encrypted file.
        persist(LegacyCacheMigrationRecord(LegacyCacheMigrationStatus.COMPLETED, attempted.attempts))
        deleteLegacyFile()
        LegacyMigrationOutcome.Migrated
    } catch (failure: Exception) {
        // The record stays FAILED with the attempt counted, and the plaintext file is untouched.
        LegacyMigrationOutcome.Failed(cause = failure, givenUp = attempted.isGivenUp())
    }
}

/**
 * Persists [LegacyCacheMigrationRecord] across launches.
 *
 * Plain prefs rather than EncryptedSharedPreferences, matching AppSecurityPreferenceStore: a status
 * word and an attempt count are not secrets, and this is read on the DI thread during the very
 * first database injection where a Keystore round trip would show up as launch latency.
 */
internal class LegacyCacheMigrationStateStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun read(): LegacyCacheMigrationRecord {
        val stored = preferences.getString(KEY_STATUS, null)
        val status = LegacyCacheMigrationStatus.values().firstOrNull { it.name == stored }
            ?: LegacyCacheMigrationStatus.NOT_STARTED
        return LegacyCacheMigrationRecord(status, preferences.getInt(KEY_ATTEMPTS, 0))
    }

    fun write(record: LegacyCacheMigrationRecord) {
        // commit(), not apply(): the attempt counter has to be on disk before the export starts, or
        // a process killed mid-export comes back with the attempt uncounted.
        preferences.edit()
            .putString(KEY_STATUS, record.status.name)
            .putInt(KEY_ATTEMPTS, record.attempts)
            .commit()
    }

    private companion object {
        const val PREF_NAME = "tday_cache_migration_state"
        const val KEY_STATUS = "legacy_plaintext_migration_status"
        const val KEY_ATTEMPTS = "legacy_plaintext_migration_attempts"
    }
}

/**
 * True when the pre-encryption plaintext cache is still sitting on this device.
 *
 * Deliberately a file check rather than a status read: whatever the state machine believes, a
 * plaintext file on disk is readable by anyone who images the device, and it may hold offline edits
 * the server has never seen. The user has to be able to find that out, so Settings renders a warning
 * from this — a `Log.e` no one reads is not a way to tell someone their data is exposed.
 */
fun hasUnmigratedPlaintextCache(context: Context): Boolean =
    context.getDatabasePath(LEGACY_PLAINTEXT_DB_NAME).exists()

/**
 * Moves the legacy plaintext offline cache into the SQLCipher database, once.
 *
 * The cache is **not** disposable: alongside re-fetchable task data it stores pending mutations
 * that have not reached the server yet, so dropping it and re-syncing would silently destroy edits
 * the user made offline. So this copies rather than discards, using SQLCipher's `sqlcipher_export`
 * to stream every table into a new encrypted file, then carries `user_version` across by hand
 * (`sqlcipher_export` does not copy it, and Room reads it to decide whether a migration is due —
 * without it Room would treat a fully-populated database as brand new).
 *
 * The plaintext file is deleted only after a clean export, and never on any path that failed. A
 * failure leaves it exactly as it was, removes the partial encrypted file, and records the attempt
 * so a later launch retries — see [runLegacyCacheMigration] for the ordering and
 * [MAX_LEGACY_MIGRATION_ATTEMPTS] for where retrying stops.
 *
 * Returns true when a migration ran and succeeded.
 */
internal fun migrateLegacyPlaintextCacheIfNeeded(
    context: Context,
    passphraseStore: DatabasePassphraseStore,
    dataMode: AppDataMode,
): Boolean {
    val legacyFile = context.getDatabasePath(LEGACY_PLAINTEXT_DB_NAME)
    val encryptedFile = context.getDatabasePath(ENCRYPTED_DB_NAME)
    val stateStore = LegacyCacheMigrationStateStore(context)

    val outcome = runLegacyCacheMigration(
        record = stateStore.read(),
        legacyFileExists = legacyFile.exists(),
        encryptedFileExists = encryptedFile.exists(),
        encryptedCacheIsDiscardable = {
            canDiscardEncryptedCache(
                // Only an explicit SERVER mode is treated as "a copy exists elsewhere". UNSET is
                // an unfinished setup, not a promise of a server, so it gets the strict rule too.
                serverBacked = dataMode == AppDataMode.SERVER,
                contents = readEncryptedCacheContents(
                    encryptedFile,
                    passphraseStore.getOrCreatePassphrase(),
                ),
            )
        },
        discardEncryptedCache = { encryptedFile.deleteWithSidecars() },
        export = {
            Log.i(LOG_TAG, "migrating legacy plaintext offline cache into SQLCipher")
            try {
                exportPlaintextInto(legacyFile, encryptedFile, passphraseStore.getOrCreatePassphrase())
            } catch (failure: Exception) {
                // Clear the half-written target so the next launch sees a clean slate. The source
                // is not touched here, by design.
                encryptedFile.deleteWithSidecars()
                throw failure
            }
        },
        deleteLegacyFile = { legacyFile.shredWithSidecars() },
        persist = stateStore::write,
    )

    when (outcome) {
        is LegacyMigrationOutcome.Migrated ->
            Log.i(LOG_TAG, "legacy plaintext offline cache migrated and removed")
        is LegacyMigrationOutcome.LegacyRemoved ->
            Log.i(LOG_TAG, "removed leftover plaintext cache from an earlier completed migration")
        is LegacyMigrationOutcome.Deferred ->
            Log.w(LOG_TAG, "legacy cache retry postponed; encrypted cache still has unsynced mutations")
        is LegacyMigrationOutcome.Failed -> Log.e(
            LOG_TAG,
            if (outcome.givenUp) {
                "legacy cache migration abandoned after $MAX_LEGACY_MIGRATION_ATTEMPTS attempts; " +
                    "plaintext file kept and surfaced in Settings"
            } else {
                "legacy cache migration failed; keeping plaintext file for a later retry"
            },
            outcome.cause,
        )
        is LegacyMigrationOutcome.Skipped -> Unit
    }

    return outcome is LegacyMigrationOutcome.Migrated
}

/**
 * Counts what the encrypted cache holds, or null when that cannot be established.
 *
 * Null on any failure — the file will not open, a table is missing, the query throws — because the
 * caller deletes the file on the strength of this answer, and "I could not look" is not evidence
 * that there is nothing there.
 */
private fun readEncryptedCacheContents(
    encryptedFile: File,
    passphrase: ByteArray,
): EncryptedCacheContents? {
    val key = String(passphrase, Charsets.UTF_8)
    passphrase.fill(0)

    return try {
        // OPEN_READWRITE, not READONLY: a database left with a hot `-wal` needs recovery on open,
        // which a read-only handle cannot perform. Only a SELECT runs against it.
        val database = SQLiteDatabase.openDatabase(
            encryptedFile.absolutePath,
            key,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
        )
        try {
            // One statement so a missing table takes the whole answer to null rather than letting
            // a partial count masquerade as an empty cache.
            database.rawQuery(CONTENTS_QUERY, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    EncryptedCacheContents(
                        pendingMutations = cursor.getInt(0),
                        userRows = cursor.getInt(1),
                    )
                } else {
                    null
                }
            }
        } finally {
            database.close()
        }
    } catch (failure: Exception) {
        Log.w(LOG_TAG, "cannot establish what the encrypted cache holds", failure)
        null
    }
}

private const val CONTENTS_QUERY = """
    SELECT
        (SELECT COUNT(*) FROM pending_mutations),
        (SELECT COUNT(*) FROM cached_todos) +
        (SELECT COUNT(*) FROM cached_floaters) +
        (SELECT COUNT(*) FROM cached_lists) +
        (SELECT COUNT(*) FROM cached_floater_lists) +
        (SELECT COUNT(*) FROM cached_completed) +
        (SELECT COUNT(*) FROM cached_completed_floaters)
"""

/**
 * Runs the ATTACH/`sqlcipher_export` recipe. The source is opened with an empty key, which makes
 * SQLCipher skip the cipher layer entirely and read the file as plain SQLite.
 */
private fun exportPlaintextInto(legacyFile: File, encryptedFile: File, passphrase: ByteArray) {
    val plaintext = SQLiteDatabase.openDatabase(
        legacyFile.absolutePath,
        "",
        null,
        SQLiteDatabase.OPEN_READWRITE,
        null,
    )

    try {
        val schemaVersion = plaintext.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

        val key = String(passphrase, Charsets.UTF_8)
        // ATTACH ... KEY is written as a literal because that is the form SQLCipher documents and
        // supports; the safety comes from the key alphabet instead. encodePassphrase emits hex
        // only, so there is no quote to break out with — asserted rather than assumed, since the
        // whole migration aborts (keeping the plaintext file) if this ever stops holding.
        require(isHexPassphrase(key)) { "database passphrase must be hex" }
        // Same reasoning for the path: it is app-private and package-derived, but doubling any
        // quote keeps the statement well-formed regardless.
        val escapedPath = encryptedFile.absolutePath.replace("'", "''")

        plaintext.execSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY '$key'")
        try {
            plaintext.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            // PRAGMA will not take a bound parameter; schemaVersion is an Int read back from
            // SQLite, so there is no string to inject.
            plaintext.execSQL("PRAGMA encrypted.user_version = $schemaVersion")
        } finally {
            plaintext.execSQL("DETACH DATABASE encrypted")
        }
    } finally {
        plaintext.close()
        passphrase.fill(0)
    }
}

/** Removes a database file together with its `-wal` / `-shm` / `-journal` companions. */
private fun File.deleteWithSidecars() {
    sidecars().forEach { it.delete() }
}

/**
 * Overwrites a database file and its companions before unlinking them.
 *
 * Only used on the plaintext cache, and only after a completed export. `delete()` alone just drops
 * the directory entry: the task titles and notes stay recoverable in the freed blocks by anyone who
 * images the partition, which is exactly the attacker this encryption work exists to stop. A single
 * overwrite pass is not a guarantee on wear-levelled flash, but it removes the trivial recovery,
 * and it is best-effort so a failure here can never block the deletion itself.
 */
private fun File.shredWithSidecars() {
    sidecars().forEach { file ->
        runCatching {
            if (file.exists() && file.length() > 0) {
                RandomAccessFile(file, "rw").use { handle ->
                    val blank = ByteArray(OVERWRITE_CHUNK_BYTES)
                    var remaining = handle.length()
                    while (remaining > 0) {
                        val chunk = minOf(remaining, blank.size.toLong()).toInt()
                        handle.write(blank, 0, chunk)
                        remaining -= chunk
                    }
                    handle.fd.sync()
                }
            }
        }
        file.delete()
    }
}

private fun File.sidecars(): List<File> = listOf(
    this,
    File("$absolutePath-wal"),
    File("$absolutePath-shm"),
    File("$absolutePath-journal"),
)

private const val OVERWRITE_CHUNK_BYTES = 64 * 1024
