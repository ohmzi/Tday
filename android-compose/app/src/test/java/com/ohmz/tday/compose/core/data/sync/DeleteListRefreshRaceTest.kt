package com.ohmz.tday.compose.core.data.sync

import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.model.AppSettingsResponse
import com.ohmz.tday.compose.core.model.CompletedFloatersResponse
import com.ohmz.tday.compose.core.model.CompletedTodosResponse
import com.ohmz.tday.compose.core.model.FloaterListsResponse
import com.ohmz.tday.compose.core.model.FloatersResponse
import com.ohmz.tday.compose.core.model.ListDto
import com.ohmz.tday.compose.core.model.ListsResponse
import com.ohmz.tday.compose.core.model.PreferencesResponse
import com.ohmz.tday.compose.core.model.TodosResponse
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.feature.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Regression test for "delete a list, pull-to-refresh, it reappears, a second
 * refresh makes it go away for good": [ListRepository.stageDeleteList] prunes the
 * list from the cache immediately but — by design, so Undo needs no network trace
 * — records no real DELETE_LIST pending mutation until the delayed commit fires
 * ~8.5s later (see `UndoableDeleteCoordinator.COMMIT_DELAY_MS`). Before this fix,
 * [SyncManager.mergeRemoteWithLocal]'s resurrection guard only recognized a REAL
 * pending DELETE_LIST mutation, so a pull-to-refresh landing inside that window
 * wrote the still-server-side list straight back into the cache; only the
 * independent 8.5s timer's own later commit corrected it, which the user observed
 * as "a second refresh makes it go away."
 *
 * The fix has staging write a non-replayable `staged` marker of the same kind (see
 * [com.ohmz.tday.compose.core.data.PendingMutationRecord.staged]) so the merge
 * guard covers the whole staged-but-undecided window. This test interleaves
 * exactly that: stage a delete, then run a real sync (what pull-to-refresh does)
 * while the server still has the list, and assert it stays gone. iOS carries the
 * byte-identical fix (`PendingMutationRecord.staged` in OfflineSyncModels.swift).
 */
class DeleteListRefreshRaceTest {

    private val listId = "list-1"

    @Test
    fun `pull-to-refresh mid undo-window does not resurrect a staged list delete`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                lists = listOf(CachedListRecord(id = listId, name = "Groceries", todoCount = 0)),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = fakeApiWithListStillOnServer(listId, "Groceries")
        val secureConfigStore = fakeSecureConfigStore()
        val syncManager = SyncManager(api, cache, secureConfigStore, mockk(relaxed = true))
        val listRepository = ListRepository(api, cache, secureConfigStore, syncManager)

        // Stage the delete: local-only removal, no server call yet, no real
        // DELETE_LIST mutation — the commit is still ~8.5s away.
        listRepository.stageDeleteList(listId)
        assertTrue(
            "list must be gone from the cache immediately after staging",
            stateRef.get().lists.none { it.id == listId },
        )

        // A pull-to-refresh races the still-open undo window; the server still
        // has the list (fakeApiWithListStillOnServer), exactly as it would before
        // the deferred commit's DELETE reaches it.
        val result = syncManager.syncCachedData(force = true)

        assertTrue("sync must succeed", result.isSuccess)
        assertTrue(
            "the deleted list must not resurrect from a mid-window refresh, found: " +
                stateRef.get().lists,
            stateRef.get().lists.none { it.id == listId },
        )
    }

    @Test
    fun `undo after staging restores the list and leaves no stray pending mutation`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lists = listOf(CachedListRecord(id = listId, name = "Groceries", todoCount = 0)),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = mockk<TdayApiService>()
        val secureConfigStore = fakeSecureConfigStore()
        val syncManager = SyncManager(api, cache, secureConfigStore, mockk(relaxed = true))
        val listRepository = ListRepository(api, cache, secureConfigStore, syncManager)

        val staged = listRepository.stageDeleteList(listId)
        assertTrue(stateRef.get().lists.none { it.id == listId })
        assertEquals(1, stateRef.get().pendingMutations.size)
        assertTrue(
            "the staged marker must be flagged non-replayable",
            stateRef.get().pendingMutations.single().staged,
        )

        listRepository.undoStagedListDeletion(staged)

        assertTrue(
            "undo must restore the list",
            stateRef.get().lists.any { it.id == listId },
        )
        assertTrue(
            "undo must not leave the staged marker stranded in the pending queue",
            stateRef.get().pendingMutations.isEmpty(),
        )
    }

    /** In-memory fake backing [OfflineCacheManager]'s observable state transitions —
     * real enough to exercise [ListRepository] and [SyncManager] without Room. */
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
        coEvery { cache.withSyncLock<Boolean>(any()) } coAnswers {
            firstArg<suspend () -> Boolean>().invoke()
        }
        return cache
    }

    private fun fakeSecureConfigStore(): SecureConfigStore = mockk(relaxed = true) {
        every { isLocalMode() } returns false
        every { getListIcon(any()) } returns null
    }

    /** Stubs every endpoint [SyncManager]'s remote snapshot fetches, with [listId]
     * still present server-side — the state a pull-to-refresh would see while the
     * deferred DELETE hasn't reached the backend yet. */
    private fun fakeApiWithListStillOnServer(listId: String, listName: String): TdayApiService {
        return mockk {
            coEvery { getTodos(any(), any(), any(), any()) } returns Response.success(TodosResponse())
            coEvery { getCompletedTodos() } returns Response.success(CompletedTodosResponse())
            coEvery { getFloaters() } returns Response.success(FloatersResponse())
            coEvery { getCompletedFloaters() } returns Response.success(CompletedFloatersResponse())
            coEvery { getLists() } returns Response.success(
                ListsResponse(lists = listOf(ListDto(id = listId, name = listName))),
            )
            coEvery { getFloaterLists() } returns Response.success(FloaterListsResponse())
            coEvery { getPreferences() } returns Response.success(PreferencesResponse())
            coEvery { getAppSettings() } returns Response.success(AppSettingsResponse())
        }
    }
}
