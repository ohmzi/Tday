package com.ohmz.tday.compose.core.data.cache

import android.util.Log
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import com.ohmz.tday.compose.core.data.db.SyncMetadataEntity
import com.ohmz.tday.compose.core.data.db.TdayDatabase
import com.ohmz.tday.compose.core.data.db.toEntity
import com.ohmz.tday.compose.core.data.db.toRecord
import com.ohmz.tday.compose.feature.widget.FloaterTasksWidgetRefresher
import com.ohmz.tday.compose.feature.widget.TodayTasksWidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
    private val todayTasksWidgetRefresher: TodayTasksWidgetRefresher,
    private val floaterTasksWidgetRefresher: FloaterTasksWidgetRefresher,
) {
    private val todoDao = database.todoDao()
    private val floaterDao = database.floaterDao()
    private val listDao = database.listDao()
    private val floaterListDao = database.floaterListDao()
    private val completedDao = database.completedDao()
    private val completedFloaterDao = database.completedFloaterDao()
    private val mutationDao = database.mutationDao()
    private val syncMetadataDao = database.syncMetadataDao()

    private val syncMutex = Mutex()
    private val cacheDataVersionMutable = MutableStateFlow(0L)
    val cacheDataVersion: StateFlow<Long> = cacheDataVersionMutable.asStateFlow()
    private val syncMetadataVersionMutable = MutableStateFlow(0L)
    val syncMetadataVersion: StateFlow<Long> = syncMetadataVersionMutable.asStateFlow()

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

    /**
     * Suspend entry point for reading the offline cache. Hops to [Dispatchers.IO]
     * so callers on the main dispatcher (UI / ViewModels) never block on Room.
     * Contexts that genuinely cannot suspend (Glance widgets, the deliberately
     * synchronous *Snapshot helpers) must call [loadOfflineStateBlocking] directly.
     */
    suspend fun loadOfflineState(): OfflineSyncState =
        withContext(Dispatchers.IO) { loadOfflineStateBlocking() }

    fun loadOfflineStateBlocking(): OfflineSyncState {
        ensureMigrated()
        val todos = todoDao.getAll().map { it.toRecord() }
        val floaters = floaterDao.getAll().map { it.toRecord() }
        val lists = listDao.getAll().map { it.toRecord() }
        val floaterLists = floaterListDao.getAll().map { it.toRecord() }
        val completed = completedDao.getAll().map { it.toRecord() }
        val completedFloaters = completedFloaterDao.getAll().map { it.toRecord() }
        val mutations = mutationDao.getAll().map { it.toRecord() }
        val metadata = syncMetadataDao.get()

        val state = OfflineSyncState(
            lastSuccessfulSyncEpochMs = metadata?.lastSuccessfulSyncEpochMs ?: 0L,
            lastSyncAttemptEpochMs = metadata?.lastSyncAttemptEpochMs ?: 0L,
            todos = todos,
            floaters = floaters,
            completedItems = completed,
            completedFloaters = completedFloaters,
            lists = lists,
            floaterLists = floaterLists,
            pendingMutations = mutations,
            aiSummaryEnabled = metadata?.aiSummaryEnabled ?: true,
        )
        lastPersistedState = state
        return state
    }

    suspend fun saveOfflineState(
        state: OfflineSyncState,
        consumedMutationIds: Set<String>? = null,
    ) = withContext(Dispatchers.IO) { saveOfflineStateBlocking(state, consumedMutationIds) }

    /**
     * [consumedMutationIds] opts into concurrent-mutation preservation and is passed ONLY by
     * SyncManager, whose saves are derived from a snapshot loaded before a multi-second network
     * phase: any mutation persisted now but absent from [state] and not in the set was queued by
     * a concurrent writer mid-sync (e.g. a widget check-off) — clobbering it would silently drop
     * that completion, so it is re-attached instead. The set holds the ids the sync legitimately
     * consumed (replayed to the server / dropped as unrecoverable). Transform-based writers
     * (updateOfflineState) leave it null: their removals (e.g. Undo discarding a queued
     * COMPLETE) are intentional and must NOT be resurrected. Writers are serialized
     * (@Synchronized) so the preserve check can't itself race.
     */
    @Synchronized
    fun saveOfflineStateBlocking(
        state: OfflineSyncState,
        consumedMutationIds: Set<String>? = null,
    ) {
        ensureMigrated()
        val previous = lastPersistedState ?: loadOfflineStateBlocking()
        var normalizedState = if (secureConfigStore.isLocalMode()) {
            state.copy(
                lastSuccessfulSyncEpochMs = 0L,
                lastSyncAttemptEpochMs = 0L,
                pendingMutations = emptyList(),
            )
        } else {
            state
        }
        if (consumedMutationIds != null && !secureConfigStore.isLocalMode()) {
            normalizedState = normalizedState.copy(
                pendingMutations = mergeConcurrentlyQueuedMutations(
                    persisted = previous.pendingMutations,
                    next = normalizedState.pendingMutations,
                    consumedMutationIds = consumedMutationIds,
                ),
            )
        }
        if (previous == normalizedState) return

        val hasUiChanges = hasUiDataChanges(previous, normalizedState)
        val hasSyncMetadataChanges = hasSyncMetadataChanges(previous, normalizedState)

        persistStateToDaos(normalizedState)
        lastPersistedState = normalizedState
        if (hasUiChanges) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
            todayTasksWidgetRefresher.requestRefresh()
            floaterTasksWidgetRefresher.requestRefresh()
        }
        if (hasSyncMetadataChanges) {
            syncMetadataVersionMutable.value = syncMetadataVersionMutable.value + 1L
        }
    }

    suspend fun updateOfflineState(
        transform: (OfflineSyncState) -> OfflineSyncState,
    ): OfflineSyncState = withContext(Dispatchers.IO) { updateOfflineStateBlocking(transform) }

    fun updateOfflineStateBlocking(
        transform: (OfflineSyncState) -> OfflineSyncState,
    ): OfflineSyncState {
        val next = transform(loadOfflineStateBlocking())
        saveOfflineStateBlocking(next)
        return next
    }

    fun hasCachedData(): Boolean {
        ensureMigrated()
        if (todoDao.count() > 0) return true
        if (floaterDao.count() > 0) return true
        if (listDao.count() > 0) return true
        if (floaterListDao.count() > 0) return true
        if (completedDao.count() > 0) return true
        if (completedFloaterDao.count() > 0) return true
        return mutationDao.count() > 0
    }

    fun clearAllLocalData() {
        val previous = lastPersistedState ?: loadOfflineStateBlocking()
        val cleared = OfflineSyncState()

        database.clearAllTables()
        secureConfigStore.clearAllLocalData()
        themePreferenceStore.clear()
        runCatching { cookieManager.cookieStore?.removeAll() }

        lastPersistedState = cleared
        if (hasUiDataChanges(previous, cleared)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
            todayTasksWidgetRefresher.requestRefresh()
            floaterTasksWidgetRefresher.requestRefresh()
        }
        if (hasSyncMetadataChanges(previous, cleared)) {
            syncMetadataVersionMutable.value = syncMetadataVersionMutable.value + 1L
        }
    }

    fun clearSessionOnly() {
        val previous = lastPersistedState ?: loadOfflineStateBlocking()
        val cleared = OfflineSyncState()

        runCatching { cookieManager.cookieStore?.removeAll() }
        database.clearAllTables()
        secureConfigStore.clearOfflineSyncState()

        lastPersistedState = cleared
        if (hasUiDataChanges(previous, cleared)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
            todayTasksWidgetRefresher.requestRefresh()
            floaterTasksWidgetRefresher.requestRefresh()
        }
        if (hasSyncMetadataChanges(previous, cleared)) {
            syncMetadataVersionMutable.value = syncMetadataVersionMutable.value + 1L
        }
    }

    suspend fun <T> withSyncLock(block: suspend () -> T): T {
        return syncMutex.withLock { block() }
    }

    private fun persistStateToDaos(state: OfflineSyncState) {
        database.runInTransaction {
            todoDao.deleteAll()
            todoDao.insertAll(state.todos.map { it.toEntity() })
            floaterDao.deleteAll()
            floaterDao.insertAll(state.floaters.map { it.toEntity() })
            listDao.deleteAll()
            listDao.insertAll(state.lists.map { it.toEntity() })
            floaterListDao.deleteAll()
            floaterListDao.insertAll(state.floaterLists.map { it.toEntity() })
            completedDao.deleteAll()
            completedDao.insertAll(state.completedItems.map { it.toEntity() })
            completedFloaterDao.deleteAll()
            completedFloaterDao.insertAll(state.completedFloaters.map { it.toEntity() })
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
                previous.floaters != next.floaters ||
            previous.completedItems != next.completedItems ||
                previous.completedFloaters != next.completedFloaters ||
            previous.lists != next.lists ||
                previous.floaterLists != next.floaterLists ||
            previous.aiSummaryEnabled != next.aiSummaryEnabled
    }

    private fun hasSyncMetadataChanges(
        previous: OfflineSyncState,
        next: OfflineSyncState,
    ): Boolean {
        return previous.lastSuccessfulSyncEpochMs != next.lastSuccessfulSyncEpochMs ||
                previous.lastSyncAttemptEpochMs != next.lastSyncAttemptEpochMs ||
                previous.pendingMutations != next.pendingMutations
    }

    private companion object {
        const val LOG_TAG = "OfflineCacheManager"
    }
}

/**
 * Re-attaches mutations that a concurrent writer queued while a sync was mid-flight.
 *
 * A sync builds its saves from a snapshot loaded BEFORE a multi-second network phase, so a
 * mutation queued during that window (a widget check-off is the common case) is absent from
 * [next] purely because the sync never saw it — writing [next] verbatim would silently drop
 * that completion. [consumedMutationIds] is the set the sync legitimately owns (the ids it
 * loaded, i.e. replayed or deliberately dropped); anything persisted but in neither [next] nor
 * that set was queued concurrently and is preserved, ordered back into the queue by timestamp.
 *
 * Pure + internal so the rule is directly testable — the race it guards is impractical to
 * reproduce by hand.
 */
internal fun mergeConcurrentlyQueuedMutations(
    persisted: List<PendingMutationRecord>,
    next: List<PendingMutationRecord>,
    consumedMutationIds: Set<String>,
): List<PendingMutationRecord> {
    val keptIds = next.mapTo(HashSet()) { it.mutationId }
    val preserved = persisted.filter { queued ->
        queued.mutationId !in keptIds && queued.mutationId !in consumedMutationIds
    }
    if (preserved.isEmpty()) return next
    return (next + preserved).sortedBy { it.timestampEpochMs }
}
