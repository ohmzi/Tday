package com.ohmz.tday.compose.core.data.sync

import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.mergeConcurrentlyQueuedMutations
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * guard covers the whole staged-but-undecided window. The first test below covers
 * that directly: stage a delete, then run a real sync (what pull-to-refresh does)
 * while the server still has the list, and assert it stays gone.
 *
 * That marker alone narrows the window rather than closing it: a sync's merge is
 * computed from a snapshot read BEFORE its network round trip
 * (`SyncManager.syncLocalCache`), so a [ListRepository.stageDeleteList] call that
 * lands *during* that round trip — not just before it — would still have its
 * prune silently overwritten by the sync's final, unconditionally-applied save
 * (see [mergeConcurrentlyQueuedMutations]'s doc: only `pendingMutations` are
 * reconciled against a concurrent writer, `lists` is not). `stageDeleteList`
 * closes that by running inside [OfflineCacheManager.withSyncLock] — the same
 * lock a sync holds for its whole read-fetch-merge-save span — so the third test
 * below exercises exactly that interleaving with a real, shared [Mutex] standing
 * in for [OfflineCacheManager]'s, rather than the passthrough `withSyncLock` fake
 * the first two tests use.
 *
 * iOS carries the byte-identical fix, including the sync-lock serialization, in
 * `PendingMutationRecord.staged` and `ListRepository.stageDeleteList` /
 * `FloaterListRepository.stageDeleteList` (both now `async`) in OfflineSyncModels.swift
 * and List/FloaterListRepository.swift.
 */
class DeleteListRefreshRaceTest {

    private val listId = "list-1"
    private val listName = "Groceries"

    @Test
    fun `pull-to-refresh mid undo-window does not resurrect a staged list delete`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                lists = listOf(CachedListRecord(id = listId, name = listName, todoCount = 0)),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = fakeApiWithListStillOnServer(listId, listName)
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
                lists = listOf(CachedListRecord(id = listId, name = listName, todoCount = 0)),
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

    /**
     * The residual race described in the class doc: a delete staged not just
     * before a sync, but *while its network round trip is already in flight*.
     * Without [OfflineCacheManager.withSyncLock] serializing
     * [ListRepository.stageDeleteList] against [SyncManager.syncCachedData]'s
     * whole read-fetch-merge-save span, this reproduces the original bug through
     * a narrower door: the sync reads a pre-delete snapshot, suspends on the
     * network, `stageDeleteList` prunes the list and lands its write while the
     * sync is still suspended, and the sync's final save — computed from the
     * stale snapshot it started with — overwrites `lists` and resurrects it.
     *
     * [fakeCacheManager]'s `withSyncLock` is backed by a real, shared [Mutex] here
     * (not a passthrough) so this test can assert `stageDeleteList` actually
     * blocks while the sync holds it, not just that the end state happens to
     * come out right.
     */
    @Test
    fun `stageDeleteList concurrent with an in-flight sync's network round trip does not resurrect the list`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                lists = listOf(CachedListRecord(id = listId, name = listName, todoCount = 0)),
            ),
        )
        val syncLock = Mutex()
        val cache = fakeCacheManager(stateRef, syncLock)
        val networkGate = CompletableDeferred<Unit>()
        val enteredNetworkPhase = CompletableDeferred<Unit>()
        val api = fakeApiWithGatedLists(listId, listName, networkGate, enteredNetworkPhase)
        val secureConfigStore = fakeSecureConfigStore()
        val syncManager = SyncManager(api, cache, secureConfigStore, mockk(relaxed = true))
        val listRepository = ListRepository(api, cache, secureConfigStore, syncManager)

        val syncJob = launch(Dispatchers.Default) { syncManager.syncCachedData(force = true) }

        // Wait until the sync has read its pre-delete snapshot and is
        // suspended mid network round trip, still holding the sync lock.
        enteredNetworkPhase.await()

        val stageJob = async(Dispatchers.Default) { listRepository.stageDeleteList(listId) }
        // Give stageDeleteList a real chance to run to completion if it were
        // NOT serialized against the sync's lock.
        delay(200)
        assertTrue(
            "stageDeleteList must block behind the in-flight sync's lock instead " +
                "of racing its write in while the sync is mid network round trip",
            !stageJob.isCompleted,
        )

        networkGate.complete(Unit)
        syncJob.join()
        stageJob.await()

        assertTrue(
            "the deleted list must not resurrect from a delete concurrent with an " +
                "in-flight sync's network round trip, found: " + stateRef.get().lists,
            stateRef.get().lists.none { it.id == listId },
        )
        assertTrue(
            "the staged marker must still be recorded once staging finally runs",
            stateRef.get().pendingMutations.any { it.targetId == listId && it.staged },
        )
    }

    /**
     * In-memory fake backing [OfflineCacheManager]'s observable state transitions —
     * real enough to exercise [ListRepository] and [SyncManager] without Room.
     *
     * `saveOfflineState` mirrors [OfflineCacheManager.saveOfflineStateBlocking]'s
     * actual concurrency contract rather than a blind passthrough: `lists` (and
     * every other collection but `pendingMutations`) is overwritten unconditionally
     * from the caller's snapshot, while `pendingMutations` alone is reconciled
     * against a concurrent writer via the real
     * [mergeConcurrentlyQueuedMutations] — so a test relying on that reconciliation
     * is exercising the same rule production does, not a simplified stand-in for it.
     *
     * `withSyncLock` is backed by [syncLock] — a real [Mutex] shared across every
     * call this fake answers, so [SyncManager.syncCachedData] and
     * [ListRepository.stageDeleteList] genuinely contend on the same lock instead
     * of independently no-op-ing through a stub.
     */
    private fun fakeCacheManager(
        stateRef: AtomicReference<OfflineSyncState>,
        syncLock: Mutex = Mutex(),
    ): OfflineCacheManager {
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
            val next = firstArg<OfflineSyncState>()
            val consumedMutationIds = secondArg<Set<String>?>()
            val previous = stateRef.get()
            stateRef.set(
                if (consumedMutationIds != null) {
                    next.copy(
                        pendingMutations = mergeConcurrentlyQueuedMutations(
                            persisted = previous.pendingMutations,
                            next = next.pendingMutations,
                            consumedMutationIds = consumedMutationIds,
                        ),
                    )
                } else {
                    next
                },
            )
        }
        coEvery { cache.withSyncLock<Any?>(any()) } coAnswers {
            syncLock.withLock { firstArg<suspend () -> Any?>().invoke() }
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

    /**
     * Same as [fakeApiWithListStillOnServer], except `getLists()` signals
     * [enteredNetworkPhase] the instant it is invoked, then suspends until
     * [networkGate] is completed — modeling a sync that is genuinely mid
     * network round trip so a test can deterministically land a concurrent
     * write inside that window instead of hoping for a lucky interleaving.
     */
    private fun fakeApiWithGatedLists(
        listId: String,
        listName: String,
        networkGate: CompletableDeferred<Unit>,
        enteredNetworkPhase: CompletableDeferred<Unit>,
    ): TdayApiService {
        return mockk {
            coEvery { getTodos(any(), any(), any(), any()) } returns Response.success(TodosResponse())
            coEvery { getCompletedTodos() } returns Response.success(CompletedTodosResponse())
            coEvery { getFloaters() } returns Response.success(FloatersResponse())
            coEvery { getCompletedFloaters() } returns Response.success(CompletedFloatersResponse())
            coEvery { getLists() } coAnswers {
                enteredNetworkPhase.complete(Unit)
                networkGate.await()
                Response.success(ListsResponse(lists = listOf(ListDto(id = listId, name = listName))))
            }
            coEvery { getFloaterLists() } returns Response.success(FloaterListsResponse())
            coEvery { getPreferences() } returns Response.success(PreferencesResponse())
            coEvery { getAppSettings() } returns Response.success(AppSettingsResponse())
        }
    }
}
