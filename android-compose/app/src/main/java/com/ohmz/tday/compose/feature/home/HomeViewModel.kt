package com.ohmz.tday.compose.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.ui.userFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummary = DashboardSummary(
        todayCount = 0,
        scheduledCount = 0,
        allCount = 0,
        priorityCount = 0,
        completedCount = 0,
        lists = emptyList(),
    ),
    val searchableTodos: List<TodoItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val listRepository: ListRepository,
    private val syncManager: SyncManager,
    private val cacheManager: OfflineCacheManager,
    private val reminderScheduler: TaskReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        runCatching {
            HomeUiState(
                isLoading = false,
                summary = todoRepository.fetchDashboardSummarySnapshot(),
                searchableTodos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL),
                errorMessage = null,
            )
        }.getOrElse { HomeUiState() },
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCacheChanges()
    }

    private fun observeCacheChanges() {
        viewModelScope.launch {
            cacheManager.cacheDataVersion
                .collect {
                    refreshFromCache()
                }
        }
    }

    fun refreshFromCache() {
        runCatching {
            todoRepository.fetchDashboardSummarySnapshot() to todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL)
        }.onSuccess { (summary, todos) ->
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    summary = if (current.summary == summary) current.summary else summary,
                    searchableTodos = if (current.searchableTodos == todos) current.searchableTodos else todos,
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    errorMessage = error.userFacingMessage("Failed to load dashboard."),
                )
            }
        }
    }

    fun refresh() {
        refreshInternal(forceSync = true, showLoading = true)
    }

    private fun refreshInternal(forceSync: Boolean, showLoading: Boolean) {
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
                    syncManager.syncCachedData(force = true, replayPendingMutations = true)
                        .onFailure { /* fall back to local cache */ }
                }
                todoRepository.fetchDashboardSummary() to todoRepository.fetchTodos(mode = TodoListMode.ALL)
            }.onSuccess { (summary, todos) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        summary = if (current.summary == summary) current.summary else summary,
                        searchableTodos = if (current.searchableTodos == todos) current.searchableTodos else todos,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.userFacingMessage("Failed to load dashboard."),
                    )
                }
            }
        }
    }

    fun createList(name: String, color: String? = null, iconKey: String? = null) {
        val normalizedName = capitalizeFirstListLetter(name).trim()
        if (normalizedName.isBlank()) return
        viewModelScope.launch {
            runCatching { listRepository.createList(normalizedName, color = color, iconKey = iconKey) }
                .onSuccess { refreshInternal(forceSync = false, showLoading = false) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.userFacingMessage("Could not create list.")) }
                }
        }
    }

    fun createTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return
        viewModelScope.launch {
            runCatching { todoRepository.createTodo(payload) }
                .onSuccess {
                    rescheduleReminders()
                    runCatching { todoRepository.fetchDashboardSummaryCached() }
                        .onSuccess { summary ->
                            val todos = runCatching {
                                todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL)
                            }.getOrDefault(emptyList())
                            _uiState.update { current ->
                                current.copy(
                                    summary = if (current.summary == summary) current.summary else summary,
                                    searchableTodos = if (current.searchableTodos == todos) {
                                        current.searchableTodos
                                    } else {
                                        todos
                                    },
                                    errorMessage = null,
                                )
                            }
                        }
                        .onFailure { refreshInternal(forceSync = false, showLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.userFacingMessage("Could not create task.")) }
                }
        }
    }

    private fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    suspend fun parseTaskTitleNlp(
        text: String,
        referenceDueEpochMs: Long,
    ): TodoTitleNlpResponse? {
        return todoRepository.parseTodoTitleNlp(
            text = text,
            referenceDueEpochMs = referenceDueEpochMs,
        )
    }

    val lists: List<ListSummary>
        get() = _uiState.value.summary.lists
}
