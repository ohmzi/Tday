package com.ohmz.tday.compose.core.data.list

import android.util.Log
import com.ohmz.tday.compose.core.data.CachedListRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.LOCAL_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.listFromCache
import com.ohmz.tday.compose.core.data.cache.orderListsLikeWeb
import com.ohmz.tday.compose.core.data.cache.parseOptionalInstant
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.DeleteListRequest
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.UpdateListRequest
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.network.TdayApiService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val secureConfigStore: SecureConfigStore,
    private val syncManager: SyncManager,
) {
    suspend fun fetchLists(): List<ListSummary> {
        return buildListsForState(cacheManager.loadOfflineState())
    }

    fun fetchListsSnapshot(): List<ListSummary> {
        return buildListsForState(cacheManager.loadOfflineStateBlocking())
    }

    suspend fun createList(
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        val normalizedName = capitalizeFirstListLetter(name).trim()
        if (normalizedName.isBlank()) return

        val localListId = "$LOCAL_LIST_PREFIX${UUID.randomUUID()}"
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        cacheManager.updateOfflineState { state ->
            val newList = CachedListRecord(
                id = localListId,
                name = normalizedName,
                color = color,
                iconKey = iconKey,
                todoCount = 0,
                createdAtEpochMs = timestampMs,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                lists = state.lists + newList,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.CREATE_LIST,
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
                api.createList(
                    CreateListRequest(
                        name = normalizedName,
                        color = color,
                        iconKey = iconKey,
                    ),
                ),
                "Could not create list",
            ).list
        }.onSuccess { createdList ->
            if (createdList == null) return@onSuccess
            val createdAt =
                parseOptionalInstant(createdList.createdAt)?.toEpochMilli() ?: timestampMs
            val updatedAt =
                parseOptionalInstant(createdList.updatedAt)?.toEpochMilli() ?: timestampMs
            cacheManager.updateOfflineState { state ->
                val remapped = replaceLocalListId(
                    state = state,
                    localListId = localListId,
                    serverListId = createdList.id,
                )
                val todoCount = remapped.todos.count { !it.completed && it.listId == createdList.id }
                remapped.copy(
                    lists = remapped.lists.map { list ->
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
        iconKey: String? = null,
    ) {
        val trimmedName = capitalizeFirstListLetter(name).trim()
        if (listId.isBlank()) return
        require(trimmedName.isNotBlank()) { "List name is required" }
        Log.d(LOG_TAG, "updateList start hasColor=${color != null} hasIcon=${iconKey != null}")

        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        if (listId.startsWith(LOCAL_LIST_PREFIX)) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    lists = state.lists.map { list ->
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
                        if (mutation.kind == MutationKind.CREATE_LIST && mutation.targetId == listId) {
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
            Log.d(LOG_TAG, "updateList local-list path finished")
            return
        }

        cacheManager.updateOfflineState { state ->
            state.copy(
                lists = state.lists.map { list ->
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
                    .filterNot { it.kind == MutationKind.UPDATE_LIST && it.targetId == listId } +
                    PendingMutationRecord(
                        mutationId = mutationId,
                        kind = MutationKind.UPDATE_LIST,
                        targetId = listId,
                        timestampEpochMs = timestampMs,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    ),
            )
        }

        if (syncManager.isLocalMode()) {
            iconKey?.takeIf { it.isNotBlank() }?.let {
                secureConfigStore.saveListIcon(listId, it)
            }
            return
        }

        Log.d(LOG_TAG, "updateList patch /api/list")
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_LIST,
            targetId = listId,
            timestampEpochMs = timestampMs,
            name = trimmedName,
            color = color,
            iconKey = iconKey,
        )
        val immediateError = runCatching {
            requireApiBody(
                api.patchListByBody(
                    UpdateListRequest(
                        id = listId,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    ),
                ),
                "Could not update list",
            )
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(immediateError, pendingMutation)) {
            throw immediateError
        }

        iconKey?.takeIf { it.isNotBlank() }?.let {
            secureConfigStore.saveListIcon(listId, it)
        }

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
            Log.d(LOG_TAG, "updateList success")
        } else {
            Log.w(LOG_TAG, "updateList deferred reason=${immediateError.javaClass.simpleName}")
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
            kind = MutationKind.DELETE_LIST,
            targetId = normalizedListId,
            timestampEpochMs = timestampMs,
        )
        val isLocalOnly = normalizedListId.startsWith(LOCAL_LIST_PREFIX)

        cacheManager.updateOfflineState { state ->
            val deletedTodoIds = state.todos
                .filter { it.listId == normalizedListId }
                .map { it.canonicalId }
                .toSet()
            val prunedMutations = state.pendingMutations.filterNot { mutation ->
                mutation.targetId == normalizedListId ||
                        mutation.listId == normalizedListId ||
                        deletedTodoIds.contains(mutation.targetId)
            }

            state.copy(
                lists = state.lists.filterNot { it.id == normalizedListId },
                todos = state.todos.filterNot { it.listId == normalizedListId },
                completedItems = state.completedItems.filterNot { completed ->
                    completed.listId == normalizedListId ||
                            completed.originalTodoId?.let(deletedTodoIds::contains) == true
                },
                pendingMutations = if (isLocalOnly) {
                    prunedMutations
                } else {
                    prunedMutations + pendingMutation
                },
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
                api.deleteListByBody(DeleteListRequest(id = normalizedListId)),
                "Could not delete list",
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
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
            Log.d(LOG_TAG, "deleteList success")
        } else {
            Log.w(
                LOG_TAG,
                "deleteList deferred reason=${immediateError.javaClass.simpleName}"
            )
        }
    }

    private fun buildListsForState(state: OfflineSyncState): List<ListSummary> {
        val todoCountsByList = state.todos
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()
        return orderListsLikeWeb(state.lists).map {
            listFromCache(cache = it, todoCountOverride = todoCountsByList[it.id] ?: 0)
        }
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

    private companion object {
        const val LOG_TAG = "ListRepository"
    }
}
