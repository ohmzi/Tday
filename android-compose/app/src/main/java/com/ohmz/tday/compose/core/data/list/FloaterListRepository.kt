package com.ohmz.tday.compose.core.data.list

import android.util.Log
import com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.floaterListFromCache
import com.ohmz.tday.compose.core.data.cache.orderFloaterListsLikeWeb
import com.ohmz.tday.compose.core.data.cache.parseOptionalInstant
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CreateFloaterListRequest
import com.ohmz.tday.compose.core.model.DeleteFloaterListRequest
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.UpdateFloaterListRequest
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.network.TdayApiService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloaterListRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val secureConfigStore: SecureConfigStore,
    private val syncManager: SyncManager,
) {
    suspend fun fetchLists(): List<ListSummary> =
        buildListsForState(cacheManager.loadOfflineState())

    fun fetchListsSnapshot(): List<ListSummary> =
        buildListsForState(cacheManager.loadOfflineStateBlocking())

    suspend fun createList(name: String, color: String? = null, iconKey: String? = null) {
        val normalizedName = capitalizeFirstListLetter(name).trim()
        if (normalizedName.isBlank()) return

        val localListId = "$LOCAL_FLOATER_LIST_PREFIX${UUID.randomUUID()}"
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        cacheManager.updateOfflineState { state ->
            val newList = CachedFloaterListRecord(
                id = localListId,
                name = normalizedName,
                color = color,
                iconKey = iconKey,
                todoCount = 0,
                createdAtEpochMs = timestampMs,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                floaterLists = state.floaterLists + newList,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.CREATE_FLOATER_LIST,
                    targetId = localListId,
                    timestampEpochMs = timestampMs,
                    name = normalizedName,
                    color = color,
                    iconKey = iconKey,
                ),
            )
        }

        if (syncManager.isLocalMode()) return

        runCatching {
            requireApiBody(
                api.createFloaterList(
                    CreateFloaterListRequest(
                        name = normalizedName,
                        color = color,
                        iconKey = iconKey,
                    ),
                ),
                "Could not create floater list",
            ).list
        }.onSuccess { createdList ->
            if (createdList == null) return@onSuccess
            val createdAt =
                parseOptionalInstant(createdList.createdAt)?.toEpochMilli() ?: timestampMs
            val updatedAt =
                parseOptionalInstant(createdList.updatedAt)?.toEpochMilli() ?: timestampMs
            cacheManager.updateOfflineState { state ->
                val remapped = replaceLocalFloaterListId(
                    state = state,
                    localListId = localListId,
                    serverListId = createdList.id,
                )
                val todoCount =
                    remapped.floaters.count { !it.completed && it.listId == createdList.id }
                remapped.copy(
                    floaterLists = remapped.floaterLists.map { list ->
                        if (list.id == createdList.id) {
                            list.copy(
                                name = createdList.name,
                                color = createdList.color,
                                iconKey = createdList.iconKey ?: list.iconKey,
                                todoCount = todoCount,
                                updatedAtEpochMs = updatedAt,
                                createdAtEpochMs = createdAt,
                            )
                        } else {
                            list
                        }
                    },
                    pendingMutations = remapped.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun updateList(
        listId: String,
        name: String,
        color: String? = null,
        iconKey: String? = null
    ) {
        val trimmedName = capitalizeFirstListLetter(name).trim()
        if (listId.isBlank()) return
        require(trimmedName.isNotBlank()) { "List name is required" }

        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        if (listId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    floaterLists = state.floaterLists.map { list ->
                        if (list.id == listId) {
                            list.copy(
                                name = trimmedName,
                                color = color ?: list.color,
                                iconKey = iconKey ?: list.iconKey,
                                updatedAtEpochMs = timestampMs,
                            )
                        } else {
                            list
                        }
                    },
                    pendingMutations = state.pendingMutations.map { mutation ->
                        if (mutation.kind == MutationKind.CREATE_FLOATER_LIST && mutation.targetId == listId) {
                            mutation.copy(
                                name = trimmedName,
                                color = color ?: mutation.color,
                                iconKey = iconKey ?: mutation.iconKey,
                                timestampEpochMs = timestampMs,
                            )
                        } else {
                            mutation
                        }
                    },
                )
            }
            iconKey?.takeIf { it.isNotBlank() }?.let {
                secureConfigStore.saveListIcon(listId, it)
            }
            if (syncManager.isLocalMode()) return
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_FLOATER_LIST,
            targetId = listId,
            timestampEpochMs = timestampMs,
            name = trimmedName,
            color = color,
            iconKey = iconKey,
        )
        cacheManager.updateOfflineState { state ->
            state.copy(
                floaterLists = state.floaterLists.map { list ->
                    if (list.id == listId) {
                        list.copy(
                            name = trimmedName,
                            color = color ?: list.color,
                            iconKey = iconKey ?: list.iconKey,
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        list
                    }
                },
                pendingMutations = state.pendingMutations
                    .filterNot { it.kind == MutationKind.UPDATE_FLOATER_LIST && it.targetId == listId } + pendingMutation,
            )
        }

        if (syncManager.isLocalMode()) {
            iconKey?.takeIf { it.isNotBlank() }?.let {
                secureConfigStore.saveListIcon(listId, it)
            }
            return
        }

        val immediateError = runCatching {
            requireApiBody(
                api.patchFloaterListByBody(
                    UpdateFloaterListRequest(
                        id = listId,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    ),
                ),
                "Could not update floater list",
            )
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(
                immediateError,
                pendingMutation
            )
        ) {
            throw immediateError
        }

        iconKey?.takeIf { it.isNotBlank() }?.let {
            secureConfigStore.saveListIcon(listId, it)
        }

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId })
            }
        } else {
            Log.w(
                LOG_TAG,
                "updateFloaterList deferred reason=${immediateError.javaClass.simpleName}"
            )
        }
    }

    /** Reset a reusable list: locally un-complete all its floaters, then queue the reset. */
    suspend fun resetFloaterList(listId: String) {
        if (listId.isBlank()) return
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.RESET_FLOATER_LIST,
            targetId = listId,
            timestampEpochMs = timestampMs,
        )

        cacheManager.updateOfflineState { state ->
            val updatedFloaters = state.floaters.map {
                if (it.listId == listId && it.completed) {
                    it.copy(completed = false, updatedAtEpochMs = timestampMs)
                } else {
                    it
                }
            }
            state.copy(
                floaters = updatedFloaters,
                completedFloaters = state.completedFloaters.filterNot { it.listId == listId },
                pendingMutations = state.pendingMutations
                    .filterNot { it.kind == MutationKind.RESET_FLOATER_LIST && it.targetId == listId } + pendingMutation,
            )
        }

        if (syncManager.isLocalMode() || listId.startsWith(LOCAL_FLOATER_LIST_PREFIX)) return

        val immediateError = runCatching {
            requireApiBody(api.resetFloaterList(listId), "Could not reset floater list")
        }.exceptionOrNull()

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId })
            }
        } else if (isLikelyUnrecoverableMutationError(immediateError, pendingMutation)) {
            throw immediateError
        } else {
            Log.w(LOG_TAG, "resetFloaterList deferred reason=${immediateError.javaClass.simpleName}")
        }
    }

    /**
     * Stage step of the delayed-commit floater-list delete: prunes the list, its
     * floaters and its completed floaters from the local cache exactly like the
     * prune-half of [deleteList], but records nothing for the server (no
     * DELETE_FLOATER_LIST pending mutation), so nothing can sync out during the
     * undo window. The removed records are captured so [undoStagedListDeletion]
     * can restore them exactly; the commit step is the existing [deleteList],
     * whose prune-half re-runs as a no-op on the already-pruned state.
     */
    suspend fun stageDeleteList(listId: String): StagedFloaterListDeletion {
        val normalizedListId = listId.trim()
        if (normalizedListId.isBlank()) return StagedFloaterListDeletion()

        var staged = StagedFloaterListDeletion()
        cacheManager.updateOfflineState { state ->
            val deletedFloaterIds = state.floaters
                .filter { it.listId == normalizedListId }
                .map { it.canonicalId }
                .toSet()

            fun matchesMutation(mutation: PendingMutationRecord): Boolean =
                mutation.targetId == normalizedListId ||
                    mutation.listId == normalizedListId ||
                    deletedFloaterIds.contains(mutation.targetId)

            fun matchesCompleted(completed: CachedCompletedFloaterRecord): Boolean =
                completed.listId == normalizedListId ||
                    completed.originalFloaterId?.let(deletedFloaterIds::contains) == true

            staged = StagedFloaterListDeletion(
                removedFloaterLists = state.floaterLists.filter { it.id == normalizedListId },
                removedFloaters = state.floaters.filter { it.listId == normalizedListId },
                removedCompletedFloaters = state.completedFloaters.filter(::matchesCompleted),
                removedPendingMutations = state.pendingMutations.filter(::matchesMutation),
            )
            state.copy(
                floaterLists = state.floaterLists.filterNot { it.id == normalizedListId },
                floaters = state.floaters.filterNot { it.listId == normalizedListId },
                completedFloaters = state.completedFloaters.filterNot(::matchesCompleted),
                pendingMutations = state.pendingMutations.filterNot(::matchesMutation),
            )
        }
        return staged
    }

    /** Undo step: re-inserts the records captured by [stageDeleteList]. Idempotent. */
    suspend fun undoStagedListDeletion(staged: StagedFloaterListDeletion) {
        cacheManager.updateOfflineState { state ->
            val listIds = state.floaterLists.map { it.id }.toSet()
            val floaterIds = state.floaters.map { it.id }.toSet()
            val completedIds = state.completedFloaters.map { it.id }.toSet()
            val mutationIds = state.pendingMutations.map { it.mutationId }.toSet()
            state.copy(
                floaterLists = state.floaterLists +
                    staged.removedFloaterLists.filterNot { it.id in listIds },
                floaters = state.floaters +
                    staged.removedFloaters.filterNot { it.id in floaterIds },
                completedFloaters = state.completedFloaters +
                    staged.removedCompletedFloaters.filterNot { it.id in completedIds },
                pendingMutations = state.pendingMutations +
                    staged.removedPendingMutations.filterNot { it.mutationId in mutationIds },
            )
        }
    }

    suspend fun deleteList(
        listId: String,
        onOptimisticDelete: () -> Unit = {},
    ) {
        val normalizedListId = listId.trim()
        if (normalizedListId.isBlank()) return

        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.DELETE_FLOATER_LIST,
            targetId = normalizedListId,
            timestampEpochMs = timestampMs,
        )
        val isLocalOnly = normalizedListId.startsWith(LOCAL_FLOATER_LIST_PREFIX)

        cacheManager.updateOfflineState { state ->
            val deletedFloaterIds = state.floaters
                .filter { it.listId == normalizedListId }
                .map { it.canonicalId }
                .toSet()
            val prunedMutations = state.pendingMutations.filterNot { mutation ->
                mutation.targetId == normalizedListId ||
                        mutation.listId == normalizedListId ||
                        deletedFloaterIds.contains(mutation.targetId)
            }

            state.copy(
                floaterLists = state.floaterLists.filterNot { it.id == normalizedListId },
                floaters = state.floaters.filterNot { it.listId == normalizedListId },
                completedFloaters = state.completedFloaters.filterNot { completed ->
                    completed.listId == normalizedListId ||
                            completed.originalFloaterId?.let(deletedFloaterIds::contains) == true
                },
                pendingMutations = if (isLocalOnly) prunedMutations else prunedMutations + pendingMutation,
            )
        }

        onOptimisticDelete()

        if (syncManager.isLocalMode()) return

        if (isLocalOnly) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        val immediateError = runCatching {
            requireApiBody(
                api.deleteFloaterListByBody(DeleteFloaterListRequest(id = normalizedListId)),
                "Could not delete floater list",
            )
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(
                immediateError,
                pendingMutation
            )
        ) {
            throw immediateError
        }

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId })
            }
        } else {
            Log.w(
                LOG_TAG,
                "deleteFloaterList deferred reason=${immediateError.javaClass.simpleName}"
            )
        }
    }

    private fun buildListsForState(state: OfflineSyncState): List<ListSummary> {
        val todoCountsByList = state.floaters
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()
        return orderFloaterListsLikeWeb(state.floaterLists).map {
            floaterListFromCache(cache = it, todoCountOverride = todoCountsByList[it.id] ?: 0)
        }
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

    private companion object {
        const val LOG_TAG = "FloaterListRepository"
    }
}

/**
 * Local cache records removed by [FloaterListRepository.stageDeleteList], retained
 * so an Undo within the delete-toast window can restore the exact pre-delete state
 * (the list plus its cascaded floaters/completed floaters). Nothing here has been
 * sent to the server.
 */
data class StagedFloaterListDeletion(
    val removedFloaterLists: List<CachedFloaterListRecord> = emptyList(),
    val removedFloaters: List<CachedFloaterRecord> = emptyList(),
    val removedCompletedFloaters: List<CachedCompletedFloaterRecord> = emptyList(),
    val removedPendingMutations: List<PendingMutationRecord> = emptyList(),
)
