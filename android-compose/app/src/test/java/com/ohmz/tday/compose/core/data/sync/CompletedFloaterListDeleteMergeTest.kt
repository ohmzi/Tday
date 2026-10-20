package com.ohmz.tday.compose.core.data.sync

import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.list.FloaterListRepository
import com.ohmz.tday.compose.core.model.AppSettingsResponse
import com.ohmz.tday.compose.core.model.CompletedFloaterDto
import com.ohmz.tday.compose.core.model.CompletedFloatersResponse
import com.ohmz.tday.compose.core.model.CompletedTodosResponse
import com.ohmz.tday.compose.core.model.FloaterListDto
import com.ohmz.tday.compose.core.model.FloaterListsResponse
import com.ohmz.tday.compose.core.model.FloatersResponse
import com.ohmz.tday.compose.core.model.ListsResponse
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.model.TodosResponse
import com.ohmz.tday.compose.core.network.TdayApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Regression coverage for the Android mirror of the iOS bug fixed in
 * `CompletedSyncMergeTests.testPendingDeletedFloaterListDoesNotRemoveRemoteCompletedFloaterRecordsForThatList`
 * (fixed on iOS in `0028a20e`, see docs/design/completed-floaters-durability.md).
 *
 * `FloaterListRepository.stageDeleteList`/`deleteList` no longer prune `completedFloaters`
 * from the local cache on floater-list delete (PR #135), so a completed floater whose list
 * is mid-deletion is expected to keep surviving in the cache. But
 * [SyncManager.mergeRemoteWithLocal] had an independent second bug: it built
 * `remoteCompletedFloaters` by filtering OUT every remote completed-floater row whose
 * `listId` matched a list with a pending (including staged, not-yet-committed)
 * `DELETE_FLOATER_LIST` mutation — the same `pendingDeletedFloaterListIds` guard that
 * (correctly) keeps a staged list delete from resurrecting the list itself. That guard has
 * no business applying to completed-floater rows: any sync landing while the delete is
 * staged or still replaying dropped that list's completed floaters right back out of the
 * merged cache, even though local pruning no longer touches them — undoing PR #135's fix on
 * every next sync.
 *
 * This test stages a floater-list delete (the ~8.5s undo window from
 * `UndoableDeleteCoordinator`, same mechanism [DeleteListRefreshRaceTest] exercises for
 * scheduled lists) while the server still reports the list and a completed floater
 * pointing at it — exactly the state a pull-to-refresh mid-undo-window would observe — and
 * asserts the completed floater is NOT dropped from the merged cache.
 */
class CompletedFloaterListDeleteMergeTest {

    private val listId = "floater-list-1"
    private val listName = "Groceries"
    private val completedFloaterId = "completed-floater-1"
    private val originalFloaterId = "floater-1"

    @Test
    fun `pull-to-refresh mid undo-window keeps remote completed floaters for a staged floater-list delete`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                floaterLists = listOf(CachedFloaterListRecord(id = listId, name = listName)),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = fakeApiWithListAndCompletedFloasterStillOnServer(listId, listName)
        val secureConfigStore = fakeSecureConfigStore()
        val syncManager = SyncManager(api, cache, secureConfigStore, mockk(relaxed = true))
        val floaterListRepository = FloaterListRepository(api, cache, secureConfigStore, syncManager)

        // Stage the delete: local-only removal of the floaterList/floaters, no server call
        // yet, no real DELETE_FLOATER_LIST mutation — the commit is still ~8.5s away. Per
        // PR #135, completedFloaters are deliberately left untouched by this step.
        floaterListRepository.stageDeleteList(listId)
        assertTrue(
            "floater list must be gone from the cache immediately after staging",
            stateRef.get().floaterLists.none { it.id == listId },
        )

        // A pull-to-refresh races the still-open undo window; the server still has the
        // list and its completed floater (fakeApiWithListAndCompletedFloasterStillOnServer),
        // exactly as it would before the deferred DELETE reaches it.
        val result = syncManager.syncCachedData(force = true)

        assertTrue("sync must succeed", result.isSuccess)
        assertTrue(
            "a completed floater for a list with only a staged/pending delete must " +
                "survive the merge, found: " + stateRef.get().completedFloaters,
            stateRef.get().completedFloaters.any { it.id == completedFloaterId },
        )
    }

    private fun fakeCacheManager(stateRef: AtomicReference<OfflineSyncState>): OfflineCacheManager {
        val cache = mockk<OfflineCacheManager>()
        coEvery { cache.loadOfflineState() } answers { stateRef.get() }
        every { cache.loadOfflineStateBlocking() } answers { stateRef.get() }
        coEvery { cache.updateOfflineState(any()) } coAnswers {
            val transform = firstArg<(OfflineSyncState) -> OfflineSyncState>()
            val next = transform(stateRef.get())
            stateRef.set(next)
            next
        }
        coEvery { cache.saveOfflineState(any(), any()) } coAnswers {
            stateRef.set(firstArg())
        }
        coEvery { cache.withSyncLock<Any?>(any()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }
        return cache
    }

    private fun fakeSecureConfigStore(): SecureConfigStore = mockk(relaxed = true) {
        every { isLocalMode() } returns false
        every { getListIcon(any()) } returns null
    }

    /** Stubs every endpoint [SyncManager]'s remote snapshot fetches, with [listId] and its
     * completed floater still present server-side — the state a pull-to-refresh would see
     * while the deferred DELETE_FLOATER_LIST hasn't reached the backend yet. */
    private fun fakeApiWithListAndCompletedFloasterStillOnServer(
        listId: String,
        listName: String,
    ): TdayApiService {
        return mockk {
            coEvery { getTodos(any(), any(), any(), any()) } returns Response.success(TodosResponse())
            coEvery { getCompletedTodos() } returns Response.success(CompletedTodosResponse())
            coEvery { getFloaters() } returns Response.success(FloatersResponse())
            coEvery { getCompletedFloaters() } returns Response.success(
                CompletedFloatersResponse(
                    completedFloaters = listOf(
                        CompletedFloaterDto(
                            id = completedFloaterId,
                            originalFloaterID = originalFloaterId,
                            title = "Buy milk",
                            listID = listId,
                            listName = listName,
                            listDeleted = false,
                        ),
                    ),
                ),
            )
            coEvery { getLists() } returns Response.success(ListsResponse())
            coEvery { getFloaterLists() } returns Response.success(
                FloaterListsResponse(lists = listOf(FloaterListDto(id = listId, name = listName))),
            )
            coEvery { getPreferences() } returns Response.success(PreferencesResponse())
            coEvery { getAppSettings() } returns Response.success(AppSettingsResponse())
        }
    }
}
