package com.ohmz.tday.compose.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
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
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.core.ui.userFacingMessage
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummary = DashboardSummary(
        todayCount = 0,
        scheduledCount = 0,
        allCount = 0,
        priorityCount = 0,
        floaterCount = 0,
        completedCount = 0,
        lists = emptyList(),
    ),
    val searchableTodos: List<TodoItem> = emptyList(),
    val todayTodos: List<TodoItem> = emptyList(),
    val errorMessage: String? = null,
    val aiSummaryEnabled: Boolean = true,
    val aiSummaryConfigured: Boolean = false,
    val summaryText: String? = null,
    val summarySource: String? = null,
    val summaryGeneratedAt: String? = null,
    val summaryError: String? = null,
    val isSummarizing: Boolean = false,
)

private data class HomeSnapshot(
    val summary: DashboardSummary,
    val searchableTodos: List<TodoItem>,
    val todayTodos: List<TodoItem>,
    val aiSummaryEnabled: Boolean,
    val aiSummaryConfigured: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val listRepository: ListRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val cacheManager: OfflineCacheManager,
    private val reminderScheduler: TaskReminderScheduler,
    private val snackbarManager: SnackbarManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private var activeLoadingRefreshes = 0

    /// Resolves the user-facing message for a failed mutation and surfaces it as
    /// an error toast (in addition to the inline error state callers still set).
    /**
     * Surfaces a failed user action as an error toast only — matching the unified
     * toast policy (and the iOS app). Returns null so callers clear the inline
     * error card; the toast is the single surface for action failures.
     */
    private fun mutationFailureMessage(
        error: Throwable,
        @androidx.annotation.StringRes fallbackRes: Int,
    ): String? {
        snackbarManager.showError(error.userFacingMessage(appContext, fallbackRes))
        return null
    }

    private val _uiState = MutableStateFlow(
        runCatching {
            HomeUiState(
                isLoading = false,
                summary = todoRepository.fetchDashboardSummarySnapshot(),
                searchableTodos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL),
                todayTodos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.TODAY),
                errorMessage = null,
                aiSummaryEnabled = settingsRepository.isAiSummaryEnabledSnapshot(),
                aiSummaryConfigured = settingsRepository.aiSummaryConfiguredSnapshot(),
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
            HomeSnapshot(
                summary = todoRepository.fetchDashboardSummarySnapshot(),
                searchableTodos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL),
                todayTodos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.TODAY),
                aiSummaryEnabled = settingsRepository.isAiSummaryEnabledSnapshot(),
                aiSummaryConfigured = settingsRepository.aiSummaryConfiguredSnapshot(),
            )
        }.onSuccess { snapshot ->
            _uiState.update { current ->
                current.copy(
                    isLoading = activeLoadingRefreshes > 0,
                    summary = if (current.summary == snapshot.summary) current.summary else snapshot.summary,
                    searchableTodos = if (current.searchableTodos == snapshot.searchableTodos) {
                        current.searchableTodos
                    } else {
                        snapshot.searchableTodos
                    },
                    todayTodos = if (current.todayTodos == snapshot.todayTodos) current.todayTodos else snapshot.todayTodos,
                    aiSummaryEnabled = snapshot.aiSummaryEnabled,
                    aiSummaryConfigured = snapshot.aiSummaryConfigured,
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            _uiState.update { current ->
                current.copy(
                    isLoading = activeLoadingRefreshes > 0,
                    errorMessage = error.userFacingMessage(appContext, R.string.error_load_dashboard_failed),
                )
            }
        }
    }

    fun refresh() {
        refreshInternal(forceSync = true, showLoading = true)
    }

    private fun refreshInternal(forceSync: Boolean, showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) activeLoadingRefreshes += 1
            try {
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
                            replayPendingMutations = true,
                            connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                        )
                            .onFailure { /* fall back to local cache */ }
                    }
                    HomeSnapshot(
                        summary = todoRepository.fetchDashboardSummary(),
                        searchableTodos = todoRepository.fetchTodos(mode = TodoListMode.ALL),
                        todayTodos = todoRepository.fetchTodos(mode = TodoListMode.TODAY),
                        aiSummaryEnabled = settingsRepository.isAiSummaryEnabledSnapshot(),
                        aiSummaryConfigured = settingsRepository.aiSummaryConfiguredSnapshot(),
                    )
                }.onSuccess { snapshot ->
                    _uiState.update { current ->
                        val keepLoading = activeLoadingRefreshes > if (showLoading) 1 else 0
                        current.copy(
                            isLoading = keepLoading,
                            summary = if (current.summary == snapshot.summary) current.summary else snapshot.summary,
                            searchableTodos = if (current.searchableTodos == snapshot.searchableTodos) {
                                current.searchableTodos
                            } else {
                                snapshot.searchableTodos
                            },
                            todayTodos = if (current.todayTodos == snapshot.todayTodos) current.todayTodos else snapshot.todayTodos,
                            aiSummaryEnabled = snapshot.aiSummaryEnabled,
                            aiSummaryConfigured = snapshot.aiSummaryConfigured,
                            errorMessage = null,
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { current ->
                        val keepLoading = activeLoadingRefreshes > if (showLoading) 1 else 0
                        current.copy(
                            isLoading = keepLoading,
                            errorMessage = error.userFacingMessage(appContext, R.string.error_load_dashboard_failed),
                        )
                    }
                }
            } finally {
                if (showLoading) activeLoadingRefreshes = maxOf(activeLoadingRefreshes - 1, 0)
            }
        }
    }

    fun summarizeToday() {
        val current = _uiState.value
        if (current.isSummarizing) return
        if (!current.aiSummaryEnabled) {
            _uiState.update {
                it.copy(summaryError = appContext.getString(R.string.todos_summary_admin_disabled))
            }
            return
        }
        _uiState.update {
            it.copy(
                isSummarizing = true,
                summaryText = null,
                summarySource = null,
                summaryGeneratedAt = null,
                summaryError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.summarizeTodos(mode = TodoListMode.TODAY)
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isSummarizing = false,
                        summaryText = response.summary,
                        summarySource = response.source,
                        summaryGeneratedAt = response.generatedAt,
                        summaryError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSummarizing = false,
                        summaryError = error.userFacingMessage(
                            appContext,
                            R.string.error_summarize_tasks_failed,
                        ),
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
                    _uiState.update {
                        it.copy(
                            errorMessage = mutationFailureMessage(
                                error,
                                R.string.error_create_list_failed
                            )
                        )
                    }
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
                    _uiState.update {
                        it.copy(
                            errorMessage = mutationFailureMessage(
                                error,
                                R.string.error_create_task_failed
                            )
                        )
                    }
                }
        }
    }

    fun updateTask(todo: TodoItem, payload: CreateTaskPayload) {
        val normalizedTitle = payload.title.trim()
        if (normalizedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val normalizedPayload = CreateTaskPayload(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            due = payload.due,
            rrule = payload.rrule,
            listId = normalizedListId,
        )

        val previousState = _uiState.value
        val updatedTodo = todo.copy(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            due = payload.due,
            rrule = payload.rrule,
            listId = normalizedListId,
        )

        _uiState.update { current ->
            current.copy(
                searchableTodos = current.searchableTodos.map { item ->
                    if (item.id == todo.id) updatedTodo else item
                },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.updateTodo(todo = todo, payload = normalizedPayload)
            }.onSuccess {
                refreshAfterMutation()
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = mutationFailureMessage(error, R.string.error_update_task_failed),
                )
            }
        }
    }

    fun toggleComplete(todo: TodoItem) {
        val previousState = _uiState.value
        _uiState.update { current ->
            current.copy(
                searchableTodos = current.searchableTodos.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.completeTodo(todo)
            }.onSuccess {
                rescheduleReminders()
                refreshAfterMutation()
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = mutationFailureMessage(
                        error,
                        R.string.error_complete_task_failed
                    ),
                )
            }
        }
    }

    fun delete(todo: TodoItem, onDeleted: (() -> Unit)? = null) {
        val previousState = _uiState.value
        _uiState.update { current ->
            current.copy(
                searchableTodos = current.searchableTodos.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.deleteTodo(todo)
            }.onSuccess {
                onDeleted?.invoke()
                rescheduleReminders()
                refreshAfterMutation()
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = mutationFailureMessage(error, R.string.error_delete_task_failed),
                )
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

    fun completeTodo(todo: TodoItem) {
        _uiState.update { current ->
            current.copy(todayTodos = current.todayTodos.filterNot { it.id == todo.id })
        }
        viewModelScope.launch {
            runCatching { todoRepository.completeTodo(todo) }
                .onSuccess { refreshInternal(forceSync = false, showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = mutationFailureMessage(
                                error,
                                R.string.error_complete_task_failed
                            )
                        )
                    }
                    refreshFromCache()
                }
        }
    }

    fun deleteTodo(todo: TodoItem) {
        _uiState.update { current ->
            current.copy(todayTodos = current.todayTodos.filterNot { it.id == todo.id })
        }
        viewModelScope.launch {
            runCatching { todoRepository.deleteTodo(todo) }
                .onSuccess { refreshInternal(forceSync = false, showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = mutationFailureMessage(
                                error,
                                R.string.error_delete_task_failed
                            )
                        )
                    }
                    refreshFromCache()
                }
        }
    }

    val lists: List<ListSummary>
        get() = _uiState.value.summary.lists

    private fun refreshAfterMutation() {
        runCatching {
            val summary = todoRepository.fetchDashboardSummarySnapshot()
            val todos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL)
            summary to todos
        }.onSuccess { (summary, todos) ->
            _uiState.update { current ->
                current.copy(
                    summary = if (current.summary == summary) current.summary else summary,
                    searchableTodos = if (current.searchableTodos == todos) current.searchableTodos else todos,
                    errorMessage = null,
                )
            }
        }.onFailure {
            refreshInternal(forceSync = false, showLoading = false)
        }
    }
}
