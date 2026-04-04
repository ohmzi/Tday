package com.ohmz.tday.compose.core.data.completed

import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.completedFromCache
import com.ohmz.tday.compose.core.data.cache.matchesCompletedRecord
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.DeleteCompletedTodoRequest
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.UpdateCompletedTodoRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
        return cacheManager.loadOfflineState().completedItems.map(::completedFromCache)
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
        val normalizedPriority = when (payload.priority.trim()) {
            "Medium" -> "Medium"
            "High" -> "High"
            else -> "Low"
        }
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
                            dueEpochMs = payload.due.toEpochMilli(),
                            rrule = payload.rrule,
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
                            dueEpochMs = payload.due.toEpochMilli(),
                            completedAtEpochMs = completed.completedAtEpochMs.takeIf { it > 0L }
                                ?: timestampMs,
                            rrule = payload.rrule,
                            listName = listMeta?.name,
                            listColor = listMeta?.color,
                        )
                    } else {
                        completed
                    }
                },
            )
        }

        requireApiBody(
            api.patchCompletedTodoByBody(
                UpdateCompletedTodoRequest(
                    id = resolvedCompletedId,
                    title = normalizedTitle,
                    description = payload.description,
                    priority = normalizedPriority,
                    due = payload.due.toString(),
                    rrule = payload.rrule,
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

        if (resolvedCompletedId.startsWith(LOCAL_COMPLETED_PREFIX)) return

        requireApiBody(
            api.deleteCompletedTodoByBody(
                DeleteCompletedTodoRequest(id = resolvedCompletedId),
            ),
            "Could not delete completed task",
        )
    }

    private suspend fun resolveCompletedServerIdForMutation(
        currentCompletedId: String,
        canonicalTodoId: String?,
        instanceDateEpochMs: Long?,
    ): String {
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
