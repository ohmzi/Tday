package com.ohmz.tday.compose.core.data.list

import android.util.Log
import com.ohmz.tday.compose.core.data.CachedFloaterListRecord
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
        buildListsForState(cacheManager.loadOfflineState())

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
                "updateFloaterList deferred listId=$listId reason=${immediateError.message}"
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
                "deleteFloaterList deferred listId=$normalizedListId reason=${immediateError.message}"
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
