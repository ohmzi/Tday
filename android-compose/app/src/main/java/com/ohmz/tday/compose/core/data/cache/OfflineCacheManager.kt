package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.ThemePreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.CookieManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCacheManager @Inject constructor(
    private val secureConfigStore: SecureConfigStore,
    private val json: Json,
    private val themePreferenceStore: ThemePreferenceStore,
    private val cookieManager: CookieManager,
) {
    private val syncMutex = Mutex()
    private val cacheDataVersionMutable = MutableStateFlow(0L)
    val cacheDataVersion: StateFlow<Long> = cacheDataVersionMutable.asStateFlow()

    @Volatile
    private var lastPersistedState: OfflineSyncState? = null

    fun loadOfflineState(): OfflineSyncState {
        val decoded = decodeOfflineSyncState(
            raw = secureConfigStore.getOfflineSyncStateRaw().orEmpty(),
        )
        lastPersistedState = decoded
        return decoded
    }

    fun saveOfflineState(state: OfflineSyncState) {
        val previous = lastPersistedState ?: decodeOfflineSyncState(
            raw = secureConfigStore.getOfflineSyncStateRaw().orEmpty(),
        )
        if (previous == state) return
        secureConfigStore.saveOfflineSyncStateRaw(json.encodeToString(state))
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
        val state = loadOfflineState()
        return state.todos.isNotEmpty() ||
            state.lists.isNotEmpty() ||
            state.completedItems.isNotEmpty() ||
            state.pendingMutations.isNotEmpty()
    }

    fun clearAllLocalData() {
        val previous = lastPersistedState ?: decodeOfflineSyncState(
            raw = secureConfigStore.getOfflineSyncStateRaw().orEmpty(),
        )
        val cleared = OfflineSyncState()

        secureConfigStore.clearAllLocalData()
        themePreferenceStore.clear()
        runCatching { cookieManager.cookieStore?.removeAll() }

        lastPersistedState = cleared
        if (hasUiDataChanges(previous, cleared)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
        }
    }

    fun clearSessionOnly() {
        val previous = lastPersistedState ?: decodeOfflineSyncState(
            raw = secureConfigStore.getOfflineSyncStateRaw().orEmpty(),
        )
        val cleared = OfflineSyncState()

        runCatching { cookieManager.cookieStore?.removeAll() }
        secureConfigStore.clearOfflineSyncState()

        lastPersistedState = cleared
        if (hasUiDataChanges(previous, cleared)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
        }
    }

    suspend fun <T> withSyncLock(block: suspend () -> T): T {
        return syncMutex.withLock { block() }
    }

    private fun decodeOfflineSyncState(raw: String): OfflineSyncState {
        if (raw.isBlank()) return OfflineSyncState()
        return try {
            json.decodeFromString<OfflineSyncState>(raw)
        } catch (_: SerializationException) {
            OfflineSyncState()
        } catch (_: IllegalArgumentException) {
            OfflineSyncState()
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
}
