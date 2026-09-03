package com.ohmz.tday.compose.core.data.completed

import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.completedFloaterFromCache
import com.ohmz.tday.compose.core.data.cache.completedFromCache
import com.ohmz.tday.compose.core.data.cache.floaterToCache
import com.ohmz.tday.compose.core.data.cache.mapFloaterDto
import com.ohmz.tday.compose.core.data.cache.matchesCompletedRecord
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.DeleteCompletedFloaterRequest
import com.ohmz.tday.compose.core.model.DeleteCompletedTodoRequest
import com.ohmz.tday.compose.core.model.FloaterUncompleteRequest
import com.ohmz.tday.compose.core.model.FloaterUncompleteResponse
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.UpdateCompletedFloaterRequest
import com.ohmz.tday.compose.core.model.UpdateCompletedTodoRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of [CompletedRepository.uncompleteFloater], enough for the ViewModel to
 * give the two response cases distinct feedback (see
 * docs/design/completed-floaters-durability.md §5.2): [listRecreated] means the
 * floater landed somewhere other than the list it was completed from — including
 * a second undo that converges onto a list an earlier undo already recreated —
 * not "this call literally inserted a new list."
 */
data class FloaterUncompleteOutcome(
    val listRecreated: Boolean,
    val listName: String?,
)

@Singleton
class CompletedRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val syncManager: SyncManager,
) {
    suspend fun fetchCompletedItems(): List<CompletedItem> {
        return cacheManager.loadOfflineState().completedItems.map(::completedFromCache)
    }

    fun fetchCompletedItemsSnapshot(): List<CompletedItem> {
        return cacheManager.loadOfflineStateBlocking().completedItems.map(::completedFromCache)
    }

    suspend fun fetchCompletedFloaterItems(): List<CompletedItem> {
        return cacheManager.loadOfflineState().completedFloaters.map(::completedFloaterFromCache)
    }

    fun fetchCompletedFloaterItemsSnapshot(): List<CompletedItem> {
        return cacheManager.loadOfflineStateBlocking().completedFloaters.map(::completedFloaterFromCache)
    }

    suspend fun uncomplete(item: CompletedItem) {
        val originalTodoId = item.originalTodoId
            ?: throw IllegalStateException("Completed todo is missing original todo id")
        val timestampMs = System.currentTimeMillis()
        val instanceDateEpochMs = item.instanceDate?.toEpochMilli()
        val mutationId = UUID.randomUUID().toString()

        cacheManager.updateOfflineState { state ->
            val updatedTodos = state.todos.map {
                if (it.canonicalId == originalTodoId) {
                    if (instanceDateEpochMs != null) {
                        if (it.instanceDateEpochMs == instanceDateEpochMs) {
                            it.copy(completed = false, updatedAtEpochMs = timestampMs)
                        } else {
                            it
                        }
                    } else {
                        it.copy(completed = false, updatedAtEpochMs = timestampMs)
                    }
                } else {
                    it
                }
            }
            state.copy(
                todos = updatedTodos,
                completedItems = state.completedItems.filterNot { it.id == item.id },
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.UNCOMPLETE_TODO,
                    targetId = originalTodoId,
                    timestampEpochMs = timestampMs,
                    instanceDateEpochMs = instanceDateEpochMs,
                ),
            )
        }

        if (syncManager.isLocalMode()) return

        if (originalTodoId.startsWith(LOCAL_TODO_PREFIX)) return

        runCatching {
            requireApiBody(
                api.uncompleteTodoByBody(
                    TodoUncompleteRequest(
                        id = originalTodoId,
                        instanceDate = instanceDateEpochMs?.let {
                            java.time.Instant.ofEpochMilli(it).toString()
                        },
                    ),
                ),
                "Could not restore task",
            )
        }.onSuccess {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun updateCompletedTodo(item: CompletedItem, payload: CreateTaskPayload) {
        val canonicalId = item.originalTodoId ?: return
        val instanceDateEpochMs = item.instanceDate?.toEpochMilli()
        val normalizedTitle = payload.title.trim()
        if (normalizedTitle.isBlank()) return
        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val timestampMs = System.currentTimeMillis()
        val resolvedCompletedId = resolveCompletedServerIdForMutation(
            currentCompletedId = item.id,
            canonicalTodoId = canonicalId,
            instanceDateEpochMs = instanceDateEpochMs,
        )

        cacheManager.updateOfflineState { state ->
            val listMeta = normalizedListId?.let { id -> state.lists.firstOrNull { it.id == id } }
            state.copy(
                todos = state.todos.map { todo ->
                    if (todo.canonicalId == canonicalId) {
                        todo.copy(
                            title = normalizedTitle,
                            description = payload.description,
                            priority = normalizedPriority,
                            dueEpochMs = payload.due?.toEpochMilli(),
                            rrule = payload.rrule?.takeIf { payload.due != null },
                            listId = normalizedListId,
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        todo
                    }
                },
                completedItems = state.completedItems.map { completed ->
                    if (
                        matchesCompletedRecord(
                            record = completed,
                            itemId = item.id,
                            resolvedItemId = resolvedCompletedId,
                            canonicalTodoId = canonicalId,
                            instanceDateEpochMs = instanceDateEpochMs,
                        )
                    ) {
                        completed.copy(
                            id = resolvedCompletedId,
                            title = normalizedTitle,
                            description = payload.description,
                            priority = normalizedPriority,
                            dueEpochMs = payload.due?.toEpochMilli(),
                            completedAtEpochMs = completed.completedAtEpochMs.takeIf { it > 0L }
                                ?: timestampMs,
                            rrule = payload.rrule?.takeIf { payload.due != null },
                            listId = normalizedListId,
                            listName = listMeta?.name,
                            listColor = listMeta?.color,
                        )
                    } else {
                        completed
                    }
                },
            )
        }

        if (syncManager.isLocalMode()) return

        requireApiBody(
            api.patchCompletedTodoByBody(
                UpdateCompletedTodoRequest(
                    id = resolvedCompletedId,
                    title = normalizedTitle,
                    description = payload.description,
                    priority = normalizedPriority,
                    due = payload.due?.toString(),
                    rrule = payload.rrule?.takeIf { payload.due != null },
                    listID = normalizedListId,
                ),
            ),
            "Could not update completed task",
        )
    }

    suspend fun deleteCompletedTodo(item: CompletedItem) {
        val canonicalId = item.originalTodoId
        val instanceDateEpochMs = item.instanceDate?.toEpochMilli()
        val resolvedCompletedId = resolveCompletedServerIdForMutation(
            currentCompletedId = item.id,
            canonicalTodoId = canonicalId,
            instanceDateEpochMs = instanceDateEpochMs,
        )

        cacheManager.updateOfflineState { state ->
            state.copy(
                todos = if (canonicalId != null) {
                    state.todos.filterNot { todo -> todo.canonicalId == canonicalId }
                } else {
                    state.todos
                },
                completedItems = state.completedItems.filterNot { completed ->
                    matchesCompletedRecord(
                        record = completed,
                        itemId = item.id,
                        resolvedItemId = resolvedCompletedId,
                        canonicalTodoId = canonicalId,
                        instanceDateEpochMs = instanceDateEpochMs,
                    )
                },
                pendingMutations = if (canonicalId != null) {
                    state.pendingMutations.filterNot { mutation -> mutation.targetId == canonicalId }
                } else {
                    state.pendingMutations
                },
            )
        }

        if (syncManager.isLocalMode()) return

        if (resolvedCompletedId.startsWith(LOCAL_COMPLETED_PREFIX)) return

        requireApiBody(
            api.deleteCompletedTodoByBody(
                DeleteCompletedTodoRequest(id = resolvedCompletedId),
            ),
            "Could not delete completed task",
        )
    }

    /**
     * Restores a completed floater. Unlike [uncomplete] (todos), this is not
     * optimistic-first: the browsable Completed screen's Undo is a real,
     * immediate round-trip against an already-committed item (see durability
     * doc §7), and only the server can say whether the floater's list survived
     * or had to be recreated — a local guess would need its own id-reconciliation
     * pass once the real response arrived. Local Mode and a not-yet-synced
     * `local-floater-` id have no server to ask, so they resolve the same two
     * cases (list intact vs. list gone) entirely from the local cache instead.
     */
    suspend fun uncompleteFloater(item: CompletedItem): FloaterUncompleteOutcome {
        val originalFloaterId = item.originalTodoId
            ?: throw IllegalStateException("Completed floater is missing original floater id")

        if (syncManager.isLocalMode() || originalFloaterId.startsWith(LOCAL_FLOATER_PREFIX)) {
            var outcome = FloaterUncompleteOutcome(listRecreated = false, listName = item.listName)
            cacheManager.updateOfflineState { state ->
                val (next, result) = state.withFloaterUncompletedLocally(
                    item = item,
                    originalFloaterId = originalFloaterId,
                )
                outcome = result
                next
            }
            return outcome
        }

        val response = requireApiBody(
            api.uncompleteFloaterByBody(FloaterUncompleteRequest(id = originalFloaterId)),
            "Could not restore floater",
        )
        applyFloaterUncompleteResponse(completedId = item.id, response = response)
        return FloaterUncompleteOutcome(
            listRecreated = response.listRecreated,
            listName = response.listName ?: item.listName,
        )
    }

    /** Applies the server's authoritative result of [uncompleteFloater] to the cache. */
    private suspend fun applyFloaterUncompleteResponse(
        completedId: String,
        response: FloaterUncompleteResponse,
    ) {
        val restoredFloater = response.floater
        if (restoredFloater == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(completedFloaters = state.completedFloaters.filterNot { it.id == completedId })
            }
            return
        }
        val mapped = floaterToCache(mapFloaterDto(restoredFloater))
        val timestampMs = System.currentTimeMillis()
        cacheManager.updateOfflineState { state ->
            val floaterListIds = state.floaterLists.map { it.id }.toSet()
            // response.listRecreated can converge onto a list an earlier undo
            // already inserted into this cache (§5.2) — only insert one here when
            // this device has genuinely never seen that list id before.
            val needsNewList = response.listRecreated &&
                mapped.listId != null &&
                mapped.listId !in floaterListIds
            state.copy(
                floaters = state.floaters.filterNot { it.canonicalId == mapped.canonicalId } + mapped,
                floaterLists = if (needsNewList) {
                    state.floaterLists + CachedFloaterListRecord(
                        id = mapped.listId!!,
                        name = response.listName.orEmpty(),
                        color = response.listColor,
                        iconKey = null,
                        todoCount = 0,
                        createdAtEpochMs = timestampMs,
                        updatedAtEpochMs = timestampMs,
                    )
                } else {
                    state.floaterLists
                },
                completedFloaters = state.completedFloaters.filterNot { it.id == completedId },
            )
        }
    }

    suspend fun updateCompletedFloater(item: CompletedItem, payload: CreateTaskPayload) {
        val originalFloaterId = item.originalTodoId
        val normalizedTitle = payload.title.trim()
        if (normalizedTitle.isBlank()) return
        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val timestampMs = System.currentTimeMillis()
        val resolvedCompletedId = resolveCompletedFloaterServerId(
            currentCompletedId = item.id,
            originalFloaterId = originalFloaterId,
        )

        cacheManager.updateOfflineState { state ->
            val listMeta = normalizedListId?.let { id -> state.floaterLists.firstOrNull { it.id == id } }
            state.copy(
                // The completed floater's Floaters row stays in the cache
                // (completed = true) rather than being pruned at completion time
                // — keep it in sync too, the same as updateCompletedTodo does for
                // `todos`, so an Undo after this edit restores the edited fields
                // instead of the stale pre-edit ones.
                floaters = if (originalFloaterId != null) {
                    state.floaters.map { floater ->
                        if (floater.canonicalId == originalFloaterId) {
                            floater.copy(
                                title = normalizedTitle,
                                description = payload.description,
                                priority = normalizedPriority,
                                listId = normalizedListId,
                                updatedAtEpochMs = timestampMs,
                            )
                        } else {
                            floater
                        }
                    }
                } else {
                    state.floaters
                },
                completedFloaters = state.completedFloaters.map { completed ->
                    if (completed.id == resolvedCompletedId) {
                        completed.copy(
                            title = normalizedTitle,
                            description = payload.description,
                            priority = normalizedPriority,
                            listId = normalizedListId,
                            listName = listMeta?.name ?: completed.listName,
                            listColor = listMeta?.color ?: completed.listColor,
                        )
                    } else {
                        completed
                    }
                },
            )
        }

        if (syncManager.isLocalMode()) return

        if (resolvedCompletedId.startsWith(LOCAL_COMPLETED_FLOATER_PREFIX)) return

        requireApiBody(
            api.patchCompletedFloaterByBody(
                UpdateCompletedFloaterRequest(
                    id = resolvedCompletedId,
                    title = normalizedTitle,
                    description = payload.description,
                    priority = normalizedPriority,
                    listID = normalizedListId,
                ),
            ),
            "Could not update completed floater",
        )
    }

    suspend fun deleteCompletedFloater(item: CompletedItem) {
        val originalFloaterId = item.originalTodoId
        val resolvedCompletedId = resolveCompletedFloaterServerId(
            currentCompletedId = item.id,
            originalFloaterId = originalFloaterId,
        )

        cacheManager.updateOfflineState { state ->
            state.copy(
                // A completed floater's Floaters row stays in the cache (completed
                // = true, see withCompletedFloaterCached) rather than being pruned
                // at completion time, so the permanent-delete path has to remove it
                // here — mirroring the backend fix to CompletedFloaterService.
                // deleteById(), which used to leave exactly this row orphaned
                // server-side (see durability doc §6).
                floaters = if (originalFloaterId != null) {
                    state.floaters.filterNot { it.canonicalId == originalFloaterId }
                } else {
                    state.floaters
                },
                completedFloaters = state.completedFloaters.filterNot { it.id == resolvedCompletedId },
            )
        }

        if (syncManager.isLocalMode()) return

        if (resolvedCompletedId.startsWith(LOCAL_COMPLETED_FLOATER_PREFIX)) return

        requireApiBody(
            api.deleteCompletedFloaterByBody(
                DeleteCompletedFloaterRequest(id = resolvedCompletedId),
            ),
            "Could not delete completed floater",
        )
    }

    /** Floater counterpart of [resolveCompletedServerIdForMutation] — no instanceDate to match on. */
    private suspend fun resolveCompletedFloaterServerId(
        currentCompletedId: String,
        originalFloaterId: String?,
    ): String {
        if (syncManager.isLocalMode()) {
            return currentCompletedId
        }

        if (!currentCompletedId.startsWith(LOCAL_COMPLETED_FLOATER_PREFIX)) {
            return currentCompletedId
        }
        if (originalFloaterId.isNullOrBlank() || originalFloaterId.startsWith(LOCAL_FLOATER_PREFIX)) {
            return currentCompletedId
        }

        syncManager.syncCachedData(force = true, replayPendingMutations = false)
            .onFailure { /* best effort */ }

        val refreshedState = cacheManager.loadOfflineState()
        return refreshedState.completedFloaters.firstOrNull { record ->
            record.originalFloaterId == originalFloaterId
        }?.id ?: currentCompletedId
    }

    private suspend fun resolveCompletedServerIdForMutation(
        currentCompletedId: String,
        canonicalTodoId: String?,
        instanceDateEpochMs: Long?,
    ): String {
        if (syncManager.isLocalMode()) {
            return currentCompletedId
        }

        if (!currentCompletedId.startsWith(LOCAL_COMPLETED_PREFIX)) {
            return currentCompletedId
        }
        if (canonicalTodoId.isNullOrBlank() || canonicalTodoId.startsWith(LOCAL_TODO_PREFIX)) {
            return currentCompletedId
        }

        syncManager.syncCachedData(force = true, replayPendingMutations = false)
            .onFailure { /* best effort */ }

        val refreshedState = cacheManager.loadOfflineState()
        return refreshedState.completedItems.firstOrNull { record ->
            record.originalTodoId == canonicalTodoId &&
                record.instanceDateEpochMs == instanceDateEpochMs
        }?.id ?: currentCompletedId
    }
}

/**
 * Local-cache-only equivalent of the backend's uncompleteFloater() find-or-create
 * (durability doc §2/§6), used by [CompletedRepository.uncompleteFloater] for
 * Local Mode and any not-yet-synced floater — both have no server to ask.
 *
 * Matches an already-recreated list by name (case-insensitive) instead of the
 * backend's list-id correlation: a deliberate simplification, safe for a single
 * local device with no concurrent writers, that still satisfies "several undone
 * items from the same deleted list converge onto one recreated list."
 */
internal fun OfflineSyncState.withFloaterUncompletedLocally(
    item: CompletedItem,
    originalFloaterId: String,
): Pair<OfflineSyncState, FloaterUncompleteOutcome> {
    val timestampMs = System.currentTimeMillis()

    // Case (a): the list was never deleted (or a previous undo already restored
    // it) — the Floaters row is still sitting there, completed=true.
    if (floaters.any { it.canonicalId == originalFloaterId }) {
        val restored = floaters.map {
            if (it.canonicalId == originalFloaterId) {
                it.copy(completed = false, updatedAtEpochMs = timestampMs)
            } else {
                it
            }
        }
        val next = copy(
            floaters = restored,
            completedFloaters = completedFloaters.filterNot { it.id == item.id },
        )
        return next to FloaterUncompleteOutcome(listRecreated = false, listName = item.listName)
    }

    // Case (b): the Floaters row is gone — only possible because its list was
    // deleted (list delete removes the live Floaters rows too, not just the
    // completion record; see FloaterListRepository.deleteList).
    val listName = item.listName
    val existingList = listName?.let { name ->
        floaterLists.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
    val resolvedList = existingList ?: listName?.let {
        CachedFloaterListRecord(
            id = "$LOCAL_FLOATER_LIST_PREFIX${UUID.randomUUID()}",
            name = it,
            color = item.listColor,
            iconKey = null,
            todoCount = 0,
            createdAtEpochMs = timestampMs,
            updatedAtEpochMs = timestampMs,
        )
    }
    val newFloaterId = "$LOCAL_FLOATER_PREFIX${UUID.randomUUID()}"
    val newFloater = CachedFloaterRecord(
        id = newFloaterId,
        canonicalId = newFloaterId,
        title = item.title,
        description = item.description,
        priority = item.priority,
        pinned = false,
        completed = false,
        listId = resolvedList?.id,
        updatedAtEpochMs = timestampMs,
    )
    val next = copy(
        floaterLists = if (existingList == null && resolvedList != null) {
            floaterLists + resolvedList
        } else {
            floaterLists
        },
        floaters = floaters + newFloater,
        completedFloaters = completedFloaters.filterNot { it.id == item.id },
    )
    return next to FloaterUncompleteOutcome(
        listRecreated = resolvedList != null,
        listName = resolvedList?.name ?: listName,
    )
}
