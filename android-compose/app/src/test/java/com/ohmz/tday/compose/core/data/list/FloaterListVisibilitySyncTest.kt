package com.ohmz.tday.compose.core.data.list

import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.floaterListFromCache
import com.ohmz.tday.compose.core.data.cache.mergeConcurrentlyQueuedMutations
import com.ohmz.tday.compose.core.data.db.toEntity
import com.ohmz.tday.compose.core.data.db.toRecord
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.AppSettingsResponse
import com.ohmz.tday.compose.core.model.CompletedFloatersResponse
import com.ohmz.tday.compose.core.model.CompletedTodosResponse
import com.ohmz.tday.compose.core.model.FloaterListDto
import com.ohmz.tday.compose.core.model.FloaterListsResponse
import com.ohmz.tday.compose.core.model.FloatersResponse
import com.ohmz.tday.compose.core.model.ListsResponse
import com.ohmz.tday.compose.core.model.MessageResponse
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.model.TodosResponse
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.shared.model.UpdateFloaterListRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FloaterListVisibilitySyncTest {
    private val listId = "fl-1"

    @Test
    fun `a cache written before the field existed reads as shown`() {
        val record = Json { ignoreUnknownKeys = true }
            .decodeFromString<CachedFloaterListRecord>("""{"id":"fl-1","name":"Someday"}""")

        assertTrue(record.showOnMainScreen)
    }

    @Test
    fun `the setting survives the room round trip`() {
        val hidden = CachedFloaterListRecord(id = listId, name = "Someday", showOnMainScreen = false)

        assertFalse(hidden.toEntity().toRecord().showOnMainScreen)
    }

    @Test
    fun `the setting reaches the domain list`() {
        val hidden = CachedFloaterListRecord(id = listId, name = "Someday", showOnMainScreen = false)

        assertFalse(floaterListFromCache(hidden, 0).showOnMainScreen)
    }

    @Test
    fun `an offline toggle replays and survives the pull`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                floaterLists = listOf(
                    CachedFloaterListRecord(
                        id = listId,
                        name = "Someday",
                        updatedAtEpochMs = 5_000L,
                        showOnMainScreen = false,
                    ),
                ),
                pendingMutations = listOf(
                    PendingMutationRecord(
                        mutationId = "m-1",
                        kind = MutationKind.UPDATE_FLOATER_LIST,
                        targetId = listId,
                        timestampEpochMs = 5_000L,
                        name = "Someday",
                        showOnMainScreen = false,
                    ),
                ),
            ),
        )
        val patched = slot<UpdateFloaterListRequest>()
        val api = fakeApi(serverShows = true)
        coEvery { api.patchFloaterListByBody(capture(patched)) } returns
            Response.success(MessageResponse(message = "ok"))
        val syncManager = SyncManager(api, fakeCache(stateRef), fakeSecureConfigStore(), mockk(relaxed = true))

        val result = syncManager.syncCachedData(force = true, replayPendingMutations = true)

        assertTrue(result.isSuccess)
        assertEquals(false, patched.captured.showOnMainScreen)
        assertFalse(stateRef.get().floaterLists.single().showOnMainScreen)
    }

    @Test
    fun `a newer server value replaces the cached one`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                floaterLists = listOf(
                    CachedFloaterListRecord(id = listId, name = "Someday", updatedAtEpochMs = 500L),
                ),
            ),
        )
        val api = fakeApi(serverShows = false)
        val syncManager = SyncManager(api, fakeCache(stateRef), fakeSecureConfigStore(), mockk(relaxed = true))

        syncManager.syncCachedData(force = true)

        assertFalse(stateRef.get().floaterLists.single().showOnMainScreen)
    }

    private fun fakeApi(serverShows: Boolean): TdayApiService = mockk {
        coEvery { getTodos(any(), any(), any(), any()) } returns Response.success(TodosResponse())
        coEvery { getCompletedTodos() } returns Response.success(CompletedTodosResponse())
        coEvery { getFloaters() } returns Response.success(FloatersResponse())
        coEvery { getCompletedFloaters() } returns Response.success(CompletedFloatersResponse())
        coEvery { getLists() } returns Response.success(ListsResponse())
        coEvery { getFloaterLists() } returns Response.success(
            FloaterListsResponse(
                lists = listOf(
                    FloaterListDto(
                        id = listId,
                        name = "Someday",
                        updatedAt = Instant.ofEpochMilli(1_000L).toString(),
                        showOnMainScreen = serverShows,
                    ),
                ),
            ),
        )
        coEvery { getPreferences() } returns Response.success(PreferencesResponse())
        coEvery { getAppSettings() } returns Response.success(AppSettingsResponse())
    }

    private fun fakeSecureConfigStore(): SecureConfigStore = mockk(relaxed = true) {
        every { isLocalMode() } returns false
        every { getListIcon(any()) } returns null
    }

    private fun fakeCache(stateRef: AtomicReference<OfflineSyncState>): OfflineCacheManager {
        val cache = mockk<OfflineCacheManager>()
        coEvery { cache.loadOfflineState() } answers { stateRef.get() }
        every { cache.loadOfflineStateBlocking() } answers { stateRef.get() }
        coEvery { cache.updateOfflineState(any()) } coAnswers {
            val next = firstArg<(OfflineSyncState) -> OfflineSyncState>()(stateRef.get())
            stateRef.set(next)
            next
        }
        coEvery { cache.saveOfflineState(any(), any()) } coAnswers {
            val next = firstArg<OfflineSyncState>()
            val consumed = secondArg<Set<String>?>()
            val previous = stateRef.get()
            stateRef.set(
                if (consumed != null) {
                    next.copy(
                        pendingMutations = mergeConcurrentlyQueuedMutations(
                            persisted = previous.pendingMutations,
                            next = next.pendingMutations,
                            consumedMutationIds = consumed,
                        ),
                    )
                } else {
                    next
                },
            )
        }
        coEvery { cache.withSyncLock<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        return cache
    }
}
