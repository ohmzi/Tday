package com.ohmz.tday.compose.core.data.sync

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.completedFloaterToCache
import com.ohmz.tday.compose.core.data.cache.completedToCache
import com.ohmz.tday.compose.core.data.cache.floaterListToCache
import com.ohmz.tday.compose.core.data.cache.floaterToCache
import com.ohmz.tday.compose.core.data.cache.listToCache
import com.ohmz.tday.compose.core.data.cache.mapCompletedDto
import com.ohmz.tday.compose.core.data.cache.mapCompletedFloaterDto
import com.ohmz.tday.compose.core.data.cache.mapFloaterDto
import com.ohmz.tday.compose.core.data.cache.mapFloaterListDto
import com.ohmz.tday.compose.core.data.cache.mapListDto
import com.ohmz.tday.compose.core.data.cache.mapTodoDto
import com.ohmz.tday.compose.core.data.cache.orderFloaterListsLikeWeb
import com.ohmz.tday.compose.core.data.cache.orderListsLikeWeb
import com.ohmz.tday.compose.core.data.cache.todoMergeKey
import com.ohmz.tday.compose.core.data.cache.todoToCache
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateFloaterListRequest
import com.ohmz.tday.compose.core.model.CreateFloaterRequest
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterListRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterRequest
import com.ohmz.tday.compose.core.model.DeleteListRequest
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.FloaterCompleteRequest
import com.ohmz.tday.compose.core.model.FloaterUncompleteRequest
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceUpdateRequest
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoPrioritizeRequest
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.UpdateFloaterListRequest
import com.ohmz.tday.compose.core.model.UpdateFloaterRequest
import com.ohmz.tday.compose.core.model.UpdateListRequest
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.feature.widget.TodayTasksWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeout
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
    private val offlineSyncFailureMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val offlineSyncFailures: SharedFlow<Unit> = offlineSyncFailureMutable.asSharedFlow()
    private val offlineSyncSuccessMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val offlineSyncSuccesses: SharedFlow<Unit> = offlineSyncSuccessMutable.asSharedFlow()

    fun hasPendingMutations(): Boolean =
        !isLocalMode() && cacheManager.loadOfflineState().pendingMutations.isNotEmpty()

    fun isLocalMode(): Boolean = secureConfigStore.isLocalMode()

    suspend fun syncCachedData(
        force: Boolean = false,
        replayPendingMutations: Boolean = true,
        notifyOfflineFailure: Boolean = true,
        connectionProbeTimeoutMs: Long? = null,
    ): Result<Unit> {
        if (isLocalMode()) {
            cacheManager.updateOfflineState { state ->
                if (state.pendingMutations.isEmpty() &&
                    state.lastSuccessfulSyncEpochMs == 0L &&
                    state.lastSyncAttemptEpochMs == 0L
                ) {
                    state
                } else {
                    state.copy(
                        lastSuccessfulSyncEpochMs = 0L,
                        lastSyncAttemptEpochMs = 0L,
                        pendingMutations = emptyList(),
                    )
                }
            }
            runCatching { TodayTasksWidget().updateAll(context) }
            return Result.success(Unit)
        }

        val result = runCatching {
            var contactedServer = false
            if (connectionProbeTimeoutMs != null) {
                verifyServerConnection(connectionProbeTimeoutMs)
                contactedServer = true
            }
            val syncedRemoteData = cacheManager.withSyncLock {
                syncLocalCache(
                    force = force,
                    replayPendingMutations = replayPendingMutations,
                )
            }
            runCatching { TodayTasksWidget().updateAll(context) }
            if (contactedServer || syncedRemoteData) {
                offlineSyncSuccessMutable.tryEmit(Unit)
            }
            Unit
        }
        val error = result.exceptionOrNull()
        if (notifyOfflineFailure && error != null && isLikelyConnectivityIssue(error)) {
            offlineSyncFailureMutable.tryEmit(Unit)
        }
        return result
    }

    private suspend fun verifyServerConnection(timeoutMs: Long) {
        withTimeout(timeoutMs) {
            requireApiBody(
                api.probeConfiguredServer(),
                "Could not connect to server",
            )
        }
    }

    private suspend fun syncLocalCache(
        force: Boolean,
        replayPendingMutations: Boolean,
    ): Boolean {
        var state = cacheManager.loadOfflineState()
        val now = System.currentTimeMillis()
        if (force && (now - state.lastSyncAttemptEpochMs) < MIN_FORCE_SYNC_INTERVAL_MS) {
            return false
        }

        val shouldReplayPendingMutations = replayPendingMutations &&
            state.pendingMutations.isNotEmpty()
        val shouldSync = force ||
            shouldReplayPendingMutations ||
            state.lastSuccessfulSyncEpochMs == 0L ||
            (now - state.lastSyncAttemptEpochMs) >= OFFLINE_RESYNC_INTERVAL_MS

        if (!shouldSync) return false

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
            return true
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
        return true
    }

    private suspend fun fetchRemoteSnapshot(): RemoteSnapshot = coroutineScope {
        val todos = async {
            requireApiBody(
                api.getTodos(timeline = true),
                "Could not load timeline tasks",
            ).todos.map(::mapTodoDto)
        }

        val completed = async {
            requireApiBody(
                api.getCompletedTodos(),
                "Could not load completed tasks",
            ).completedTodos.map(::mapCompletedDto)
        }

        val floaters = async {
            requireApiBody(
                api.getFloaters(),
                "Could not load floaters",
            ).floaters.map(::mapFloaterDto)
        }

        val completedFloaters = async {
            requireApiBody(
                api.getCompletedFloaters(),
                "Could not load completed floaters",
            ).completedFloaters.map(::mapCompletedFloaterDto)
        }

        val lists = async {
            requireApiBody(
                api.getLists(),
                "Could not load lists",
            ).lists.map { mapListDto(it, iconFallback = secureConfigStore.getListIcon(it.id)) }
        }

        val floaterLists = async {
            requireApiBody(
                api.getFloaterLists(),
                "Could not load floater lists",
            ).lists.map {
                mapFloaterListDto(
                    it,
                    iconFallback = secureConfigStore.getListIcon(it.id)
                )
            }
        }

        val aiSummaryEnabled = async {
            runCatching {
                requireApiBody(
                    api.getAppSettings(),
                    "Could not load app settings",
                ).aiSummaryEnabled
            }.getOrElse {
                cacheManager.loadOfflineState().aiSummaryEnabled
            }
        }

        RemoteSnapshot(
            todos = todos.await(),
            floaters = floaters.await(),
            completedItems = completed.await(),
            completedFloaters = completedFloaters.await(),
            lists = lists.await(),
            floaterLists = floaterLists.await(),
            aiSummaryEnabled = aiSummaryEnabled.await(),
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
        val resolvedFloaterListIds = mutableMapOf<String, String>()
        val remaining = mutableListOf<PendingMutationRecord>()

        for (mutation in pending) {
            val resolvedTargetId = resolveTargetId(
                targetId = mutation.targetId,
                todoIdMap = resolvedTodoIds,
                listIdMap = resolvedListIds + resolvedFloaterListIds,
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

                    MutationKind.DELETE_LIST -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_LIST_PREFIX)) return@runCatching true
                        requireApiBody(
                            api.deleteListByBody(DeleteListRequest(id = targetId)),
                            "Could not delete list",
                        )
                        true
                    }

                    MutationKind.CREATE_FLOATER_LIST -> {
                        val localListId = mutation.targetId ?: return@runCatching false
                        if (!localListId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return@runCatching true
                        val localListExists = state.floaterLists.any { it.id == localListId }
                        if (!localListExists) return@runCatching true
                        val response = requireApiBody(
                            api.createFloaterList(
                                CreateFloaterListRequest(
                                    name = mutation.name?.trim().orEmpty(),
                                    color = mutation.color,
                                    iconKey = mutation.iconKey,
                                ),
                            ),
                            "Could not create floater list",
                        )
                        val serverListId = response.list?.id ?: return@runCatching false
                        resolvedFloaterListIds[localListId] = serverListId
                        state = replaceLocalFloaterListId(state, localListId, serverListId)
                        true
                    }

                    MutationKind.UPDATE_FLOATER_LIST -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return@runCatching false
                        val remoteUpdatedAt =
                            remoteSnapshot.floaterListUpdatedAtById[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.patchFloaterListByBody(
                                UpdateFloaterListRequest(
                                    id = targetId,
                                    name = mutation.name,
                                    color = mutation.color,
                                    iconKey = mutation.iconKey,
                                ),
                            ),
                            "Could not update floater list",
                        )
                        true
                    }

                    MutationKind.DELETE_FLOATER_LIST -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return@runCatching true
                        requireApiBody(
                            api.deleteFloaterListByBody(DeleteFloaterListRequest(id = targetId)),
                            "Could not delete floater list",
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
                                    due = mutation.dueEpochMs?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    } ?: return@runCatching false,
                                    rrule = mutation.rrule?.takeIf { mutation.dueEpochMs != null },
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
                        val isDueOnlyMove = mutation.dueEpochMs != null &&
                                mutation.title == null &&
                                mutation.description == null &&
                                mutation.priority == null &&
                                mutation.pinned == null &&
                                mutation.completed == null &&
                                mutation.rrule == null &&
                                mutation.listId == null
                        val descriptionForApi = if (isDueOnlyMove) {
                            null
                        } else {
                            mutation.description
                                ?: if (remoteTodo?.description != null) "" else null
                        }
                        val rruleForApi = if (isDueOnlyMove) {
                            null
                        } else {
                            mutation.rrule ?: if (!remoteTodo?.rrule.isNullOrBlank()) "" else null
                        }
                        val listIdForApi = if (isDueOnlyMove) {
                            null
                        } else {
                            resolvedListId ?: if (!remoteTodo?.listId.isNullOrBlank()) "" else null
                        }

                        if (mutation.instanceDateEpochMs != null) {
                            requireApiBody(
                                api.patchTodoInstanceByBody(
                                    TodoInstanceUpdateRequest(
                                        todoId = targetId,
                                        instanceDate = Instant.ofEpochMilli(
                                            mutation.instanceDateEpochMs,
                                        ).toString(),
                                        title = mutation.title,
                                        description = descriptionForApi,
                                        priority = mutation.priority,
                                        due = mutation.dueEpochMs?.let {
                                            Instant.ofEpochMilli(it).toString()
                                        },
                                    ),
                                ),
                                "Could not update recurring task instance",
                            )
                        } else {
                            requireApiBody(
                                api.patchTodoByBody(
                                    UpdateTodoRequest(
                                        id = targetId,
                                        title = mutation.title,
                                        description = descriptionForApi,
                                        pinned = mutation.pinned,
                                        priority = mutation.priority,
                                        due = mutation.dueEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                                        rrule = rruleForApi,
                                        listID = listIdForApi,
                                        dateChanged = true,
                                        rruleChanged = if (isDueOnlyMove) null else true,
                                        instanceDate = null,
                                    ),
                                ),
                                "Could not update task",
                            )
                        }
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

                    MutationKind.CREATE_FLOATER -> {
                        val localFloaterId = mutation.targetId ?: return@runCatching false
                        if (!localFloaterId.startsWith(LOCAL_FLOATER_PREFIX)) return@runCatching true
                        val localFloaterExists =
                            state.floaters.any { it.canonicalId == localFloaterId }
                        if (!localFloaterExists) return@runCatching true
                        val resolvedListId = mutation.listId?.let {
                            resolvedFloaterListIds[it] ?: it
                        }
                        if (resolvedListId != null && resolvedListId.startsWith(
                                LOCAL_FLOATER_LIST_PREFIX
                            )
                        ) {
                            return@runCatching false
                        }
                        val created = requireApiBody(
                            api.createFloater(
                                CreateFloaterRequest(
                                    title = mutation.title?.trim().orEmpty(),
                                    description = mutation.description,
                                    priority = mutation.priority ?: "Low",
                                    listID = resolvedListId,
                                ),
                            ),
                            "Could not create floater",
                        ).floater ?: return@runCatching false
                        val createdFloater = mapFloaterDto(created)
                        resolvedTodoIds[localFloaterId] = createdFloater.canonicalId
                        state =
                            replaceLocalFloaterId(state, localFloaterId, createdFloater.canonicalId)
                        true
                    }

                    MutationKind.UPDATE_FLOATER -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return@runCatching false
                        val remoteUpdatedAt =
                            remoteSnapshot.floaterUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        val resolvedListId =
                            mutation.listId?.let { resolvedFloaterListIds[it] ?: it }
                        if (!resolvedListId.isNullOrBlank() && resolvedListId.startsWith(
                                LOCAL_FLOATER_LIST_PREFIX
                            )
                        ) {
                            return@runCatching false
                        }
                        val remoteFloater =
                            remoteSnapshot.floaters.firstOrNull { it.canonicalId == targetId }
                        val listIdForApi = resolvedListId
                            ?: if (!remoteFloater?.listId.isNullOrBlank()) "" else null
                        requireApiBody(
                            api.patchFloaterByBody(
                                UpdateFloaterRequest(
                                    id = targetId,
                                    title = mutation.title,
                                    description = mutation.description
                                        ?: if (remoteFloater?.description != null) "" else null,
                                    pinned = mutation.pinned,
                                    priority = mutation.priority,
                                    completed = mutation.completed,
                                    listID = listIdForApi,
                                ),
                            ),
                            "Could not update floater",
                        )
                        true
                    }

                    MutationKind.DELETE_FLOATER -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return@runCatching true
                        val remoteUpdatedAt =
                            remoteSnapshot.floaterUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.deleteFloaterByBody(DeleteFloaterRequest(id = targetId)),
                            "Could not delete floater",
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

                    MutationKind.COMPLETE_FLOATER -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return@runCatching false
                        val remoteUpdatedAt =
                            remoteSnapshot.floaterUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireApiBody(
                            api.completeFloaterByBody(FloaterCompleteRequest(id = targetId)),
                            "Could not complete floater",
                        )
                        true
                    }

                    MutationKind.UNCOMPLETE_FLOATER -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return@runCatching false
                        requireApiBody(
                            api.uncompleteFloaterByBody(FloaterUncompleteRequest(id = targetId)),
                            "Could not restore floater",
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
        val pendingDeletedListIds = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingDeletedFloaterListIds = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_FLOATER_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val remoteTodos = remote.todos
            .filterNot { it.listId != null && pendingDeletedListIds.contains(it.listId) }
            .map(::todoToCache)
        val remoteLists = remote.lists.map(::listToCache)
        val remoteFloaterLists = remote.floaterLists.map(::floaterListToCache)
        val remoteCompleted = remote.completedItems
            .filterNot { it.listId != null && pendingDeletedListIds.contains(it.listId) }
            .map(::completedToCache)
            .toMutableList()
        val remoteFloaters = remote.floaters
            .filterNot { it.listId != null && pendingDeletedFloaterListIds.contains(it.listId) }
            .map(::floaterToCache)
        val remoteCompletedFloaters = remote.completedFloaters
            .filterNot { it.listId != null && pendingDeletedFloaterListIds.contains(it.listId) }
            .map(::completedFloaterToCache)
            .toMutableList()

        val pendingTodoCanonicalIds = localState.pendingMutations
            .filter { it.kind.affectsTodo() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingFloaterCanonicalIds = localState.pendingMutations
            .filter { it.kind.affectsFloater() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingListIds = localState.pendingMutations
            .filter {
                it.kind == MutationKind.CREATE_LIST ||
                        it.kind == MutationKind.UPDATE_LIST ||
                        it.kind == MutationKind.DELETE_LIST
            }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingFloaterListIds = localState.pendingMutations
            .filter {
                it.kind == MutationKind.CREATE_FLOATER_LIST ||
                        it.kind == MutationKind.UPDATE_FLOATER_LIST ||
                        it.kind == MutationKind.DELETE_FLOATER_LIST
            }
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
        val pendingDeletedFloaterIds = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_FLOATER }
            .mapNotNull { it.targetId }
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

        val localFloaterById = localState.floaters.associateBy { it.canonicalId }
        val remoteFloaterById = remoteFloaters.associateBy { it.canonicalId }
        val mergedFloaters = mutableListOf<CachedFloaterRecord>()
        val allFloaterIds = LinkedHashSet<String>().apply {
            addAll(remoteFloaterById.keys)
            addAll(localFloaterById.keys)
        }

        allFloaterIds.forEach { canonicalId ->
            val localFloater = localFloaterById[canonicalId]
            val remoteFloater = remoteFloaterById[canonicalId]

            if (remoteFloater != null && pendingDeletedFloaterIds.contains(remoteFloater.canonicalId)) {
                return@forEach
            }
            if (remoteFloater == null && localFloater != null) {
                val hasPendingLocalMutation =
                    pendingFloaterCanonicalIds.contains(localFloater.canonicalId)
                val isUnsyncedLocalFloater =
                    localFloater.canonicalId.startsWith(LOCAL_FLOATER_PREFIX)
                if (!hasPendingLocalMutation && !isUnsyncedLocalFloater) return@forEach
            }

            val merged = when {
                localFloater != null && remoteFloater != null -> {
                    if (pendingFloaterCanonicalIds.contains(localFloater.canonicalId) ||
                        localFloater.updatedAtEpochMs > remoteFloater.updatedAtEpochMs
                    ) {
                        localFloater
                    } else {
                        remoteFloater
                    }
                }

                localFloater != null -> localFloater
                remoteFloater != null -> remoteFloater
                else -> null
            }
            if (merged != null) mergedFloaters.add(merged)
        }

        pendingFloaterCanonicalIds.forEach { canonicalId ->
            val localCompletedForFloater =
                localState.completedFloaters.filter { it.originalFloaterId == canonicalId }
            if (localCompletedForFloater.isNotEmpty()) {
                remoteCompletedFloaters.removeAll { it.originalFloaterId == canonicalId }
                remoteCompletedFloaters.addAll(localCompletedForFloater)
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

            if (remoteList != null && pendingDeletedListIds.contains(remoteList.id)) {
                return@forEach
            }

            if (remoteList == null && localList != null) {
                val hasPendingLocalMutation = pendingListIds.contains(localList.id)
                val isUnsyncedLocalList = localList.id.startsWith(LOCAL_LIST_PREFIX)
                if (!hasPendingLocalMutation && !isUnsyncedLocalList) return@forEach
            }

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

        val localFloaterListById = localState.floaterLists.associateBy { it.id }
        val remoteFloaterListById = remoteFloaterLists.associateBy { it.id }
        val mergedFloaterLists = mutableListOf<CachedFloaterListRecord>()
        val allFloaterListIds = LinkedHashSet<String>().apply {
            addAll(remoteFloaterListById.keys)
            addAll(localFloaterListById.keys)
        }

        allFloaterListIds.forEach { listId ->
            val localList = localFloaterListById[listId]
            val remoteList = remoteFloaterListById[listId]

            if (remoteList != null && pendingDeletedFloaterListIds.contains(remoteList.id)) {
                return@forEach
            }

            if (remoteList == null && localList != null) {
                val hasPendingLocalMutation = pendingFloaterListIds.contains(localList.id)
                val isUnsyncedLocalList = localList.id.startsWith(LOCAL_FLOATER_LIST_PREFIX)
                if (!hasPendingLocalMutation && !isUnsyncedLocalList) return@forEach
            }

            val merged = when {
                localList != null && remoteList != null -> {
                    if (
                        pendingFloaterListIds.contains(listId) ||
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
            if (merged != null) mergedFloaterLists.add(merged)
        }

        val todoCountByList = mergedTodos
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()
        val normalizedLists = orderListsLikeWeb(
            mergedLists.map {
                it.copy(todoCount = todoCountByList[it.id] ?: 0)
            },
        )
        val floaterCountByList = mergedFloaters
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()
        val normalizedFloaterLists = orderFloaterListsLikeWeb(
            mergedFloaterLists.map {
                it.copy(todoCount = floaterCountByList[it.id] ?: 0)
            },
        )

        val dataMergedState = localState.copy(
            todos = mergedTodos,
            floaters = mergedFloaters,
            completedItems = remoteCompleted,
            completedFloaters = remoteCompletedFloaters,
            lists = normalizedLists,
            floaterLists = normalizedFloaterLists,
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
        val pendingFloaterCanonicalIds = existingPending
            .filter { it.kind.affectsFloater() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingListIds = existingPending
            .filter {
                it.kind == MutationKind.CREATE_LIST ||
                        it.kind == MutationKind.UPDATE_LIST ||
                        it.kind == MutationKind.DELETE_LIST
            }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingFloaterListIds = existingPending
            .filter {
                it.kind == MutationKind.CREATE_FLOATER_LIST ||
                        it.kind == MutationKind.UPDATE_FLOATER_LIST ||
                        it.kind == MutationKind.DELETE_FLOATER_LIST
            }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingLocalListCreates = existingPending
            .filter { it.kind == MutationKind.CREATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingLocalFloaterListCreates = existingPending
            .filter { it.kind == MutationKind.CREATE_FLOATER_LIST }
            .mapNotNull { it.targetId }
            .toSet()

        val remoteTodoByKey = remote.todos
            .map(::todoToCache)
            .associateBy(::todoMergeKey)
        val remoteFloaterById = remote.floaters
            .map(::floaterToCache)
            .associateBy { it.canonicalId }
        val remoteListById = remote.lists
            .map(::listToCache)
            .associateBy { it.id }
        val remoteFloaterListById = remote.floaterLists
            .map(::floaterListToCache)
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
                    dueEpochMs = localTodo.dueEpochMs,
                    rrule = localTodo.rrule,
                    listId = localTodo.listId,
                    instanceDateEpochMs = localTodo.instanceDateEpochMs,
                )
            }
            generated.add(mutation)
        }

        mergedState.floaters.forEach { localFloater ->
            if (localFloater.canonicalId.startsWith(LOCAL_FLOATER_PREFIX)) return@forEach
            if (pendingFloaterCanonicalIds.contains(localFloater.canonicalId)) return@forEach

            val remoteFloater = remoteFloaterById[localFloater.canonicalId] ?: return@forEach
            if (!hasFloaterMeaningfulDifferences(
                    local = localFloater,
                    remote = remoteFloater
                )
            ) return@forEach
            val localUpdatedAt = localFloater.updatedAtEpochMs
            val remoteUpdatedAt = remoteFloater.updatedAtEpochMs
            if (localUpdatedAt <= 0L || localUpdatedAt <= remoteUpdatedAt) return@forEach

            val mutation = if (localFloater.completed != remoteFloater.completed) {
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = if (localFloater.completed) MutationKind.COMPLETE_FLOATER else MutationKind.UNCOMPLETE_FLOATER,
                    targetId = localFloater.canonicalId,
                    timestampEpochMs = localUpdatedAt,
                )
            } else {
                val localListId = localFloater.listId
                if (!localListId.isNullOrBlank() &&
                    localListId.startsWith(LOCAL_FLOATER_LIST_PREFIX) &&
                    !pendingLocalFloaterListCreates.contains(localListId)
                ) {
                    return@forEach
                }
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = MutationKind.UPDATE_FLOATER,
                    targetId = localFloater.canonicalId,
                    timestampEpochMs = localUpdatedAt,
                    title = localFloater.title,
                    description = localFloater.description,
                    priority = localFloater.priority,
                    pinned = localFloater.pinned,
                    completed = localFloater.completed,
                    listId = localFloater.listId,
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

        mergedState.floaterLists.forEach { localList ->
            if (localList.id.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return@forEach
            if (pendingFloaterListIds.contains(localList.id)) return@forEach

            val remoteList = remoteFloaterListById[localList.id] ?: return@forEach
            if (!hasFloaterListMeaningfulDifferences(
                    local = localList,
                    remote = remoteList
                )
            ) return@forEach
            val localUpdatedAt = localList.updatedAtEpochMs
            val remoteUpdatedAt = remoteList.updatedAtEpochMs
            if (localUpdatedAt <= 0L || localUpdatedAt <= remoteUpdatedAt) return@forEach

            generated.add(
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = MutationKind.UPDATE_FLOATER_LIST,
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
            local.dueEpochMs != remote.dueEpochMs ||
            local.rrule != remote.rrule ||
            local.instanceDateEpochMs != remote.instanceDateEpochMs ||
            local.pinned != remote.pinned ||
            local.completed != remote.completed ||
            local.listId != remote.listId
    }

    private fun hasFloaterMeaningfulDifferences(
        local: CachedFloaterRecord,
        remote: CachedFloaterRecord,
    ): Boolean {
        return local.title != remote.title ||
                local.description != remote.description ||
                local.priority != remote.priority ||
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

    private fun hasFloaterListMeaningfulDifferences(
        local: CachedFloaterListRecord,
        remote: CachedFloaterListRecord,
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

    private fun MutationKind.affectsFloater(): Boolean {
        return this == MutationKind.CREATE_FLOATER ||
                this == MutationKind.UPDATE_FLOATER ||
                this == MutationKind.DELETE_FLOATER ||
                this == MutationKind.COMPLETE_FLOATER ||
                this == MutationKind.UNCOMPLETE_FLOATER
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
            completedItems = state.completedItems.map {
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

    private fun replaceLocalFloaterListId(
        state: OfflineSyncState,
        localListId: String,
        serverListId: String,
    ): OfflineSyncState {
        return state.copy(
            floaterLists = state.floaterLists.map {
                if (it.id == localListId) it.copy(id = serverListId) else it
            },
            floaters = state.floaters.map {
                if (it.listId == localListId) it.copy(listId = serverListId) else it
            },
            completedFloaters = state.completedFloaters.map {
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

    private fun replaceLocalFloaterId(
        state: OfflineSyncState,
        localFloaterId: String,
        serverFloaterId: String,
    ): OfflineSyncState {
        return state.copy(
            floaters = state.floaters.map {
                if (it.canonicalId == localFloaterId) {
                    it.copy(
                        id = if (it.id == localFloaterId) serverFloaterId else it.id,
                        canonicalId = serverFloaterId,
                    )
                } else {
                    it
                }
            },
            pendingMutations = state.pendingMutations.map {
                if (it.targetId == localFloaterId) it.copy(targetId = serverFloaterId) else it
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
        val floaters: List<TodoItem>,
        val completedItems: List<CompletedItem>,
        val completedFloaters: List<CompletedItem>,
        val lists: List<ListSummary>,
        val floaterLists: List<ListSummary>,
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

        val floaterListUpdatedAtById: Map<String, Long> = floaterLists
            .groupBy { it.id }
            .mapValues { (_, entries) ->
                entries.maxOfOrNull { it.updatedAt?.toEpochMilli() ?: 0L } ?: 0L
            }

        val floaterUpdatedAtByCanonical: Map<String, Long> = floaters
            .groupBy { it.canonicalId }
            .mapValues { (_, entries) ->
                entries.maxOfOrNull { it.updatedAt?.toEpochMilli() ?: 0L } ?: 0L
            }
    }

    companion object {
        const val USER_REFRESH_CONNECTION_TIMEOUT_MS = 2_000L
        private const val LOG_TAG = "SyncManager"
        private const val OFFLINE_RESYNC_INTERVAL_MS = 5 * 60 * 1000L
        private const val MIN_FORCE_SYNC_INTERVAL_MS = 1_200L
    }
}
