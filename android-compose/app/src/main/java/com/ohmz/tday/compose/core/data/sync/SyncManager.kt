package com.ohmz.tday.compose.core.data.sync

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.LOCAL_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.completedToCache
import com.ohmz.tday.compose.core.data.cache.listToCache
import com.ohmz.tday.compose.core.data.cache.mapCompletedDto
import com.ohmz.tday.compose.core.data.cache.mapListDto
import com.ohmz.tday.compose.core.data.cache.mapTodoDto
import com.ohmz.tday.compose.core.data.cache.todoMergeKey
import com.ohmz.tday.compose.core.data.cache.todoToCache
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoPrioritizeRequest
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.UpdateListRequest
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.feature.widget.TodayTasksWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val secureConfigStore: SecureConfigStore,
) {
    fun hasPendingMutations(): Boolean =
        cacheManager.loadOfflineState().pendingMutations.isNotEmpty()

    suspend fun syncCachedData(
        force: Boolean = false,
        replayPendingMutations: Boolean = true,
    ): Result<Unit> = runCatching {
        cacheManager.withSyncLock {
            syncLocalCache(
                force = force,
                replayPendingMutations = replayPendingMutations,
            )
        }
        runCatching { TodayTasksWidget().updateAll(context) }
    }

    private suspend fun syncLocalCache(
        force: Boolean,
        replayPendingMutations: Boolean,
    ) {
        var state = cacheManager.loadOfflineState()
        val now = System.currentTimeMillis()
        if (force && (now - state.lastSyncAttemptEpochMs) < MIN_FORCE_SYNC_INTERVAL_MS) {
            return
        }

        val shouldReplayPendingMutations = replayPendingMutations &&
            state.pendingMutations.isNotEmpty()
        val shouldSync = force ||
            shouldReplayPendingMutations ||
            state.lastSuccessfulSyncEpochMs == 0L ||
            (now - state.lastSyncAttemptEpochMs) >= OFFLINE_RESYNC_INTERVAL_MS

        if (!shouldSync) return

        state = state.copy(lastSyncAttemptEpochMs = now)
        cacheManager.saveOfflineState(state)

        val initialPendingCount = state.pendingMutations.size
        val firstRemote = fetchRemoteSnapshot()

        if (initialPendingCount == 0 || !shouldReplayPendingMutations) {
            var mergedWithoutMutations = mergeRemoteWithLocal(
                localState = state,
                remote = firstRemote,
            )
            if (replayPendingMutations && mergedWithoutMutations.pendingMutations.isNotEmpty()) {
                val afterPending = applyPendingMutations(
                    initialState = mergedWithoutMutations,
                    remoteSnapshot = firstRemote,
                )
                val shouldRefetchRemote =
                    afterPending.pendingMutations.size < mergedWithoutMutations.pendingMutations.size
                val latestRemote = if (shouldRefetchRemote) fetchRemoteSnapshot() else firstRemote
                mergedWithoutMutations = mergeRemoteWithLocal(
                    localState = afterPending,
                    remote = latestRemote,
                )
            }
            cacheManager.saveOfflineState(
                mergedWithoutMutations.copy(
                    lastSyncAttemptEpochMs = now,
                    lastSuccessfulSyncEpochMs = now,
                ),
            )
            return
        }

        val afterPending = applyPendingMutations(state, firstRemote)
        cacheManager.saveOfflineState(afterPending.copy(lastSyncAttemptEpochMs = now))
        val shouldRefetchRemote = afterPending.pendingMutations.size < initialPendingCount
        val latestRemote = if (shouldRefetchRemote) fetchRemoteSnapshot() else firstRemote
        val mergedState = mergeRemoteWithLocal(
            localState = afterPending,
            remote = latestRemote,
        ).copy(
            lastSyncAttemptEpochMs = now,
            lastSuccessfulSyncEpochMs = now,
        )

        cacheManager.saveOfflineState(mergedState)
    }

    private suspend fun fetchRemoteSnapshot(): RemoteSnapshot {
        val todos = requireApiBody(
            api.getTodos(timeline = true),
            "Could not load timeline tasks",
        ).todos.map(::mapTodoDto)

        val completed = requireApiBody(
            api.getCompletedTodos(),
            "Could not load completed tasks",
        ).completedTodos.map(::mapCompletedDto)

        val lists = requireApiBody(
            api.getLists(),
            "Could not load lists",
        ).lists.map { mapListDto(it, iconFallback = secureConfigStore.getListIcon(it.id)) }

        val aiSummaryEnabled = runCatching {
            requireApiBody(
                api.getAppSettings(),
                "Could not load app settings",
            ).aiSummaryEnabled
        }.getOrElse {
            cacheManager.loadOfflineState().aiSummaryEnabled
        }

        return RemoteSnapshot(
            todos = todos,
            completedItems = completed,
            lists = lists,
            aiSummaryEnabled = aiSummaryEnabled,
        )
    }

    private suspend fun applyPendingMutations(
        initialState: OfflineSyncState,
        remoteSnapshot: RemoteSnapshot,
    ): OfflineSyncState {
        if (initialState.pendingMutations.isEmpty()) return initialState

        var state = initialState
        val pending = initialState.pendingMutations.sortedBy { it.timestampEpochMs }.toMutableList()
        val resolvedTodoIds = mutableMapOf<String, String>()
        val resolvedListIds = mutableMapOf<String, String>()
        val remaining = mutableListOf<PendingMutationRecord>()

        for (mutation in pending) {
            val resolvedTargetId = resolveTargetId(
                targetId = mutation.targetId,
                todoIdMap = resolvedTodoIds,
                listIdMap = resolvedListIds,
            )

            val success = runCatching {
                when (mutation.kind) {
                    MutationKind.CREATE_LIST -> {
                        val localListId = mutation.targetId ?: return@runCatching false
                        if (!localListId.startsWith(LOCAL_LIST_PREFIX)) return@runCatching true
                        val localListExists = state.lists.any { it.id == localListId }
                        if (!localListExists) return@runCatching true
                        val response = requireApiBody(
                            api.createList(
                                CreateListRequest(
                                    name = mutation.name?.trim().orEmpty(),
                                    color = mutation.color,
                                    iconKey = mutation.iconKey,
                                ),
                            ),
                            "Could not create list",
                        )
                        val serverListId = response.list?.id ?: return@runCatching false
                        resolvedListIds[localListId] = serverListId
                        state = replaceLocalListId(state, localListId, serverListId)
                        true
                    }

                    MutationKind.UPDATE_LIST -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_LIST_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.listUpdatedAtById[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.patchListByBody(
                                UpdateListRequest(
                                    id = targetId,
                                    name = mutation.name,
                                    color = mutation.color,
                                    iconKey = mutation.iconKey,
                                ),
                            ),
                            "Could not update list",
                        )
                        true
                    }

                    MutationKind.CREATE_TODO -> {
                        val localTodoId = mutation.targetId ?: return@runCatching false
                        if (!localTodoId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching true
                        val localTodoExists = state.todos.any { it.canonicalId == localTodoId }
                        if (!localTodoExists) return@runCatching true
                        val resolvedListId = mutation.listId?.let {
                            resolvedListIds[it] ?: it
                        }
                        if (resolvedListId != null && resolvedListId.startsWith(LOCAL_LIST_PREFIX)) {
                            return@runCatching false
                        }
                        val created = requireApiBody(
                            api.createTodo(
                                CreateTodoRequest(
                                    title = mutation.title?.trim().orEmpty(),
                                    description = mutation.description,
                                    priority = mutation.priority ?: "Low",
                                    dtstart = Instant.ofEpochMilli(
                                        mutation.dtstartEpochMs ?: System.currentTimeMillis(),
                                    ).toString(),
                                    due = Instant.ofEpochMilli(
                                        mutation.dueEpochMs ?: System.currentTimeMillis(),
                                    ).toString(),
                                    rrule = mutation.rrule,
                                    listID = resolvedListId,
                                ),
                            ),
                            "Could not create task",
                        ).todo ?: return@runCatching false
                        val createdTodo = mapTodoDto(created)
                        resolvedTodoIds[localTodoId] = createdTodo.canonicalId
                        state = replaceLocalTodoId(state, localTodoId, createdTodo.canonicalId)
                        true
                    }

                    MutationKind.UPDATE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true

                        val resolvedListId = mutation.listId?.let { resolvedListIds[it] ?: it }
                        if (!resolvedListId.isNullOrBlank() && resolvedListId.startsWith(LOCAL_LIST_PREFIX)) {
                            return@runCatching false
                        }

                        val remoteTodo = remoteSnapshot.todos.firstOrNull { it.canonicalId == targetId }
                        val descriptionForApi = mutation.description
                            ?: if (remoteTodo?.description != null) "" else null
                        val rruleForApi = mutation.rrule
                            ?: if (!remoteTodo?.rrule.isNullOrBlank()) "" else null
                        val listIdForApi = resolvedListId
                            ?: if (!remoteTodo?.listId.isNullOrBlank()) "" else null

                        requireApiBody(
                            api.patchTodoByBody(
                                UpdateTodoRequest(
                                    id = targetId,
                                    title = mutation.title,
                                    description = descriptionForApi,
                                    pinned = mutation.pinned,
                                    priority = mutation.priority,
                                    dtstart = mutation.dtstartEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                                    due = mutation.dueEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                                    rrule = rruleForApi,
                                    listID = listIdForApi,
                                    dateChanged = true,
                                    rruleChanged = true,
                                    instanceDate = mutation.instanceDateEpochMs?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    },
                                ),
                            ),
                            "Could not update task",
                        )
                        true
                    }

                    MutationKind.DELETE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching true

                        val instanceDateEpochMs = mutation.instanceDateEpochMs
                        if (instanceDateEpochMs != null) {
                            requireApiBody(
                                api.deleteTodoInstanceByBody(
                                    com.ohmz.tday.compose.core.model.TodoInstanceDeleteRequest(
                                        todoId = targetId,
                                        instanceDate = Instant.ofEpochMilli(instanceDateEpochMs).toString(),
                                    ),
                                ),
                                "Could not delete recurring task instance",
                            )
                            return@runCatching true
                        }

                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.deleteTodoByBody(DeleteTodoRequest(id = targetId)),
                            "Could not delete task",
                        )
                        true
                    }

                    MutationKind.SET_PINNED -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.patchTodoByBody(
                                UpdateTodoRequest(id = targetId, pinned = mutation.pinned ?: false),
                            ),
                            "Could not update pin",
                        )
                        true
                    }

                    MutationKind.SET_PRIORITY -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true

                        val priority = mutation.priority ?: "Low"
                        val instanceDateEpochMs = mutation.instanceDateEpochMs
                        if (instanceDateEpochMs != null) {
                            requireApiBody(
                                api.prioritizeTodoByBody(
                                    TodoPrioritizeRequest(
                                        id = targetId,
                                        priority = priority,
                                        instanceDate = Instant.ofEpochMilli(instanceDateEpochMs).toString(),
                                    ),
                                ),
                                "Could not update priority",
                            )
                        } else {
                            requireApiBody(
                                api.patchTodoByBody(
                                    UpdateTodoRequest(id = targetId, priority = priority),
                                ),
                                "Could not update priority",
                            )
                        }
                        true
                    }

                    MutationKind.COMPLETE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.completeTodoByBody(TodoCompleteRequest(id = targetId)),
                            "Could not complete task",
                        )
                        true
                    }

                    MutationKind.COMPLETE_TODO_INSTANCE -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        requireApiBody(
                            api.completeTodoByBody(
                                TodoCompleteRequest(
                                    id = targetId,
                                    instanceDate = mutation.instanceDateEpochMs?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    },
                                ),
                            ),
                            "Could not complete recurring task",
                        )
                        true
                    }

                    MutationKind.UNCOMPLETE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        requireApiBody(
                            api.uncompleteTodoByBody(
                                TodoUncompleteRequest(
                                    id = targetId,
                                    instanceDate = mutation.instanceDateEpochMs?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    },
                                ),
                            ),
                            "Could not restore task",
                        )
                        true
                    }
                }
            }.getOrElse { error ->
                if (isLikelyConnectivityIssue(error)) {
                    remaining.add(resolveLatestMutationSnapshot(state, mutation))
                    remaining.addAll(
                        pending
                            .dropWhile { it.mutationId != mutation.mutationId }
                            .drop(1)
                            .map { queued -> resolveLatestMutationSnapshot(state, queued) },
                    )
                    cacheManager.saveOfflineState(state.copy(pendingMutations = remaining))
                    return state.copy(pendingMutations = remaining)
                }
                if (isLikelyUnrecoverableMutationError(error, mutation)) {
                    Log.w(
                        LOG_TAG,
                        "Dropping unrecoverable mutation kind=${mutation.kind} target=${mutation.targetId}: ${error.message}",
                    )
                    true
                } else {
                    false
                }
            }

            if (!success) {
                remaining.add(resolveLatestMutationSnapshot(state, mutation))
            }
        }

        return state.copy(pendingMutations = remaining)
    }

    private fun mergeRemoteWithLocal(
        localState: OfflineSyncState,
        remote: RemoteSnapshot,
    ): OfflineSyncState {
        val remoteTodos = remote.todos.map(::todoToCache)
        val remoteLists = remote.lists.map(::listToCache)
        val remoteCompleted = remote.completedItems.map(::completedToCache).toMutableList()

        val pendingTodoCanonicalIds = localState.pendingMutations
            .filter { it.kind.affectsTodo() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingListIds = localState.pendingMutations
            .filter { it.kind == MutationKind.CREATE_LIST || it.kind == MutationKind.UPDATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingDeleteAllCanonicals = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_TODO && it.instanceDateEpochMs == null }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingDeleteSpecificKeys = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_TODO && it.instanceDateEpochMs != null }
            .mapNotNull { mutation ->
                mutation.targetId?.let { targetId ->
                    todoMergeKey(targetId, mutation.instanceDateEpochMs)
                }
            }
            .toSet()

        val localTodoByKey = localState.todos.associateBy(::todoMergeKey)
        val remoteTodoByKey = remoteTodos.associateBy(::todoMergeKey)
        val mergedTodos = mutableListOf<CachedTodoRecord>()
        val allTodoKeys = LinkedHashSet<String>().apply {
            addAll(remoteTodoByKey.keys)
            addAll(localTodoByKey.keys)
        }

        allTodoKeys.forEach { key ->
            val localTodo = localTodoByKey[key]
            val remoteTodo = remoteTodoByKey[key]

            if (remoteTodo != null) {
                val blockedByPendingDelete =
                    pendingDeleteAllCanonicals.contains(remoteTodo.canonicalId) ||
                        pendingDeleteSpecificKeys.contains(key)
                if (blockedByPendingDelete) return@forEach
            }

            if (remoteTodo == null && localTodo != null) {
                val hasPendingLocalMutation = pendingTodoCanonicalIds.contains(localTodo.canonicalId)
                val isUnsyncedLocalTodo = localTodo.canonicalId.startsWith(LOCAL_TODO_PREFIX)
                if (!hasPendingLocalMutation && !isUnsyncedLocalTodo) return@forEach
            }

            val merged = when {
                localTodo != null && remoteTodo != null -> {
                    if (shouldPreferLocalTodo(localTodo, remoteTodo, pendingTodoCanonicalIds)) {
                        localTodo
                    } else {
                        remoteTodo
                    }
                }
                localTodo != null -> localTodo
                remoteTodo != null -> remoteTodo
                else -> null
            }
            if (merged != null) mergedTodos.add(merged)
        }

        pendingTodoCanonicalIds.forEach { canonicalId ->
            val localCompletedForTodo = localState.completedItems.filter { it.originalTodoId == canonicalId }
            if (localCompletedForTodo.isNotEmpty()) {
                remoteCompleted.removeAll { it.originalTodoId == canonicalId }
                remoteCompleted.addAll(localCompletedForTodo)
            }
        }

        val localListById = localState.lists.associateBy { it.id }
        val remoteListById = remoteLists.associateBy { it.id }
        val mergedLists = mutableListOf<CachedListRecord>()
        val allListIds = LinkedHashSet<String>().apply {
            addAll(remoteListById.keys)
            addAll(localListById.keys)
        }

        allListIds.forEach { listId ->
            val localList = localListById[listId]
            val remoteList = remoteListById[listId]
            val merged = when {
                localList != null && remoteList != null -> {
                    if (
                        pendingListIds.contains(listId) ||
                        localList.updatedAtEpochMs > remoteList.updatedAtEpochMs
                    ) {
                        localList
                    } else {
                        remoteList
                    }
                }
                localList != null -> localList
                remoteList != null -> remoteList
                else -> null
            }
            if (merged != null) mergedLists.add(merged)
        }

        val todoCountByList = mergedTodos
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()
        val normalizedLists = mergedLists.map {
            it.copy(todoCount = todoCountByList[it.id] ?: 0)
        }

        val dataMergedState = localState.copy(
            todos = mergedTodos,
            completedItems = remoteCompleted,
            lists = normalizedLists,
            aiSummaryEnabled = remote.aiSummaryEnabled,
        )
        val localWinsMutations = buildLocalWinsMutations(
            mergedState = dataMergedState,
            remote = remote,
        )
        if (localWinsMutations.isEmpty()) return dataMergedState

        return dataMergedState.copy(
            pendingMutations = mergePendingMutations(
                existing = dataMergedState.pendingMutations,
                generated = localWinsMutations,
            ),
        )
    }

    private fun buildLocalWinsMutations(
        mergedState: OfflineSyncState,
        remote: RemoteSnapshot,
    ): List<PendingMutationRecord> {
        val existingPending = mergedState.pendingMutations
        val pendingTodoCanonicalIds = existingPending
            .filter { it.kind.affectsTodo() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingListIds = existingPending
            .filter { it.kind == MutationKind.CREATE_LIST || it.kind == MutationKind.UPDATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingLocalListCreates = existingPending
            .filter { it.kind == MutationKind.CREATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()

        val remoteTodoByKey = remote.todos
            .map(::todoToCache)
            .associateBy(::todoMergeKey)
        val remoteListById = remote.lists
            .map(::listToCache)
            .associateBy { it.id }

        val generated = mutableListOf<PendingMutationRecord>()

        mergedState.todos.forEach { localTodo ->
            if (localTodo.canonicalId.startsWith(LOCAL_TODO_PREFIX)) return@forEach
            if (pendingTodoCanonicalIds.contains(localTodo.canonicalId)) return@forEach

            val remoteTodo = remoteTodoByKey[todoMergeKey(localTodo)] ?: return@forEach
            if (!hasTodoMeaningfulDifferences(local = localTodo, remote = remoteTodo)) return@forEach
            val localUpdatedAt = localTodo.updatedAtEpochMs
            val remoteUpdatedAt = remoteTodo.updatedAtEpochMs
            if (localUpdatedAt <= 0L || localUpdatedAt <= remoteUpdatedAt) return@forEach

            val mutation = if (localTodo.completed != remoteTodo.completed) {
                if (localTodo.completed) {
                    PendingMutationRecord(
                        mutationId = UUID.randomUUID().toString(),
                        kind = if (localTodo.instanceDateEpochMs != null) {
                            MutationKind.COMPLETE_TODO_INSTANCE
                        } else {
                            MutationKind.COMPLETE_TODO
                        },
                        targetId = localTodo.canonicalId,
                        timestampEpochMs = localUpdatedAt,
                        instanceDateEpochMs = localTodo.instanceDateEpochMs,
                    )
                } else {
                    PendingMutationRecord(
                        mutationId = UUID.randomUUID().toString(),
                        kind = MutationKind.UNCOMPLETE_TODO,
                        targetId = localTodo.canonicalId,
                        timestampEpochMs = localUpdatedAt,
                        instanceDateEpochMs = localTodo.instanceDateEpochMs,
                    )
                }
            } else {
                val localListId = localTodo.listId
                if (!localListId.isNullOrBlank() &&
                    localListId.startsWith(LOCAL_LIST_PREFIX) &&
                    !pendingLocalListCreates.contains(localListId)
                ) {
                    return@forEach
                }
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = MutationKind.UPDATE_TODO,
                    targetId = localTodo.canonicalId,
                    timestampEpochMs = localUpdatedAt,
                    title = localTodo.title,
                    description = localTodo.description,
                    priority = localTodo.priority,
                    pinned = localTodo.pinned,
                    dtstartEpochMs = localTodo.dtstartEpochMs,
                    dueEpochMs = localTodo.dueEpochMs,
                    rrule = localTodo.rrule,
                    listId = localTodo.listId,
                    instanceDateEpochMs = localTodo.instanceDateEpochMs,
                )
            }
            generated.add(mutation)
        }

        mergedState.lists.forEach { localList ->
            if (localList.id.startsWith(LOCAL_LIST_PREFIX)) return@forEach
            if (pendingListIds.contains(localList.id)) return@forEach

            val remoteList = remoteListById[localList.id] ?: return@forEach
            if (!hasListMeaningfulDifferences(local = localList, remote = remoteList)) return@forEach
            val localUpdatedAt = localList.updatedAtEpochMs
            val remoteUpdatedAt = remoteList.updatedAtEpochMs
            if (localUpdatedAt <= 0L || localUpdatedAt <= remoteUpdatedAt) return@forEach

            generated.add(
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = MutationKind.UPDATE_LIST,
                    targetId = localList.id,
                    timestampEpochMs = localUpdatedAt,
                    name = localList.name,
                    color = localList.color,
                    iconKey = localList.iconKey,
                ),
            )
        }

        return generated
    }

    private fun shouldPreferLocalTodo(
        localTodo: CachedTodoRecord,
        remoteTodo: CachedTodoRecord,
        pendingTodoCanonicalIds: Set<String>,
    ): Boolean {
        if (pendingTodoCanonicalIds.contains(localTodo.canonicalId)) return true
        return localTodo.updatedAtEpochMs > remoteTodo.updatedAtEpochMs
    }

    private fun hasTodoMeaningfulDifferences(
        local: CachedTodoRecord,
        remote: CachedTodoRecord,
    ): Boolean {
        return local.title != remote.title ||
            local.description != remote.description ||
            local.priority != remote.priority ||
            local.dtstartEpochMs != remote.dtstartEpochMs ||
            local.dueEpochMs != remote.dueEpochMs ||
            local.rrule != remote.rrule ||
            local.instanceDateEpochMs != remote.instanceDateEpochMs ||
            local.pinned != remote.pinned ||
            local.completed != remote.completed ||
            local.listId != remote.listId
    }

    private fun hasListMeaningfulDifferences(
        local: CachedListRecord,
        remote: CachedListRecord,
    ): Boolean {
        return local.name != remote.name ||
            local.color != remote.color ||
            local.iconKey != remote.iconKey
    }

    private fun mergePendingMutations(
        existing: List<PendingMutationRecord>,
        generated: List<PendingMutationRecord>,
    ): List<PendingMutationRecord> {
        if (generated.isEmpty()) return existing
        val merged = existing.toMutableList()
        generated.forEach { candidate ->
            val replaceIndex = merged.indexOfFirst { existingMutation ->
                shouldReplacePendingMutation(existing = existingMutation, candidate = candidate)
            }
            if (replaceIndex >= 0) {
                merged[replaceIndex] = candidate
            } else {
                merged.add(candidate)
            }
        }
        return merged.sortedBy { it.timestampEpochMs }
    }

    private fun shouldReplacePendingMutation(
        existing: PendingMutationRecord,
        candidate: PendingMutationRecord,
    ): Boolean {
        if (existing.kind != candidate.kind) return false
        if (existing.targetId != candidate.targetId) return false
        return existing.instanceDateEpochMs == candidate.instanceDateEpochMs
    }

    private fun MutationKind.affectsTodo(): Boolean {
        return this == MutationKind.CREATE_TODO ||
            this == MutationKind.UPDATE_TODO ||
            this == MutationKind.DELETE_TODO ||
            this == MutationKind.SET_PINNED ||
            this == MutationKind.SET_PRIORITY ||
            this == MutationKind.COMPLETE_TODO ||
            this == MutationKind.COMPLETE_TODO_INSTANCE ||
            this == MutationKind.UNCOMPLETE_TODO
    }

    private fun replaceLocalListId(
        state: OfflineSyncState,
        localListId: String,
        serverListId: String,
    ): OfflineSyncState {
        return state.copy(
            lists = state.lists.map {
                if (it.id == localListId) it.copy(id = serverListId) else it
            },
            todos = state.todos.map {
                if (it.listId == localListId) it.copy(listId = serverListId) else it
            },
            pendingMutations = state.pendingMutations.map {
                it.copy(
                    targetId = if (it.targetId == localListId) serverListId else it.targetId,
                    listId = if (it.listId == localListId) serverListId else it.listId,
                )
            },
        )
    }

    private fun replaceLocalTodoId(
        state: OfflineSyncState,
        localTodoId: String,
        serverTodoId: String,
    ): OfflineSyncState {
        return state.copy(
            todos = state.todos.map {
                if (it.canonicalId == localTodoId) {
                    it.copy(
                        id = if (it.id == localTodoId) serverTodoId else it.id,
                        canonicalId = serverTodoId,
                    )
                } else {
                    it
                }
            },
            pendingMutations = state.pendingMutations.map {
                if (it.targetId == localTodoId) it.copy(targetId = serverTodoId) else it
            },
        )
    }

    private fun resolveTargetId(
        targetId: String?,
        todoIdMap: Map<String, String>,
        listIdMap: Map<String, String>,
    ): String? {
        if (targetId == null) return null
        return todoIdMap[targetId] ?: listIdMap[targetId] ?: targetId
    }

    private fun resolveLatestMutationSnapshot(
        state: OfflineSyncState,
        mutation: PendingMutationRecord,
    ): PendingMutationRecord {
        return state.pendingMutations.firstOrNull { it.mutationId == mutation.mutationId } ?: mutation
    }

    private data class RemoteSnapshot(
        val todos: List<TodoItem>,
        val completedItems: List<CompletedItem>,
        val lists: List<ListSummary>,
        val aiSummaryEnabled: Boolean,
    ) {
        val todoUpdatedAtByCanonical: Map<String, Long> = todos
            .groupBy { it.canonicalId }
            .mapValues { (_, entries) ->
                entries.maxOfOrNull { it.updatedAt?.toEpochMilli() ?: 0L } ?: 0L
            }

        val listUpdatedAtById: Map<String, Long> = lists
            .groupBy { it.id }
            .mapValues { (_, entries) ->
                entries.maxOfOrNull { it.updatedAt?.toEpochMilli() ?: 0L } ?: 0L
            }
    }

    private companion object {
        const val LOG_TAG = "SyncManager"
        const val OFFLINE_RESYNC_INTERVAL_MS = 5 * 60 * 1000L
        const val MIN_FORCE_SYNC_INTERVAL_MS = 1_200L
    }
}
