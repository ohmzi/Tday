package com.ohmz.tday.compose.core.data.todo

import com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.feature.widget.WidgetRefresher
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The batched half of [TodoRepository], for the four bulk actions a multi-select
 * in a task list can apply (see `docs/design/bulk-selection.md`).
 *
 * It exists because looping the single-item repository methods does not scale:
 * each of those runs its own [OfflineCacheManager.updateOfflineState] (a full
 * Room load → transform → synchronised save), writes the widget snapshot, bumps
 * `cacheDataVersion` — which re-hydrates the whole list in every ViewModel
 * collecting it — and then makes its own HTTP call. A hundred-row selection would
 * do all of that a hundred times over.
 *
 * Every method here instead applies the whole selection inside **one** cache
 * write, folding the same pure `OfflineSyncState` transforms the single-item
 * paths use, and enqueues one [PendingMutationRecord] per row in that same write.
 * The network fan-out is then a single
 * [SyncManager.syncCachedData] pass, which replays those mutations one at a time
 * over the single-item routes that already exist — there is deliberately no
 * batch endpoint. Local Mode short-circuits before the sync, exactly as the
 * single-item paths do, so a bulk action is a local edit there and nothing else.
 *
 * Failures are not thrown from the fan-out: a mutation that could not be pushed
 * stays queued and replays on the next sync, which is what makes "offline is not
 * a failure" true for bulk as well as for single actions.
 */
@Singleton
class BulkTaskRepository @Inject constructor(
    private val cacheManager: OfflineCacheManager,
    private val syncManager: SyncManager,
    private val widgetRefresher: WidgetRefresher,
) {

    /**
     * Stage step of the delayed-commit bulk delete: prunes every selected task
     * from the local cache in one write and records nothing for the server, so
     * nothing can sync out during the undo window. The returned snapshot covers
     * the whole batch, so a single [TodoRepository.undoStagedTodoDeletion] puts
     * all of it back — the staged types are already list-shaped.
     */
    suspend fun stageDeleteTodos(todos: List<TodoItem>): StagedTodoDeletion {
        if (todos.isEmpty()) return StagedTodoDeletion()
        var staged = StagedTodoDeletion()
        cacheManager.updateOfflineState { state ->
            var collected = StagedTodoDeletion()
            val next = todos.fold(state) { current, todo ->
                val (pruned, removed) = current.withStagedTodoDeletion(
                    canonicalId = todo.canonicalId,
                    instanceDateEpochMs = todo.instanceDateEpochMillis,
                    isRecurringInstanceDelete = todo.isRecurring && todo.instanceDate != null,
                    isLocalOnly = todo.canonicalId.startsWith(LOCAL_TODO_PREFIX),
                )
                collected = collected + removed
                pruned
            }
            staged = collected
            next
        }
        refreshWidgetsNow()
        return staged
    }

    /** Floater counterpart of [stageDeleteTodos]. */
    suspend fun stageDeleteFloaters(floaters: List<TodoItem>): StagedFloaterDeletion {
        if (floaters.isEmpty()) return StagedFloaterDeletion()
        var staged = StagedFloaterDeletion()
        cacheManager.updateOfflineState { state ->
            var collected = StagedFloaterDeletion()
            val next = floaters.fold(state) { current, floater ->
                val (pruned, removed) = current.withStagedFloaterDeletion(
                    canonicalId = floater.canonicalId,
                    isLocalOnly = floater.canonicalId.startsWith(LOCAL_FLOATER_PREFIX),
                )
                collected = collected + removed
                pruned
            }
            staged = collected
            next
        }
        refreshWidgetsNow()
        return staged
    }

    /**
     * Commit step of the bulk delete. Re-runs the prune (a no-op on already
     * staged state) and enqueues one `DELETE_TODO` per row in the same write.
     */
    suspend fun deleteTodos(todos: List<TodoItem>) {
        if (todos.isEmpty()) return
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            todos.fold(state) { current, todo ->
                current.withDeletedTodoCached(
                    canonicalId = todo.canonicalId,
                    instanceDateEpochMs = todo.instanceDateEpochMillis,
                    isRecurringInstanceDelete = todo.isRecurring && todo.instanceDate != null,
                    isLocalOnly = todo.canonicalId.startsWith(LOCAL_TODO_PREFIX),
                    mutationId = UUID.randomUUID().toString(),
                    timestampEpochMs = timestampMs,
                )
            }
        }
    }

    /** Floater counterpart of [deleteTodos]. */
    suspend fun deleteFloaters(floaters: List<TodoItem>) {
        if (floaters.isEmpty()) return
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            floaters.fold(state) { current, floater ->
                current.withDeletedFloaterCached(
                    canonicalId = floater.canonicalId,
                    isLocalOnly = floater.canonicalId.startsWith(LOCAL_FLOATER_PREFIX),
                    mutationId = UUID.randomUUID().toString(),
                    timestampEpochMs = timestampMs,
                )
            }
        }
    }

    /**
     * Commit step of the bulk complete. A recurring occurrence is completed as
     * the occurrence it represents — the queued mutation carries its
     * `instanceDate`, because `PATCH /api/todo/complete` without one writes a
     * history row and leaves the task standing.
     */
    suspend fun completeTodos(todos: List<TodoItem>) {
        if (todos.isEmpty()) return
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            todos.fold(state) { current, todo ->
                current.withCompletedTodoCached(
                    todo = todo,
                    timestampEpochMs = timestampMs,
                    mutationId = UUID.randomUUID().toString(),
                    completedRecordId = "$LOCAL_COMPLETED_PREFIX${UUID.randomUUID()}",
                )
            }
        }
    }

    /** Floater counterpart of [completeTodos]. */
    suspend fun completeFloaters(floaters: List<TodoItem>) {
        if (floaters.isEmpty()) return
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            floaters.fold(state) { current, floater ->
                current.withCompletedFloaterCached(
                    floater = floater,
                    timestampEpochMs = timestampMs,
                    mutationId = UUID.randomUUID().toString(),
                    completedRecordId = "$LOCAL_COMPLETED_FLOATER_PREFIX${UUID.randomUUID()}",
                )
            }
        }
    }

    /**
     * Bulk priority. Uses `SET_PRIORITY`, which replays as
     * `PATCH /api/todo/prioritize` (or the plain priority patch) rather than a
     * whole-record write — the lighter of the two paths the single-item edit
     * sheet could have taken.
     */
    suspend fun setTodoPriority(todos: List<TodoItem>, priority: String) {
        if (todos.isEmpty()) return
        val normalizedPriority = canonicalPriorityValue(priority)
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            todos.fold(state) { current, todo ->
                current.withTodoPriorityCached(
                    todo = todo,
                    priority = normalizedPriority,
                    mutationId = UUID.randomUUID().toString(),
                    timestampEpochMs = timestampMs,
                )
            }
        }
    }

    /**
     * Bulk priority for floaters. There is no floater branch in the
     * `SET_PRIORITY` replay, so this goes through a whole-record
     * `UPDATE_FLOATER` built from the row — changing only the priority.
     */
    suspend fun setFloaterPriority(floaters: List<TodoItem>, priority: String) {
        if (floaters.isEmpty()) return
        val normalizedPriority = canonicalPriorityValue(priority)
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            floaters.fold(state) { current, floater ->
                current.withFloaterEditCached(
                    floater = floater,
                    priority = normalizedPriority,
                    listId = floater.listId,
                    mutationId = UUID.randomUUID().toString(),
                    timestampEpochMs = timestampMs,
                )
            }
        }
    }

    /**
     * Bulk move between scheduled lists. Each payload is rebuilt from the row
     * with only `listId` changed, so the replayed patch never reads as a
     * date or recurrence edit. A null/blank [listId] clears the list.
     */
    suspend fun moveTodosToList(todos: List<TodoItem>, listId: String?) {
        if (todos.isEmpty()) return
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            todos.fold(state) { current, todo ->
                current.withTodoListCached(
                    todo = todo,
                    listId = listId,
                    mutationId = UUID.randomUUID().toString(),
                    timestampEpochMs = timestampMs,
                )
            }
        }
    }

    /** Floater counterpart of [moveTodosToList]; floater lists are their own silo. */
    suspend fun moveFloatersToList(floaters: List<TodoItem>, listId: String?) {
        if (floaters.isEmpty()) return
        val timestampMs = System.currentTimeMillis()
        applyBulk { state ->
            floaters.fold(state) { current, floater ->
                current.withFloaterEditCached(
                    floater = floater,
                    priority = floater.priority,
                    listId = listId,
                    mutationId = UUID.randomUUID().toString(),
                    timestampEpochMs = timestampMs,
                )
            }
        }
    }

    /**
     * One cache write, one widget refresh, one sync pass — the whole point of
     * this class. The sync replays the mutations the transform just enqueued,
     * one HTTP call per row, and drops each one as it lands.
     */
    private suspend fun applyBulk(
        transform: (OfflineSyncState) -> OfflineSyncState,
    ) {
        cacheManager.updateOfflineState(transform)
        refreshWidgetsNow()
        if (syncManager.isLocalMode()) return
        syncManager.syncCachedData(force = true, replayPendingMutations = true)
    }

    /**
     * One call repaints every placed widget instance with its OWN kind — there is no longer a
     * per-kind refresher to pick between, and picking wrong is exactly what left a widget rendering
     * as the wrong one. `NonCancellable` because the repaint must still land when the caller's
     * scope is torn down mid-write.
     */
    private suspend fun refreshWidgetsNow() {
        withContext(NonCancellable) {
            runCatching { widgetRefresher.refreshNow() }
        }
    }
}

// --- Pure cache transforms -------------------------------------------------
//
// Shared by the single-item methods on TodoRepository and the batched ones
// above, so a bulk action can never drift from the action it repeats. They take
// and return an OfflineSyncState and nothing else, which is what makes them
// testable without Room, Hilt or a dispatcher (see BulkTaskCacheTest).

/** Merges two staged snapshots so one batch restores through a single undo. */
internal operator fun StagedTodoDeletion.plus(other: StagedTodoDeletion): StagedTodoDeletion =
    StagedTodoDeletion(
        removedTodos = removedTodos + other.removedTodos,
        removedCompletedItems = removedCompletedItems + other.removedCompletedItems,
        removedPendingMutations = removedPendingMutations + other.removedPendingMutations,
    )

/** Floater counterpart of the staged-snapshot merge above. */
internal operator fun StagedFloaterDeletion.plus(
    other: StagedFloaterDeletion,
): StagedFloaterDeletion = StagedFloaterDeletion(
    removedFloaters = removedFloaters + other.removedFloaters,
    removedCompletedFloaters = removedCompletedFloaters + other.removedCompletedFloaters,
    removedPendingMutations = removedPendingMutations + other.removedPendingMutations,
)

/**
 * Prune-only half of a delete: removes the task, its completed-history rows and
 * its outstanding mutations, and returns what it removed so an Undo can put the
 * exact records back. Nothing is enqueued for the server — that is
 * [OfflineSyncState.withDeletedTodoCached]'s job at commit time.
 */
internal fun OfflineSyncState.withStagedTodoDeletion(
    canonicalId: String,
    instanceDateEpochMs: Long?,
    isRecurringInstanceDelete: Boolean,
    isLocalOnly: Boolean,
): Pair<OfflineSyncState, StagedTodoDeletion> {
    fun matchesTodo(record: com.ohmz.tday.compose.core.data.CachedTodoRecord): Boolean {
        if (record.canonicalId != canonicalId) return false
        return !isRecurringInstanceDelete || record.instanceDateEpochMs == instanceDateEpochMs
    }

    fun matchesCompleted(record: CachedCompletedRecord): Boolean {
        if (record.originalTodoId != canonicalId) return false
        return !isRecurringInstanceDelete || record.instanceDateEpochMs == instanceDateEpochMs
    }

    // Mirrors the mutation pruning of [OfflineSyncState.withDeletedTodoCached]
    // minus enqueueing the DELETE record (that happens at commit time).
    fun matchesMutation(mutation: PendingMutationRecord): Boolean {
        return if (isLocalOnly) {
            !isRecurringInstanceDelete && mutation.targetId == canonicalId
        } else {
            mutation.kind == MutationKind.DELETE_TODO &&
                mutation.targetId == canonicalId &&
                mutation.instanceDateEpochMs == instanceDateEpochMs
        }
    }

    val staged = StagedTodoDeletion(
        removedTodos = todos.filter(::matchesTodo),
        removedCompletedItems = completedItems.filter(::matchesCompleted),
        removedPendingMutations = pendingMutations.filter(::matchesMutation),
    )
    val next = copy(
        todos = todos.filterNot(::matchesTodo),
        completedItems = completedItems.filterNot(::matchesCompleted),
        pendingMutations = pendingMutations.filterNot(::matchesMutation),
    )
    return next to staged
}

/** Floater counterpart of [withStagedTodoDeletion]. */
internal fun OfflineSyncState.withStagedFloaterDeletion(
    canonicalId: String,
    isLocalOnly: Boolean,
): Pair<OfflineSyncState, StagedFloaterDeletion> {
    fun matchesMutation(mutation: PendingMutationRecord): Boolean {
        return if (isLocalOnly) {
            mutation.targetId == canonicalId
        } else {
            mutation.kind == MutationKind.DELETE_FLOATER && mutation.targetId == canonicalId
        }
    }

    val staged = StagedFloaterDeletion(
        removedFloaters = floaters.filter { it.canonicalId == canonicalId },
        removedCompletedFloaters = completedFloaters
            .filter { it.originalFloaterId == canonicalId },
        removedPendingMutations = pendingMutations.filter(::matchesMutation),
    )
    val next = copy(
        floaters = floaters.filterNot { it.canonicalId == canonicalId },
        completedFloaters = completedFloaters.filterNot { it.originalFloaterId == canonicalId },
        pendingMutations = pendingMutations.filterNot(::matchesMutation),
    )
    return next to staged
}

/** Commit half of a floater delete: prune plus one queued `DELETE_FLOATER`. */
internal fun OfflineSyncState.withDeletedFloaterCached(
    canonicalId: String,
    isLocalOnly: Boolean,
    mutationId: String,
    timestampEpochMs: Long,
): OfflineSyncState {
    val prunedFloaters = floaters.filterNot { it.canonicalId == canonicalId }
    val prunedCompleted = completedFloaters.filterNot { it.originalFloaterId == canonicalId }

    if (isLocalOnly) {
        return copy(
            floaters = prunedFloaters,
            completedFloaters = prunedCompleted,
            pendingMutations = pendingMutations.filterNot { it.targetId == canonicalId },
        )
    }

    return copy(
        floaters = prunedFloaters,
        completedFloaters = prunedCompleted,
        pendingMutations = pendingMutations
            .filterNot { it.kind == MutationKind.DELETE_FLOATER && it.targetId == canonicalId } +
            PendingMutationRecord(
                mutationId = mutationId,
                kind = MutationKind.DELETE_FLOATER,
                targetId = canonicalId,
                timestampEpochMs = timestampEpochMs,
            ),
    )
}

/**
 * Marks a task complete in the cache, files its completed-history row, and
 * queues the matching complete mutation. A recurring occurrence flips only the
 * cached row for its own `instanceDate` and queues `COMPLETE_TODO_INSTANCE`, so
 * the rest of the series is untouched.
 */
internal fun OfflineSyncState.withCompletedTodoCached(
    todo: TodoItem,
    timestampEpochMs: Long,
    mutationId: String,
    completedRecordId: String,
): OfflineSyncState {
    val isOccurrence = todo.isRecurring && todo.instanceDate != null
    val updatedTodos = todos.map {
        if (it.canonicalId == todo.canonicalId) {
            if (isOccurrence) {
                if (it.instanceDateEpochMs == todo.instanceDate?.toEpochMilli()) {
                    it.copy(completed = true, updatedAtEpochMs = timestampEpochMs)
                } else {
                    it
                }
            } else {
                it.copy(completed = true, updatedAtEpochMs = timestampEpochMs)
            }
        } else {
            it
        }
    }
    val listMeta = todo.listId?.let { listId -> lists.firstOrNull { it.id == listId } }
    val completedItem = CachedCompletedRecord(
        id = completedRecordId,
        originalTodoId = todo.canonicalId,
        title = todo.title,
        description = todo.description,
        priority = todo.priority,
        dueEpochMs = todo.due?.toEpochMilli(),
        completedAtEpochMs = timestampEpochMs,
        rrule = todo.rrule,
        instanceDateEpochMs = todo.instanceDateEpochMillis,
        listId = todo.listId,
        listName = listMeta?.name,
        listColor = listMeta?.color,
    )

    return copy(
        todos = updatedTodos,
        completedItems = completedItems + completedItem,
        pendingMutations = pendingMutations + PendingMutationRecord(
            mutationId = mutationId,
            kind = if (isOccurrence) {
                MutationKind.COMPLETE_TODO_INSTANCE
            } else {
                MutationKind.COMPLETE_TODO
            },
            targetId = todo.canonicalId,
            timestampEpochMs = timestampEpochMs,
            instanceDateEpochMs = todo.instanceDateEpochMillis,
        ),
    )
}

/** Floater counterpart of [withCompletedTodoCached]; floaters never recur. */
internal fun OfflineSyncState.withCompletedFloaterCached(
    floater: TodoItem,
    timestampEpochMs: Long,
    mutationId: String,
    completedRecordId: String,
): OfflineSyncState {
    val updatedFloaters = floaters.map {
        if (it.canonicalId == floater.canonicalId) {
            it.copy(completed = true, updatedAtEpochMs = timestampEpochMs)
        } else {
            it
        }
    }
    val listMeta = floater.listId?.let { listId -> floaterLists.firstOrNull { it.id == listId } }
    val completedItem = CachedCompletedFloaterRecord(
        id = completedRecordId,
        originalFloaterId = floater.canonicalId,
        title = floater.title,
        description = floater.description,
        priority = floater.priority,
        completedAtEpochMs = timestampEpochMs,
        listId = floater.listId,
        listName = listMeta?.name,
        listColor = listMeta?.color,
    )

    return copy(
        floaters = updatedFloaters,
        completedFloaters = completedFloaters + completedItem,
        pendingMutations = pendingMutations + PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.COMPLETE_FLOATER,
            targetId = floater.canonicalId,
            timestampEpochMs = timestampEpochMs,
        ),
    )
}

/**
 * Sets a task's priority in the cache and queues one `SET_PRIORITY`. The
 * occurrence's `instanceDate` rides along so that if a recurring row ever
 * reached here it would replay through the per-occurrence prioritize route
 * rather than the series — bulk priority excludes recurring rows upstream, and
 * this keeps that from being the only thing standing between a multi-select and
 * a rewritten series.
 */
internal fun OfflineSyncState.withTodoPriorityCached(
    todo: TodoItem,
    priority: String,
    mutationId: String,
    timestampEpochMs: Long,
): OfflineSyncState {
    val canonicalId = todo.canonicalId
    val instanceDateEpochMs = todo.instanceDateEpochMillis
    return copy(
        todos = todos.map { cached ->
            val isTarget = cached.canonicalId == canonicalId &&
                (instanceDateEpochMs == null || cached.instanceDateEpochMs == instanceDateEpochMs)
            if (isTarget) {
                cached.copy(priority = priority, updatedAtEpochMs = timestampEpochMs)
            } else {
                cached
            }
        },
        pendingMutations = pendingMutations
            .filterNot {
                it.kind == MutationKind.SET_PRIORITY &&
                    it.targetId == canonicalId &&
                    it.instanceDateEpochMs == instanceDateEpochMs
            } + PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.SET_PRIORITY,
            targetId = canonicalId,
            timestampEpochMs = timestampEpochMs,
            priority = priority,
            instanceDateEpochMs = instanceDateEpochMs,
        ),
    )
}

/**
 * Moves a task between scheduled lists. The queued `UPDATE_TODO` carries the
 * row's own title, notes, priority, due and recurrence so the replayed patch
 * changes the list and nothing else; a null or blank [listId] clears it.
 */
internal fun OfflineSyncState.withTodoListCached(
    todo: TodoItem,
    listId: String?,
    mutationId: String,
    timestampEpochMs: Long,
): OfflineSyncState {
    val canonicalId = todo.canonicalId
    val instanceDateEpochMs = todo.instanceDateEpochMillis
    val normalizedListId = listId?.takeIf { it.isNotBlank() }
    return copy(
        todos = todos.map { cached ->
            val isTarget = cached.canonicalId == canonicalId &&
                (instanceDateEpochMs == null || cached.instanceDateEpochMs == instanceDateEpochMs)
            if (isTarget) {
                cached.copy(listId = normalizedListId, updatedAtEpochMs = timestampEpochMs)
            } else {
                cached
            }
        },
        pendingMutations = pendingMutations
            .filterNot {
                it.kind == MutationKind.UPDATE_TODO &&
                    it.targetId == canonicalId &&
                    it.instanceDateEpochMs == instanceDateEpochMs
            } + PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_TODO,
            targetId = canonicalId,
            timestampEpochMs = timestampEpochMs,
            title = todo.title,
            description = todo.description,
            priority = todo.priority,
            dueEpochMs = todo.due?.toEpochMilli(),
            rrule = todo.rrule,
            listId = normalizedListId,
            instanceDateEpochMs = instanceDateEpochMs,
        ),
    )
}

/**
 * The floater edit both bulk priority and bulk move go through: a whole-record
 * `UPDATE_FLOATER` rebuilt from the row with one field replaced. Sending the
 * whole record matters — the replay clears any field the mutation leaves null.
 */
internal fun OfflineSyncState.withFloaterEditCached(
    floater: TodoItem,
    priority: String,
    listId: String?,
    mutationId: String,
    timestampEpochMs: Long,
): OfflineSyncState {
    val canonicalId = floater.canonicalId
    val normalizedListId = listId?.takeIf { it.isNotBlank() }
    return copy(
        floaters = floaters.map { cached ->
            if (cached.canonicalId == canonicalId) {
                cached.copy(
                    priority = priority,
                    listId = normalizedListId,
                    updatedAtEpochMs = timestampEpochMs,
                )
            } else {
                cached
            }
        },
        pendingMutations = pendingMutations
            .filterNot {
                it.kind == MutationKind.UPDATE_FLOATER && it.targetId == canonicalId
            } + PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_FLOATER,
            targetId = canonicalId,
            timestampEpochMs = timestampEpochMs,
            title = floater.title,
            description = floater.description,
            priority = priority,
            listId = normalizedListId,
        ),
    )
}
