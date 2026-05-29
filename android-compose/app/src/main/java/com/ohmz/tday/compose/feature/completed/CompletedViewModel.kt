package com.ohmz.tday.compose.feature.completed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.completed.CompletedRepository
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.ui.userFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompletedUiState(
    val isLoading: Boolean = false,
    val items: List<CompletedItem> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CompletedViewModel @Inject constructor(
    private val completedRepository: CompletedRepository,
    private val listRepository: ListRepository,
    private val syncManager: SyncManager,
    private val cacheManager: OfflineCacheManager,
    private val reminderScheduler: TaskReminderScheduler,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        runCatching {
            CompletedUiState(
                isLoading = false,
                items = completedRepository.fetchCompletedItemsSnapshot(),
                lists = listRepository.fetchListsSnapshot(),
                errorMessage = null,
            )
        }.getOrElse { CompletedUiState() },
    )
    val uiState: StateFlow<CompletedUiState> = _uiState.asStateFlow()
    private var hasLoadedScreen = false

    init {
        observeCacheChanges()
    }

    private fun observeCacheChanges() {
        viewModelScope.launch {
            cacheManager.cacheDataVersion
                .collect {
                    if (!hasLoadedScreen) return@collect
                    hydrateFromCache()
                }
        }
    }

    fun load() {
        hasLoadedScreen = true
        hydrateFromCache()
    }

    fun refresh() {
        hasLoadedScreen = true
        loadInternal(forceSync = true, showLoading = true)
    }

    private fun hydrateFromCache() {
        runCatching {
            completedRepository.fetchCompletedItemsSnapshot() to listRepository.fetchListsSnapshot()
        }.onSuccess { (items, lists) ->
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    items = if (current.items == items) current.items else items,
                    lists = if (current.lists == lists) current.lists else lists,
                    errorMessage = null,
                )
            }
        }
    }

    private fun loadInternal(forceSync: Boolean, showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { current ->
                    if (current.isLoading && current.errorMessage == null) current
                    else current.copy(isLoading = true, errorMessage = null)
                }
            } else {
                _uiState.update { current ->
                    if (current.errorMessage == null) current else current.copy(errorMessage = null)
                }
            }
            runCatching {
                if (forceSync) {
                    syncManager.syncCachedData(
                        force = true,
                        replayPendingMutations = false,
                        connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                    )
                        .onFailure { /* fall back to local cache */ }
                }
                Pair(
                    completedRepository.fetchCompletedItems(),
                    listRepository.fetchLists(),
                )
            }.onSuccess { (items, lists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = if (current.items == items) current.items else items,
                        lists = if (current.lists == lists) current.lists else lists,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.userFacingMessage(appContext, R.string.error_load_failed),
                    )
                }
            }
        }
    }

    fun delete(item: CompletedItem, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            runCatching { completedRepository.deleteCompletedTodo(item) }
                .onSuccess {
                    onDeleted?.invoke()
                    loadInternal(forceSync = false, showLoading = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_delete_task_failed))
                    }
                }
        }
    }

    fun uncomplete(item: CompletedItem) {
        viewModelScope.launch {
            runCatching { completedRepository.uncomplete(item) }
                .onSuccess {
                    rescheduleReminders()
                    loadInternal(forceSync = false, showLoading = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_restore_task_failed))
                    }
                }
        }
    }

    fun update(item: CompletedItem, payload: CreateTaskPayload) {
        viewModelScope.launch {
            runCatching { completedRepository.updateCompletedTodo(item, payload) }
                .onSuccess { loadInternal(forceSync = false, showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_update_task_failed))
                    }
                }
        }
    }

    private fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }
}
