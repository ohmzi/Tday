package com.ohmz.tday.compose.core.data.cache

import android.util.Log
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.data.db.SyncMetadataEntity
import com.ohmz.tday.compose.core.data.db.TdayDatabase
import com.ohmz.tday.compose.core.data.db.toEntity
import com.ohmz.tday.compose.core.data.db.toRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.CookieManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCacheManager @Inject constructor(
    private val database: TdayDatabase,
    private val secureConfigStore: SecureConfigStore,
    private val json: Json,
    private val themePreferenceStore: ThemePreferenceStore,
    private val cookieManager: CookieManager,
) {
    private val todoDao = database.todoDao()
    private val listDao = database.listDao()
    private val completedDao = database.completedDao()
    private val mutationDao = database.mutationDao()
    private val syncMetadataDao = database.syncMetadataDao()

    private val syncMutex = Mutex()
    private val cacheDataVersionMutable = MutableStateFlow(0L)
    val cacheDataVersion: StateFlow<Long> = cacheDataVersionMutable.asStateFlow()

    @Volatile
    private var lastPersistedState: OfflineSyncState? = null

    @Volatile
    private var migrated = false
    private val migrationLock = Any()

    private fun ensureMigrated() {
        if (migrated) return
        synchronized(migrationLock) {
            if (migrated) return
            migrateFromSharedPrefsIfNeeded()
            migrated = true
        }
    }

    /**
     * One-time migration: reads the legacy JSON blob from EncryptedSharedPreferences,
     * inserts all records into Room, then deletes the prefs key.
     */
    private fun migrateFromSharedPrefsIfNeeded() {
        val raw = secureConfigStore.getOfflineSyncStateRaw()
        if (raw.isNullOrBlank()) return

        val state = try {
            json.decodeFromString<OfflineSyncState>(raw)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }

        persistStateToDaos(state)
        secureConfigStore.clearOfflineSyncState()
        Log.i(LOG_TAG, "Migrated offline cache from SharedPreferences to Room")
    }

    fun loadOfflineState(): OfflineSyncState {
        ensureMigrated()
        val todos = todoDao.getAll().map { it.toRecord() }
        val lists = listDao.getAll().map { it.toRecord() }
        val completed = completedDao.getAll().map { it.toRecord() }
        val mutations = mutationDao.getAll().map { it.toRecord() }
        val metadata = syncMetadataDao.get()

        val state = OfflineSyncState(
            lastSuccessfulSyncEpochMs = metadata?.lastSuccessfulSyncEpochMs ?: 0L,
            lastSyncAttemptEpochMs = metadata?.lastSyncAttemptEpochMs ?: 0L,
            todos = todos,
            completedItems = completed,
            lists = lists,
            pendingMutations = mutations,
            aiSummaryEnabled = metadata?.aiSummaryEnabled ?: true,
        )
        lastPersistedState = state
        return state
    }

    fun saveOfflineState(state: OfflineSyncState) {
        ensureMigrated()
        val previous = lastPersistedState ?: loadOfflineState()
        if (previous == state) return

        persistStateToDaos(state)
        lastPersistedState = state
        if (hasUiDataChanges(previous, state)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
        }
    }

    fun updateOfflineState(transform: (OfflineSyncState) -> OfflineSyncState): OfflineSyncState {
        val next = transform(loadOfflineState())
        saveOfflineState(next)
        return next
    }

    fun hasCachedData(): Boolean {
        ensureMigrated()
        if (todoDao.count() > 0) return true
        if (listDao.count() > 0) return true
        if (completedDao.count() > 0) return true
        return mutationDao.count() > 0
    }

    fun clearAllLocalData() {
        val previous = lastPersistedState ?: loadOfflineState()
        val cleared = OfflineSyncState()

        database.clearAllTables()
        secureConfigStore.clearAllLocalData()
        themePreferenceStore.clear()
        runCatching { cookieManager.cookieStore?.removeAll() }

        lastPersistedState = cleared
        if (hasUiDataChanges(previous, cleared)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
        }
    }

    fun clearSessionOnly() {
        val previous = lastPersistedState ?: loadOfflineState()
        val cleared = OfflineSyncState()

        runCatching { cookieManager.cookieStore?.removeAll() }
        database.clearAllTables()
        secureConfigStore.clearOfflineSyncState()

        lastPersistedState = cleared
        if (hasUiDataChanges(previous, cleared)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
        }
    }

    suspend fun <T> withSyncLock(block: suspend () -> T): T {
        return syncMutex.withLock { block() }
    }

    private fun persistStateToDaos(state: OfflineSyncState) {
        database.runInTransaction {
            todoDao.deleteAll()
            todoDao.insertAll(state.todos.map { it.toEntity() })
            listDao.deleteAll()
            listDao.insertAll(state.lists.map { it.toEntity() })
            completedDao.deleteAll()
            completedDao.insertAll(state.completedItems.map { it.toEntity() })
            mutationDao.deleteAll()
            mutationDao.insertAll(state.pendingMutations.map { it.toEntity() })
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    lastSuccessfulSyncEpochMs = state.lastSuccessfulSyncEpochMs,
                    lastSyncAttemptEpochMs = state.lastSyncAttemptEpochMs,
                    aiSummaryEnabled = state.aiSummaryEnabled,
                ),
            )
        }
    }

    private fun hasUiDataChanges(
        previous: OfflineSyncState,
        next: OfflineSyncState,
    ): Boolean {
        return previous.todos != next.todos ||
            previous.completedItems != next.completedItems ||
            previous.lists != next.lists ||
            previous.aiSummaryEnabled != next.aiSummaryEnabled
    }

    private companion object {
        const val LOG_TAG = "OfflineCacheManager"
    }
}
