package com.ohmz.tday.compose.core.data.sync

import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.mergeConcurrentlyQueuedMutations
import com.ohmz.tday.compose.core.model.AppSettingsResponse
import com.ohmz.tday.compose.core.model.CompletedFloaterDto
import com.ohmz.tday.compose.core.model.CompletedFloatersResponse
import com.ohmz.tday.compose.core.model.CompletedTodoDto
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * The Completed screen's own counterpart to [DeleteListRefreshRaceTest]: a sync
 * landing after an un-complete must not put the restored task back.
 *
 * [SyncManager.mergeRemoteWithLocal] assigns the server's completed list to the
 * cache wholesale — `completedItems = remoteCompleted` — and that snapshot was
 * fetched *before* the merge ran. The guard directly above it only protects the
 * opposite direction: it re-adds a LOCAL completion that a remote read is missing
 * (`pendingTodoCanonicalIds.forEach { ... }`). Nothing stopped a remote row the
 * user had just restored from being written back over its own removal.
 *
 * The window is not hypothetical. Un-completing publishes a `completed` realtime
 * event to the actor, [com.ohmz.tday.compose.feature.app.AppViewModel] coalesces
 * it into a sync ~400 ms later, and that sync's `getCompletedTodos()` began its
 * round trip while the server still had the task completed. The user sees the row
 * it just dismissed come back, struck through, and then vanish when the next
 * merge lands — the "freaking out" the screen was reported for.
 *
 * The fix is the one this codebase already uses for a staged delete: while a row
 * carries a pending local mutation of its own, the merge keeps the local answer
 * for that row instead of the server's. These tests pin both halves — that the
 * guard holds, and that it releases the moment the mutation is acknowledged, so
 * it cannot mask a genuine server-side change forever.
 */
class CompletedUncompleteRefreshRaceTest {

    private val todoId = "todo-1"
    private val completedId = "completed-1"
    private val title = "Buy milk"

    private fun completedTodoMutation() = PendingMutationRecord(
        mutationId = "mutation-1",
        kind = MutationKind.UNCOMPLETE_TODO,
        targetId = todoId,
        timestampEpochMs = 1_000L,
    )

    @Test
    fun `a refresh landing while the un-complete is unacknowledged does not put the task back`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                // The user's tap already pruned both halves locally: the timeline
                // row is pending again and the completion record is gone.
                todos = listOf(cachedTodo(id = todoId, completed = false, updatedAtEpochMs = 2_000L)),
                completedItems = emptyList(),
                pendingMutations = listOf(completedTodoMutation()),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = fakeApiWithTaskStillCompleted()
        val syncManager = SyncManager(api, cache, fakeSecureConfigStore(), mockk<WidgetRefresher>(relaxed = true))

        val result = syncManager.syncCachedData(force = true, replayPendingMutations = false)

        assertTrue("sync must succeed", result.isSuccess)
        assertTrue(
            "a task the user restored must not come back from a refresh that raced its " +
                "unacknowledged un-complete, found: " + stateRef.get().completedItems,
            stateRef.get().completedItems.none { it.originalTodoId == todoId },
        )
    }

    @Test
    fun `once the un-complete is acknowledged the server's answer is authoritative again`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                todos = listOf(cachedTodo(id = todoId, completed = false, updatedAtEpochMs = 2_000L)),
                completedItems = emptyList(),
                // Nothing pending: the PATCH returned and the mutation was consumed.
                pendingMutations = emptyList(),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = fakeApiWithTaskStillCompleted()
        val syncManager = SyncManager(api, cache, fakeSecureConfigStore(), mockk<WidgetRefresher>(relaxed = true))

        val result = syncManager.syncCachedData(force = true, replayPendingMutations = false)

        assertTrue("sync must succeed", result.isSuccess)
        // The guard is a window, not a permanent local override: an acked row takes
        // the server's answer, so a change made on another device still lands here.
        assertTrue(
            "a server row that is still completed must land once nothing is pending for it, " +
                "found: " + stateRef.get().completedItems,
            stateRef.get().completedItems.any { it.originalTodoId == todoId },
        )
    }

    /**
     * The guard must not swallow the opposite intent. A restore stays unacknowledged for
     * as long as its PATCH takes, and a user who restores a task and immediately completes
     * it again has both mutations queued — hiding the row then would take it out of the
     * list they just put it in, which is the same complaint this guard exists to fix,
     * mirrored. The later intent wins.
     */
    @Test
    fun `re-completing a task whose restore is still unacknowledged still shows it completed`() = runBlocking {
        val stateRef = AtomicReference(
            OfflineSyncState(
                lastSuccessfulSyncEpochMs = 1L,
                todos = listOf(cachedTodo(id = todoId, completed = true, updatedAtEpochMs = 3_000L)),
                completedItems = listOf(
                    CachedCompletedRecord(
                        id = completedId,
                        originalTodoId = todoId,
                        title = title,
                        priority = "Low",
                        completedAtEpochMs = 3_000L,
                    ),
                ),
                pendingMutations = listOf(
                    completedTodoMutation(),
                    PendingMutationRecord(
                        mutationId = "mutation-2",
                        kind = MutationKind.COMPLETE_TODO,
                        targetId = todoId,
                        timestampEpochMs = 3_000L,
                    ),
                ),
            ),
        )
        val cache = fakeCacheManager(stateRef)
        val api = fakeApiWithTaskStillCompleted()
        val syncManager = SyncManager(api, cache, fakeSecureConfigStore(), mockk<WidgetRefresher>(relaxed = true))

        val result = syncManager.syncCachedData(force = true, replayPendingMutations = false)

        assertTrue("sync must succeed", result.isSuccess)
        assertTrue(
            "a task the user re-completed while its restore was in flight must stay completed, " +
                "found: " + stateRef.get().completedItems,
            stateRef.get().completedItems.any { it.originalTodoId == todoId },
        )
    }

    private fun cachedTodo(id: String, completed: Boolean, updatedAtEpochMs: Long) = CachedTodoRecord(
        id = id,
        canonicalId = id,
        title = title,
        priority = "Low",
        pinned = false,
        completed = completed,
        listId = null,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    /** In-memory fake mirroring [DeleteListRefreshRaceTest]'s, including the real
     * [mergeConcurrentlyQueuedMutations] reconciliation on save. */
    private fun fakeCacheManager(stateRef: AtomicReference<OfflineSyncState>): OfflineCacheManager {
        val syncLock = Mutex()
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

    /**
     * The server's state while the un-complete has not reached it: the task is
     * still completed, and its completion record is still in the completed list.
     */
    private fun fakeApiWithTaskStillCompleted(): TdayApiService = mockk {
        coEvery { getTodos(any(), any(), any(), any()) } returns Response.success(TodosResponse())
        coEvery { getCompletedTodos() } returns Response.success(
            CompletedTodosResponse(
                completedTodos = listOf(
                    CompletedTodoDto(
                        id = completedId,
                        originalTodoID = todoId,
                        title = title,
                        priority = "Low",
                        due = "",
                        completedOnTime = true,
                    ),
                ),
            ),
        )
        coEvery { getFloaters() } returns Response.success(FloatersResponse())
        coEvery { getCompletedFloaters() } returns Response.success(
            CompletedFloatersResponse(completedFloaters = emptyList<CompletedFloaterDto>()),
        )
        coEvery { getLists() } returns Response.success(ListsResponse(lists = emptyList<ListDto>()))
        coEvery { getFloaterLists() } returns Response.success(FloaterListsResponse())
        coEvery { getPreferences() } returns Response.success(PreferencesResponse())
        coEvery { getAppSettings() } returns Response.success(AppSettingsResponse())
    }
}
