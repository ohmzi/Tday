package com.ohmz.tday.compose.core.data.sync

import android.util.Log
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
import com.ohmz.tday.compose.core.data.cache.LOCAL_STEP_PREFIX
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
import com.ohmz.tday.compose.core.data.cache.replaceLocalFloaterListId
import com.ohmz.tday.compose.core.data.cache.replaceLocalListId
import com.ohmz.tday.compose.core.data.cache.todoMergeKey
import com.ohmz.tday.compose.core.data.cache.todoToCache
import com.ohmz.tday.compose.core.data.ConnectionFailureKind
import com.ohmz.tday.compose.core.data.classifyConnectionFailure
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateFloaterListRequest
import com.ohmz.tday.compose.core.model.CreateFloaterRequest
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateTaskStepRequest
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterListRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterRequest
import com.ohmz.tday.compose.core.model.DeleteListRequest
import com.ohmz.tday.compose.core.model.DeleteTaskStepRequest
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.ReorderTaskStepsRequest
import com.ohmz.tday.compose.core.model.ToggleTaskStepRequest
import com.ohmz.tday.compose.core.model.FloaterCompleteRequest
import com.ohmz.tday.compose.core.model.FloaterUncompleteRequest
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.PromoteFloaterRequest
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
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.feature.widget.WidgetRefresher
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
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val secureConfigStore: SecureConfigStore,
    private val widgetRefresher: WidgetRefresher,
) {
    private val offlineSyncFailureMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val offlineSyncFailures: SharedFlow<Unit> = offlineSyncFailureMutable.asSharedFlow()
    // Connectivity failures from user-initiated syncs (pull-to-refresh) carry the failure
    // kind so the app can force-show the matching toast every time, even when already offline.
    private val userInitiatedSyncFailureMutable =
        MutableSharedFlow<ConnectionFailureKind>(extraBufferCapacity = 8)
    val userInitiatedSyncFailures: SharedFlow<ConnectionFailureKind> =
        userInitiatedSyncFailureMutable.asSharedFlow()
    private val offlineSyncSuccessMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val offlineSyncSuccesses: SharedFlow<Unit> = offlineSyncSuccessMutable.asSharedFlow()

    fun hasPendingMutations(): Boolean =
        !isLocalMode() && cacheManager.loadOfflineStateBlocking().pendingMutations.isNotEmpty()

    fun isLocalMode(): Boolean = secureConfigStore.isLocalMode()

    suspend fun syncCachedData(
        force: Boolean = false,
        replayPendingMutations: Boolean = true,
        notifyOfflineFailure: Boolean = true,
        userInitiated: Boolean = false,
        connectionProbeTimeoutMs: Long? = null,
    ): Result<Unit> {
        if (isLocalMode()) {
            TdayTelemetry.addBreadcrumb("local_mode.sync_noop")
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
            refreshTaskWidgets()
            return Result.success(Unit)
        }

        val result = runCatching {
            var contactedServer = false
            if (connectionProbeTimeoutMs != null) {
                TdayTelemetry.addBreadcrumb("server.probe", data = mapOf("phase" to "sync"))
                verifyServerConnection(connectionProbeTimeoutMs)
                contactedServer = true
            }
            val syncedRemoteData = cacheManager.withSyncLock {
                syncLocalCache(
                    force = force,
                    replayPendingMutations = replayPendingMutations,
                )
            }
            refreshTaskWidgets()
            if (contactedServer || syncedRemoteData) {
                offlineSyncSuccessMutable.tryEmit(Unit)
            }
            Unit
        }
        val error = result.exceptionOrNull()
        if (error != null && isLikelyConnectivityIssue(error)) {
            if (userInitiated) {
                // Dedicated channel: force-shows the toast even when already offline.
                userInitiatedSyncFailureMutable.tryEmit(classifyConnectionFailure(error))
            } else if (notifyOfflineFailure) {
                offlineSyncFailureMutable.tryEmit(Unit)
            }
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

    private suspend fun refreshTaskWidgets() {
        runCatching { widgetRefresher.refreshNow() }
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
        if (shouldReplayPendingMutations) {
            TdayTelemetry.addBreadcrumb(
                "sync.replay",
                data = mapOf("pendingMutationCount" to state.pendingMutations.size),
            )
        }
        val shouldSync = force ||
            shouldReplayPendingMutations ||
            state.lastSuccessfulSyncEpochMs == 0L ||
            (now - state.lastSyncAttemptEpochMs) >= OFFLINE_RESYNC_INTERVAL_MS

        if (!shouldSync) return false

        state = state.copy(lastSyncAttemptEpochMs = now)
        cacheManager.saveOfflineState(state)

        // Every save below is derived from THIS pre-network snapshot, but the network
        // phase takes seconds — long enough for a concurrent writer (a widget check-off
        // is the common one) to queue a mutation the snapshot never saw. Handing the
        // cache the ids this sync is authoritative over lets it tell "this sync
        // deliberately consumed the mutation" from "someone queued it mid-flight, keep
        // it" — otherwise the save silently drops that completion.
        val syncStartPendingIds = state.pendingMutations.mapTo(HashSet()) { it.mutationId }

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
                consumedMutationIds = syncStartPendingIds,
            )
            return true
        }

        val afterPending = applyPendingMutations(state, firstRemote)
        cacheManager.saveOfflineState(
            afterPending.copy(lastSyncAttemptEpochMs = now),
            consumedMutationIds = syncStartPendingIds,
        )
        val shouldRefetchRemote = afterPending.pendingMutations.size < initialPendingCount
        val latestRemote = if (shouldRefetchRemote) fetchRemoteSnapshot() else firstRemote
        val mergedState = mergeRemoteWithLocal(
            localState = afterPending,
            remote = latestRemote,
        ).copy(
            lastSyncAttemptEpochMs = now,
            lastSuccessfulSyncEpochMs = now,
        )

        cacheManager.saveOfflineState(mergedState, consumedMutationIds = syncStartPendingIds)
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
                    api.getPreferences(),
                    "Could not load preferences",
                ).aiSummaryEnabled
            }.getOrElse {
                cacheManager.loadOfflineState().aiSummaryEnabled
            }
        }

        // Capability (configured/healthy) lives in SecureConfigStore, not Room. Refresh it
        // best-effort alongside the snapshot; failures keep the previously-stored values.
        val aiCapability = async {
            runCatching {
                val settings = requireApiBody(
                    api.getAppSettings(),
                    "Could not load app settings",
                )
                secureConfigStore.setAiSummaryConfigured(settings.aiSummaryConfigured)
                secureConfigStore.setAiSummaryHealthy(settings.aiSummaryHealthy)
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
        ).also { aiCapability.await() }
    }

    // KT-R1006 (cyclomatic complexity) is suppressed on this declaration rather than
    // fixed here. DeepSource measures this function's mutation-kind dispatch — one
    // `when` branch per MutationKind, several with their own conditional short-
    // circuits — at 176, Critical risk. That is pre-existing debt this PR only
    // marginally touches: the `if (mutation.staged) { ...; continue }` guard added
    // below (so a staged, non-replayable delete is skipped rather than sent to the
    // server) is a single extra branch, but DeepSource fingerprints an occurrence by
    // its line and reported number, so any change to a flagged function reads as
    // newly introduced regardless of size — the same reason TodoListScreen.kt
    // re-suppresses KT-R1006 at its own reduced number instead of going green.
    // Splitting this dispatcher into one handler function per MutationKind would
    // fix it properly, but is a substantially larger, behavior-preserving refactor
    // of code this PR does not otherwise need to touch — deliberately left for a
    // separate follow-up rather than rushed into a race-condition bug fix.
    private suspend fun applyPendingMutations( // skipcq: KT-R1006
        initialState: OfflineSyncState,
        remoteSnapshot: RemoteSnapshot,
    ): OfflineSyncState {
        if (initialState.pendingMutations.isEmpty()) return initialState

        var state = initialState
        // The mutation ids this replay owns; anything else in the cache was queued
        // concurrently and must survive our saves (see the bail-out save below).
        val replayStartPendingIds = initialState.pendingMutations.mapTo(HashSet()) { it.mutationId }
        val pending = initialState.pendingMutations.sortedBy { it.timestampEpochMs }.toMutableList()
        val resolvedTodoIds = mutableMapOf<String, String>()
        val resolvedListIds = mutableMapOf<String, String>()
        val resolvedFloaterListIds = mutableMapOf<String, String>()
        val remaining = mutableListOf<PendingMutationRecord>()

        for (mutation in pending) {
            if (mutation.staged) {
                // A delayed-commit list/floater-list delete still inside its undo
                // window (see PendingMutationRecord.staged): never replay it — that
                // would leak the delete to the server before Undo/commit resolves —
                // just keep it pending so mergeRemoteWithLocal's resurrection guard
                // keeps covering the list for as long as it stays staged.
                remaining.add(mutation)
                continue
            }

            val resolvedTargetId = resolveTargetId(
                targetId = mutation.targetId,
                todoIdMap = resolvedTodoIds,
                listIdMap = resolvedListIds + resolvedFloaterListIds,
            )

            val success = runCatching {
                when (mutation.kind) {
                    MutationKind.CREATE_LIST ->
                        applyCreateListMutation(mutation, state, resolvedListIds)
                            .also { state = it.second }.first

                    MutationKind.UPDATE_LIST ->
                        applyUpdateListMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.DELETE_LIST ->
                        applyDeleteListMutation(resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.CREATE_FLOATER_LIST ->
                        applyCreateFloaterListMutation(mutation, state, resolvedFloaterListIds)
                            .also { state = it.second }.first

                    MutationKind.UPDATE_FLOATER_LIST ->
                        applyUpdateFloaterListMutation(
                            mutation,
                            resolvedTargetId,
                            remoteSnapshot,
                            state,
                        ).also { state = it.second }.first

                    MutationKind.RESET_FLOATER_LIST ->
                        applyResetFloaterListMutation(resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.DELETE_FLOATER_LIST ->
                        applyDeleteFloaterListMutation(resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.CREATE_TODO ->
                        applyCreateTodoMutation(mutation, state, resolvedListIds, resolvedTodoIds)
                            .also { state = it.second }.first

                    MutationKind.UPDATE_TODO ->
                        applyUpdateTodoMutation(
                            mutation,
                            resolvedTargetId,
                            remoteSnapshot,
                            state,
                            resolvedListIds,
                        ).also { state = it.second }.first

                    MutationKind.DELETE_TODO ->
                        applyDeleteTodoMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.CREATE_FLOATER ->
                        applyCreateFloaterMutation(
                            mutation,
                            state,
                            resolvedFloaterListIds,
                            resolvedTodoIds,
                        ).also { state = it.second }.first

                    MutationKind.UPDATE_FLOATER ->
                        applyUpdateFloaterMutation(
                            mutation,
                            resolvedTargetId,
                            remoteSnapshot,
                            state,
                            resolvedFloaterListIds,
                        ).also { state = it.second }.first

                    MutationKind.DELETE_FLOATER ->
                        applyDeleteFloaterMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.SET_PINNED ->
                        applySetPinnedMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.SET_PRIORITY ->
                        applySetPriorityMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.COMPLETE_TODO ->
                        applyCompleteTodoMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.COMPLETE_TODO_INSTANCE ->
                        applyCompleteTodoInstanceMutation(mutation, resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.UNCOMPLETE_TODO ->
                        applyUncompleteTodoMutation(mutation, resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.COMPLETE_FLOATER ->
                        applyCompleteFloaterMutation(mutation, resolvedTargetId, remoteSnapshot, state)
                            .also { state = it.second }.first

                    MutationKind.UNCOMPLETE_FLOATER ->
                        applyUncompleteFloaterMutation(resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.PROMOTE_FLOATER ->
                        applyPromoteFloaterMutation(mutation, resolvedTargetId, state, resolvedTodoIds)
                            .also { state = it.second }.first

                    MutationKind.DEMOTE_TODO ->
                        applyDemoteTodoMutation(mutation, resolvedTargetId, state, resolvedTodoIds)
                            .also { state = it.second }.first

                    MutationKind.CREATE_STEP ->
                        applyCreateStepMutation(mutation, resolvedTargetId, state, resolvedTodoIds)
                            .also { state = it.second }.first

                    MutationKind.TOGGLE_STEP ->
                        applyToggleStepMutation(mutation, resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.DELETE_STEP ->
                        applyDeleteStepMutation(resolvedTargetId, state)
                            .also { state = it.second }.first

                    MutationKind.REORDER_STEPS -> {
                        val todoId = resolvedTargetId ?: return@runCatching false
                        if (todoId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val orderedIds = mutation.orderedIds.orEmpty()
                            .map { resolvedTodoIds[it] ?: it }
                            .filterNot { it.startsWith(LOCAL_STEP_PREFIX) }
                        if (orderedIds.isEmpty()) return@runCatching true
                        requireApiBody(
                            api.reorderTaskSteps(
                                ReorderTaskStepsRequest(todoId = todoId, orderedIds = orderedIds),
                            ),
                            "Could not reorder steps",
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
                    // Same concurrent-writer hazard as the caller's saves: `remaining` is
                    // built from this replay's starting snapshot, so a mutation queued
                    // mid-replay (e.g. a widget check-off) isn't in it and would be
                    // dropped. Only the ids this replay owns may be removed.
                    cacheManager.saveOfflineState(
                        state.copy(pendingMutations = remaining),
                        consumedMutationIds = replayStartPendingIds,
                    )
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

    private suspend fun applyCreateListMutation(
        mutation: PendingMutationRecord,
        state: OfflineSyncState,
        resolvedListIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        var nextState = state
        val localListId = mutation.targetId ?: return false to nextState
        if (!localListId.startsWith(LOCAL_LIST_PREFIX)) return true to nextState
        val localListExists = nextState.lists.any { it.id == localListId }
        if (!localListExists) return true to nextState
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
        val serverListId = response.list?.id ?: return false to nextState
        resolvedListIds[localListId] = serverListId
        nextState = replaceLocalListId(nextState, localListId, serverListId)
        return true to nextState
    }

    private suspend fun applyUpdateListMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_LIST_PREFIX)) return false to state
        val remoteUpdatedAt = remoteSnapshot.listUpdatedAtById[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
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
        return true to state
    }

    private suspend fun applyDeleteListMutation(
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_LIST_PREFIX)) return true to state
        requireApiBody(
            api.deleteListByBody(DeleteListRequest(id = targetId)),
            "Could not delete list",
        )
        return true to state
    }

    private suspend fun applyCreateFloaterListMutation(
        mutation: PendingMutationRecord,
        state: OfflineSyncState,
        resolvedFloaterListIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        var nextState = state
        val localListId = mutation.targetId ?: return false to nextState
        if (!localListId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return true to nextState
        val localListExists = nextState.floaterLists.any { it.id == localListId }
        if (!localListExists) return true to nextState
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
        val serverListId = response.list?.id ?: return false to nextState
        resolvedFloaterListIds[localListId] = serverListId
        nextState = replaceLocalFloaterListId(nextState, localListId, serverListId)
        return true to nextState
    }

    private suspend fun applyUpdateFloaterListMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return false to state
        val remoteUpdatedAt =
            remoteSnapshot.floaterListUpdatedAtById[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
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
        return true to state
    }

    private suspend fun applyResetFloaterListMutation(
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return false to state
        requireApiBody(
            api.resetFloaterList(targetId),
            "Could not reset floater list",
        )
        return true to state
    }

    private suspend fun applyDeleteFloaterListMutation(
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return true to state
        requireApiBody(
            api.deleteFloaterListByBody(DeleteFloaterListRequest(id = targetId)),
            "Could not delete floater list",
        )
        return true to state
    }

    private suspend fun applyCreateTodoMutation(
        mutation: PendingMutationRecord,
        state: OfflineSyncState,
        resolvedListIds: MutableMap<String, String>,
        resolvedTodoIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        var nextState = state
        val localTodoId = mutation.targetId ?: return false to nextState
        if (!localTodoId.startsWith(LOCAL_TODO_PREFIX)) return true to nextState
        val localTodoExists = nextState.todos.any { it.canonicalId == localTodoId }
        if (!localTodoExists) return true to nextState
        val resolvedListId = mutation.listId?.let {
            resolvedListIds[it] ?: it
        }
        if (resolvedListId != null && resolvedListId.startsWith(LOCAL_LIST_PREFIX)) {
            return false to nextState
        }
        val created = requireApiBody(
            api.createTodo(
                CreateTodoRequest(
                    title = mutation.title?.trim().orEmpty(),
                    description = mutation.description,
                    priority = mutation.priority ?: "Low",
                    due = mutation.dueEpochMs?.let {
                        Instant.ofEpochMilli(it).toString()
                    } ?: return false to nextState,
                    rrule = mutation.rrule?.takeIf { mutation.dueEpochMs != null },
                    listID = resolvedListId,
                ),
            ),
            "Could not create task",
        ).todo ?: return false to nextState
        val createdTodo = mapTodoDto(created)
        resolvedTodoIds[localTodoId] = createdTodo.canonicalId
        nextState = replaceLocalTodoId(nextState, localTodoId, createdTodo.canonicalId)
        return true to nextState
    }

    private suspend fun applyUpdateTodoMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
        resolvedListIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to state
        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state

        val resolvedListId = mutation.listId?.let { resolvedListIds[it] ?: it }
        if (!resolvedListId.isNullOrBlank() && resolvedListId.startsWith(LOCAL_LIST_PREFIX)) {
            return false to state
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
        return true to state
    }

    private suspend fun applyDeleteTodoMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return true to state

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
            return true to state
        }

        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
        requireApiBody(
            api.deleteTodoByBody(DeleteTodoRequest(id = targetId)),
            "Could not delete task",
        )
        return true to state
    }

    private suspend fun applyCreateFloaterMutation(
        mutation: PendingMutationRecord,
        state: OfflineSyncState,
        resolvedFloaterListIds: MutableMap<String, String>,
        resolvedTodoIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        var nextState = state
        val localFloaterId = mutation.targetId ?: return false to nextState
        if (!localFloaterId.startsWith(LOCAL_FLOATER_PREFIX)) return true to nextState
        val localFloaterExists =
            nextState.floaters.any { it.canonicalId == localFloaterId }
        if (!localFloaterExists) return true to nextState
        val resolvedListId = mutation.listId?.let {
            resolvedFloaterListIds[it] ?: it
        }
        if (resolvedListId != null && resolvedListId.startsWith(
                LOCAL_FLOATER_LIST_PREFIX
            )
        ) {
            return false to nextState
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
        ).floater ?: return false to nextState
        val createdFloater = mapFloaterDto(created)
        resolvedTodoIds[localFloaterId] = createdFloater.canonicalId
        nextState =
            replaceLocalFloaterId(nextState, localFloaterId, createdFloater.canonicalId)
        return true to nextState
    }

    private suspend fun applyUpdateFloaterMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
        resolvedFloaterListIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return false to state
        val remoteUpdatedAt =
            remoteSnapshot.floaterUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
        val resolvedListId =
            mutation.listId?.let { resolvedFloaterListIds[it] ?: it }
        if (!resolvedListId.isNullOrBlank() && resolvedListId.startsWith(
                LOCAL_FLOATER_LIST_PREFIX
            )
        ) {
            return false to state
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
        return true to state
    }

    private suspend fun applyDeleteFloaterMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return true to state
        val remoteUpdatedAt =
            remoteSnapshot.floaterUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
        requireApiBody(
            api.deleteFloaterByBody(DeleteFloaterRequest(id = targetId)),
            "Could not delete floater",
        )
        return true to state
    }

    private suspend fun applySetPinnedMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to state
        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
        requireApiBody(
            api.patchTodoByBody(
                UpdateTodoRequest(id = targetId, pinned = mutation.pinned ?: false),
            ),
            "Could not update pin",
        )
        return true to state
    }

    private suspend fun applySetPriorityMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to state
        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state

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
        return true to state
    }

    private suspend fun applyCompleteTodoMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to state
        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
        requireApiBody(
            api.completeTodoByBody(TodoCompleteRequest(id = targetId)),
            "Could not complete task",
        )
        return true to state
    }

    private suspend fun applyCompleteTodoInstanceMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to state
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
        return true to state
    }

    private suspend fun applyUncompleteTodoMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to state
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
        return true to state
    }

    private suspend fun applyCompleteFloaterMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        remoteSnapshot: RemoteSnapshot,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return false to state
        val remoteUpdatedAt =
            remoteSnapshot.floaterUpdatedAtByCanonical[targetId] ?: 0L
        if (remoteUpdatedAt > mutation.timestampEpochMs) return true to state
        requireApiBody(
            api.completeFloaterByBody(FloaterCompleteRequest(id = targetId)),
            "Could not complete floater",
        )
        return true to state
    }

    private suspend fun applyUncompleteFloaterMutation(
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val targetId = resolvedTargetId ?: return false to state
        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return false to state
        requireApiBody(
            api.uncompleteFloaterByBody(FloaterUncompleteRequest(id = targetId)),
            "Could not restore floater",
        )
        return true to state
    }

    private suspend fun applyPromoteFloaterMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        state: OfflineSyncState,
        resolvedTodoIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        var nextState = state
        // A floater that never reached the server has nothing to
        // promote yet; its CREATE_FLOATER replays first and
        // resolvedTargetId remaps us to the server id.
        val targetId = resolvedTargetId ?: return false to nextState
        if (targetId.startsWith(LOCAL_FLOATER_PREFIX)) return false to nextState
        val due = mutation.dueEpochMs ?: return true to nextState
        val promoted = requireApiBody(
            api.promoteFloater(
                targetId,
                PromoteFloaterRequest(
                    due = Instant.ofEpochMilli(due).toString(),
                    rrule = mutation.rrule,
                ),
            ),
            "Could not schedule floater",
        ).todo
        // Remap the optimistic local todo (minted at enqueue time,
        // carried in `name`) to the server row — CREATE_TODO-style.
        val localTodoId = mutation.name
        if (promoted != null && localTodoId != null &&
            localTodoId.startsWith(LOCAL_TODO_PREFIX)
        ) {
            val promotedTodo = mapTodoDto(promoted)
            resolvedTodoIds[localTodoId] = promotedTodo.canonicalId
            nextState = replaceLocalTodoId(nextState, localTodoId, promotedTodo.canonicalId)
        }
        return true to nextState
    }

    private suspend fun applyDemoteTodoMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        state: OfflineSyncState,
        resolvedTodoIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        var nextState = state
        val targetId = resolvedTargetId ?: return false to nextState
        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return false to nextState
        val demoted = requireApiBody(
            api.demoteTodo(targetId),
            "Could not float task",
        ).floater
        val localFloaterId = mutation.name
        if (demoted != null && localFloaterId != null &&
            localFloaterId.startsWith(LOCAL_FLOATER_PREFIX)
        ) {
            val demotedFloater = mapFloaterDto(demoted)
            resolvedTodoIds[localFloaterId] = demotedFloater.canonicalId
            nextState = replaceLocalFloaterId(nextState, localFloaterId, demotedFloater.canonicalId)
        }
        return true to nextState
    }

    private suspend fun applyCreateStepMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        state: OfflineSyncState,
        resolvedTodoIds: MutableMap<String, String>,
    ): Pair<Boolean, OfflineSyncState> {
        val todoId = resolvedTargetId ?: return false to state
        // The parent todo must exist server-side before a step attaches.
        if (todoId.startsWith(LOCAL_TODO_PREFIX)) return false to state
        val created = requireApiBody(
            api.createTaskStep(
                CreateTaskStepRequest(
                    todoId = todoId,
                    title = mutation.title?.trim().orEmpty(),
                ),
            ),
            "Could not add step",
        ).step ?: return false to state
        // Remap the optimistic local step id (carried in `name`) so a
        // later TOGGLE/DELETE in this same batch resolves correctly.
        val localStepId = mutation.name
        if (localStepId != null && localStepId.startsWith(LOCAL_STEP_PREFIX)) {
            resolvedTodoIds[localStepId] = created.id
        }
        return true to state
    }

    private suspend fun applyToggleStepMutation(
        mutation: PendingMutationRecord,
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val stepId = resolvedTargetId ?: return false to state
        if (stepId.startsWith(LOCAL_STEP_PREFIX)) return false to state
        requireApiBody(
            api.toggleTaskStep(
                ToggleTaskStepRequest(id = stepId, completed = mutation.completed ?: false),
            ),
            "Could not update step",
        )
        return true to state
    }

    private suspend fun applyDeleteStepMutation(
        resolvedTargetId: String?,
        state: OfflineSyncState,
    ): Pair<Boolean, OfflineSyncState> {
        val stepId = resolvedTargetId ?: return false to state
        // A step that never synced has nothing to delete server-side.
        if (stepId.startsWith(LOCAL_STEP_PREFIX)) return true to state
        requireApiBody(
            api.deleteTaskStep(DeleteTaskStepRequest(id = stepId)),
            "Could not delete step",
        )
        return true to state
    }

    private fun mergeRemoteWithLocal(
        localState: OfflineSyncState,
        remote: RemoteSnapshot,
    ): OfflineSyncState {
        // Deliberately kind-only (not filtered on `staged`): a delayed-commit delete's
        // staged marker (PendingMutationRecord.staged) carries the same DELETE_LIST /
        // DELETE_FLOATER_LIST kind specifically so it counts here too — otherwise a
        // refresh mid-undo-window would write the still-server-side list straight back
        // into the cache (the bug this guard exists to prevent).
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
            this == MutationKind.UNCOMPLETE_TODO ||
            // Consumes a todo (its optimistic floater is local-prefixed and
            // therefore already merge-protected).
            this == MutationKind.DEMOTE_TODO
    }

    private fun MutationKind.affectsFloater(): Boolean {
        return this == MutationKind.CREATE_FLOATER ||
                this == MutationKind.UPDATE_FLOATER ||
                this == MutationKind.DELETE_FLOATER ||
                this == MutationKind.COMPLETE_FLOATER ||
                this == MutationKind.UNCOMPLETE_FLOATER ||
                // Consumes a floater (its optimistic todo is local-prefixed and
                // therefore already merge-protected).
                this == MutationKind.PROMOTE_FLOATER
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
        // Reachability probe budget for user-initiated refresh, foreground reconnect, and
        // cached-session restore. 2s was too aggressive — a cold TLS handshake over cellular
        // or dual-stack IPv6 routinely exceeds it and falsely flips the app to "offline".
        // 8s leaves ample headroom while still failing fast when there is genuinely no route
        // (the OS surfaces "no network" errors immediately, independent of this timeout).
        const val USER_REFRESH_CONNECTION_TIMEOUT_MS = 8_000L
        private const val LOG_TAG = "SyncManager"
        private const val OFFLINE_RESYNC_INTERVAL_MS = 5 * 60 * 1000L
        private const val MIN_FORCE_SYNC_INTERVAL_MS = 1_200L
    }
}
