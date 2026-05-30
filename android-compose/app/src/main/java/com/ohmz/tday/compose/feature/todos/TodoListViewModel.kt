package com.ohmz.tday.compose.feature.todos

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.isLikelyConnectivityIssue
import com.ohmz.tday.compose.core.data.list.FloaterListRepository
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TaskRescheduleScope
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.model.movedDuePreservingTime
import com.ohmz.tday.compose.core.model.repositoryTargetForReschedule
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.core.ui.userFacingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodoListUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val mode: TodoListMode = TodoListMode.TODAY,
    val listId: String? = null,
    val hasHydratedSnapshot: Boolean = false,
    val lists: List<ListSummary> = emptyList(),
    val items: List<TodoItem> = emptyList(),
    val errorMessage: String? = null,
    val aiSummaryEnabled: Boolean = true,
    val summaryText: String? = null,
    val summarySource: String? = null,
    val summaryGeneratedAt: String? = null,
    val summaryError: String? = null,
    val summaryConnectivityError: Boolean = false,
    val isSummarizing: Boolean = false,
)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val listRepository: ListRepository,
    private val floaterListRepository: FloaterListRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val cacheManager: OfflineCacheManager,
    private val reminderScheduler: TaskReminderScheduler,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TodoListUiState(title = appContext.getString(R.string.todos_title_tasks)),
    )
    val uiState: StateFlow<TodoListUiState> = _uiState.asStateFlow()
    private var hasLoadedMode = false

    init {
        observeCacheChanges()
    }

    private fun observeCacheChanges() {
        viewModelScope.launch {
            cacheManager.cacheDataVersion
                .collect {
                    if (!hasLoadedMode) return@collect
                    hydrateFromCache(
                        mode = _uiState.value.mode,
                        listId = _uiState.value.listId,
                    )
                }
        }
    }

    fun load(mode: TodoListMode, listId: String? = null, listName: String? = null) {
        hasLoadedMode = true
        TdayTelemetry.addBreadcrumb(
            "todo_list.load",
            data = modeTelemetryData(mode = mode, scopedList = !listId.isNullOrBlank()),
        )
        _uiState.update { current ->
            val isSameTimeline = current.mode == mode && current.listId == listId
            current.copy(
                mode = mode,
                listId = listId,
                hasHydratedSnapshot = if (isSameTimeline) {
                    current.hasHydratedSnapshot
                } else {
                    false
                },
                title = when (mode) {
                    TodoListMode.TODAY -> appContext.getString(R.string.todos_title_today)
                    TodoListMode.OVERDUE -> appContext.getString(R.string.todos_title_overdue)
                    TodoListMode.SCHEDULED -> appContext.getString(R.string.todos_title_scheduled)
                    TodoListMode.ALL -> appContext.getString(R.string.todos_title_all_tasks)
                    TodoListMode.PRIORITY -> appContext.getString(R.string.todos_title_priority)
                    TodoListMode.FLOATER -> listName ?: appContext.getString(R.string.todos_title_floater)
                    TodoListMode.LIST -> listName ?: appContext.getString(R.string.todos_title_list)
                },
                aiSummaryEnabled = settingsRepository.isAiSummaryEnabledSnapshot(),
                summaryText = null,
                summarySource = null,
                summaryGeneratedAt = null,
                summaryError = null,
                summaryConnectivityError = false,
                isSummarizing = false,
            )
        }
        hydrateFromCache(mode = mode, listId = listId)
        refreshAiSummaryAvailability()
    }

    fun summarizeCurrentMode() {
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
                summaryConnectivityError = false,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.summarizeTodos(mode = current.mode, listId = current.listId)
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
                    if (isLikelyConnectivityIssue(error)) {
                        it.copy(
                            isSummarizing = false,
                            summaryError = appContext.getString(R.string.todos_summary_offline_unavailable),
                        )
                    } else {
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
    }

    fun dismissSummaryConnectivityError() {
        _uiState.update { it.copy(summaryConnectivityError = false) }
    }

    fun refresh() {
        TdayTelemetry.addBreadcrumb(
            "todo_list.refresh",
            data = modeTelemetryData(),
        )
        refreshInternal(forceSync = true, showLoading = true)
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

    private fun hydrateFromCache(mode: TodoListMode, listId: String?) {
        runCatching {
            val todos = todoRepository.fetchTodosSnapshot(mode = mode, listId = listId)
            val lists = fetchListsSnapshotForMode(mode)
            val aiSummaryEnabled = settingsRepository.isAiSummaryEnabledSnapshot()
            Triple(todos, lists, aiSummaryEnabled)
        }.onSuccess { (todos, lists, aiSummaryEnabled) ->
            _uiState.update { current ->
                current.copy(
                    hasHydratedSnapshot = true,
                    lists = if (current.lists == lists) current.lists else lists,
                    items = if (current.items == todos) current.items else todos,
                    aiSummaryEnabled = aiSummaryEnabled,
                    errorMessage = null,
                )
            }
        }.onFailure {
            _uiState.update { current ->
                current.copy(hasHydratedSnapshot = true)
            }
        }
    }

    private fun refreshInternal(forceSync: Boolean, showLoading: Boolean) {
        val mode = _uiState.value.mode
        val listId = _uiState.value.listId

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
                        replayPendingMutations = true,
                        connectionProbeTimeoutMs = SyncManager.USER_REFRESH_CONNECTION_TIMEOUT_MS,
                    )
                        .onFailure { /* fall back to local cache */ }
                }
                val todos = todoRepository.fetchTodos(mode = mode, listId = listId)
                val lists = fetchListsForMode(mode)
                todos to lists
            }.onSuccess { (todos, lists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        lists = if (current.lists == lists) current.lists else lists,
                        items = if (current.items == todos) current.items else todos,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.userFacingMessage(appContext, R.string.error_load_tasks_failed),
                    )
                }
            }
            refreshAiSummaryAvailability()
        }
    }

    private fun refreshAiSummaryAvailability() {
        viewModelScope.launch {
            val enabled = settingsRepository.refreshAiSummaryEnabled()
            _uiState.update { current ->
                if (current.aiSummaryEnabled == enabled) current
                else current.copy(aiSummaryEnabled = enabled)
            }
        }
    }

    fun addTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return
        val mode = _uiState.value.mode
        val currentListId = _uiState.value.listId
        TdayTelemetry.addBreadcrumb(
            "task.create",
            data = taskTelemetryData(mode = mode, payload = payload),
        )

        viewModelScope.launch {
            runCatching {
                if (mode == TodoListMode.FLOATER) {
                    todoRepository.createFloater(payload)
                } else {
                    todoRepository.createTodo(payload)
                }
            }.onSuccess {
                if (mode != TodoListMode.FLOATER) rescheduleReminders()
                runCatching {
                    val todos = todoRepository.fetchTodosCached(mode = mode, listId = currentListId)
                    val lists = fetchListsForMode(mode)
                    todos to lists
                }.onSuccess { (todos, lists) ->
                    _uiState.update { current ->
                        current.copy(
                            lists = if (current.lists == lists) current.lists else lists,
                            items = if (current.items == todos) current.items else todos,
                            errorMessage = null,
                        )
                    }
                }.onFailure { refreshInternal(forceSync = false, showLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_create_task_failed))
                }
            }
        }
    }

    fun updateTask(todo: TodoItem, payload: CreateTaskPayload) {
        TdayTelemetry.addBreadcrumb(
            "task.update",
            data = taskTelemetryData(mode = _uiState.value.mode, payload = payload),
        )
        updateTaskInternal(
            visibleTodo = todo,
            repositoryTodo = todo,
            payload = payload,
        )
    }

    fun moveTask(todo: TodoItem, targetDate: LocalDate, scope: TaskRescheduleScope) {
        val due = todo.due ?: return
        val movedDue = movedDuePreservingTime(due, targetDate)
        val previousState = _uiState.value
        val mode = previousState.mode
        val currentListId = previousState.listId
        val updatedTodo = todo.copy(due = movedDue)
        TdayTelemetry.addBreadcrumb(
            "task.reschedule",
            data = taskTelemetryData(mode = mode, scope = scope) + mapOf("source" to "todo_list"),
        )

        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id == todo.id) updatedTodo else item
                },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.moveTodo(
                    todo = todo.repositoryTargetForReschedule(scope),
                    due = movedDue,
                )
            }.onSuccess {
                rescheduleReminders()
                runCatching {
                    val todos = todoRepository.fetchTodosCached(mode = mode, listId = currentListId)
                    val lists = fetchListsForMode(mode)
                    todos to lists
                }.onSuccess { (todos, lists) ->
                    _uiState.update { current ->
                        current.copy(
                            lists = if (current.lists == lists) current.lists else lists,
                            items = if (current.items == todos) current.items else todos,
                            errorMessage = null,
                        )
                    }
                }.onFailure { refreshInternal(forceSync = false, showLoading = false) }
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = error.userFacingMessage(appContext, R.string.error_update_task_failed),
                )
            }
        }
    }

    private fun updateTaskInternal(
        visibleTodo: TodoItem,
        repositoryTodo: TodoItem,
        payload: CreateTaskPayload,
    ) {
        val normalizedTitle = payload.title.trim()
        if (normalizedTitle.isBlank()) return

        val normalizedPriority = when (payload.priority.trim()) {
            "Medium" -> "Medium"
            "High" -> "High"
            else -> "Low"
        }
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }

        val previousState = _uiState.value
        val mode = previousState.mode
        val normalizedDue = if (mode == TodoListMode.FLOATER) null else payload.due
        val currentListId = previousState.listId
        val updatedTodo = visibleTodo.copy(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            due = normalizedDue,
            rrule = payload.rrule,
            listId = normalizedListId,
        )

        _uiState.update { current ->
            val optimisticItems = current.items
                .map { item -> if (item.id == visibleTodo.id) updatedTodo else item }
                .filterNot { item ->
                    (current.mode == TodoListMode.LIST || current.mode == TodoListMode.FLOATER) &&
                        !current.listId.isNullOrBlank() &&
                            item.id == visibleTodo.id &&
                        item.listId != current.listId
                }
            current.copy(items = optimisticItems, errorMessage = null)
        }

        viewModelScope.launch {
            runCatching {
                val normalizedPayload = CreateTaskPayload(
                    title = normalizedTitle,
                    description = normalizedDescription,
                    priority = normalizedPriority,
                    due = normalizedDue,
                    rrule = if (mode == TodoListMode.FLOATER) null else payload.rrule,
                    listId = normalizedListId,
                )
                if (mode == TodoListMode.FLOATER) {
                    todoRepository.updateFloater(repositoryTodo, normalizedPayload)
                } else {
                    todoRepository.updateTodo(repositoryTodo, normalizedPayload)
                }
            }.onSuccess {
                if (mode != TodoListMode.FLOATER) rescheduleReminders()
                runCatching {
                    val todos = todoRepository.fetchTodosCached(mode = mode, listId = currentListId)
                    val lists = fetchListsForMode(mode)
                    todos to lists
                }.onSuccess { (todos, lists) ->
                    _uiState.update { current ->
                        current.copy(
                            lists = if (current.lists == lists) current.lists else lists,
                            items = if (current.items == todos) current.items else todos,
                            errorMessage = null,
                        )
                    }
                }.onFailure { refreshInternal(forceSync = false, showLoading = false) }
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = error.userFacingMessage(appContext, R.string.error_update_task_failed),
                )
            }
        }
    }

    fun toggleComplete(todo: TodoItem) {
        val previousItems = _uiState.value.items
        TdayTelemetry.addBreadcrumb(
            "task.complete",
            data = taskTelemetryData(mode = _uiState.value.mode),
        )
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                if (_uiState.value.mode == TodoListMode.FLOATER) {
                    todoRepository.completeFloater(todo)
                } else {
                    todoRepository.completeTodo(todo)
                }
            }.onSuccess {
                if (_uiState.value.mode != TodoListMode.FLOATER) rescheduleReminders()
                refreshInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        items = previousItems,
                        errorMessage = error.userFacingMessage(appContext, R.string.error_complete_task_failed),
                    )
                }
            }
        }
    }

    fun delete(todo: TodoItem, onDeleted: (() -> Unit)? = null) {
        val previousItems = _uiState.value.items
        val mode = _uiState.value.mode
        val listId = _uiState.value.listId
        TdayTelemetry.addBreadcrumb(
            "task.delete",
            data = taskTelemetryData(mode = mode),
        )
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                if (mode == TodoListMode.FLOATER) {
                    todoRepository.deleteFloater(todo)
                } else {
                    todoRepository.deleteTodo(todo)
                }
            }.onSuccess {
                onDeleted?.invoke()
                if (mode != TodoListMode.FLOATER) rescheduleReminders()
                runCatching {
                    val todos = todoRepository.fetchTodosCached(mode = mode, listId = listId)
                    val lists = fetchListsForMode(mode)
                    todos to lists
                }.onSuccess { (todos, lists) ->
                    _uiState.update { current ->
                        current.copy(
                            lists = if (current.lists == lists) current.lists else lists,
                            items = if (current.items == todos) current.items else todos,
                            errorMessage = null,
                        )
                    }
                }.onFailure { refreshInternal(forceSync = false, showLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        items = previousItems,
                        errorMessage = error.userFacingMessage(appContext, R.string.error_delete_task_failed),
                    )
                }
            }
        }
    }

    fun updateListSettings(
        listId: String,
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        val trimmedName = capitalizeFirstListLetter(name).trim()
        if (trimmedName.isBlank()) return

        val currentState = _uiState.value
        val resolvedListId = when {
            listId.isNotBlank() && currentState.lists.any { it.id == listId } -> listId
            !currentState.listId.isNullOrBlank() &&
                currentState.lists.any { it.id == currentState.listId } -> currentState.listId
            listId.isNotBlank() -> listId
            else -> return
        }
        Log.d(
            TAG,
            "updateListSettings requested hasColor=${color != null} hasIcon=${iconKey != null}",
        )
        TdayTelemetry.addBreadcrumb(
            "list.update",
            data = listTelemetryData(currentState.mode, color, iconKey),
        )

        val previousState = currentState
        _uiState.update { current ->
            current.copy(
                title = if (
                    current.mode == TodoListMode.LIST ||
                    (current.mode == TodoListMode.FLOATER && !current.listId.isNullOrBlank())
                ) {
                    trimmedName
                } else {
                    current.title
                },
                lists = current.lists.map { list ->
                    if (list.id == resolvedListId) {
                        list.copy(
                            name = trimmedName,
                            color = color ?: list.color,
                            iconKey = iconKey ?: list.iconKey,
                        )
                    } else {
                        list
                    }
                },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                if (currentState.mode == TodoListMode.FLOATER) {
                    floaterListRepository.updateList(
                        listId = resolvedListId,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    )
                } else {
                    listRepository.updateList(
                        listId = resolvedListId,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    )
            }
            }.onSuccess {
                Log.d(TAG, "updateListSettings persisted")
            }.onFailure { error ->
                Log.e(TAG, "updateListSettings failed", error)
                _uiState.value = previousState.copy(
                    errorMessage = error.userFacingMessage(appContext, R.string.error_update_list_failed),
                )
            }
        }
    }

    fun createList(name: String, color: String? = null, iconKey: String? = null) {
        val trimmedName = capitalizeFirstListLetter(name).trim()
        if (trimmedName.isBlank()) return

        val currentMode = _uiState.value.mode
        TdayTelemetry.addBreadcrumb(
            "list.create",
            data = listTelemetryData(currentMode, color, iconKey),
        )
        viewModelScope.launch {
            runCatching {
                if (currentMode == TodoListMode.FLOATER) {
                    floaterListRepository.createList(
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    )
                } else {
                    listRepository.createList(
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    )
                }
            }.onSuccess {
                hydrateFromCache(
                    mode = _uiState.value.mode,
                    listId = _uiState.value.listId,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_create_list_failed))
                }
            }
        }
    }

    fun deleteList(
        listId: String,
        onOptimisticDelete: () -> Unit,
    ) {
        val currentState = _uiState.value
        val resolvedListId = when {
            listId.isNotBlank() -> listId
            !currentState.listId.isNullOrBlank() -> currentState.listId
            else -> return
        }
        TdayTelemetry.addBreadcrumb(
            "list.delete",
            data = listTelemetryData(currentState.mode, color = null, iconKey = null),
        )

        viewModelScope.launch {
            runCatching {
                val optimisticDelete = {
                    _uiState.update { current ->
                        current.copy(
                            lists = current.lists.filterNot { it.id == resolvedListId },
                            items = current.items.filterNot { it.listId == resolvedListId },
                            errorMessage = null,
                        )
                    }
                    onOptimisticDelete()
                }

                if (currentState.mode == TodoListMode.FLOATER) {
                    floaterListRepository.deleteList(
                        listId = resolvedListId,
                        onOptimisticDelete = optimisticDelete,
                    )
                } else {
                    listRepository.deleteList(
                        listId = resolvedListId,
                        onOptimisticDelete = optimisticDelete,
                    )
                }
            }.onSuccess {
                if (currentState.mode != TodoListMode.FLOATER) rescheduleReminders()
            }.onFailure { error ->
                Log.e(TAG, "deleteList failed", error)
                _uiState.update {
                    it.copy(errorMessage = error.userFacingMessage(appContext, R.string.error_delete_list_failed))
                }
                hydrateFromCache(
                    mode = _uiState.value.mode,
                    listId = _uiState.value.listId,
                )
            }
        }
    }

    private fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    private suspend fun fetchListsForMode(mode: TodoListMode): List<ListSummary> {
        return if (mode == TodoListMode.FLOATER) {
            floaterListRepository.fetchLists()
        } else {
            listRepository.fetchLists()
        }
    }

    private fun fetchListsSnapshotForMode(mode: TodoListMode): List<ListSummary> {
        return if (mode == TodoListMode.FLOATER) {
            floaterListRepository.fetchListsSnapshot()
        } else {
            listRepository.fetchListsSnapshot()
        }
    }

    private fun modeTelemetryData(
        mode: TodoListMode = _uiState.value.mode,
        scopedList: Boolean = !_uiState.value.listId.isNullOrBlank(),
    ): Map<String, Any?> = mapOf(
        "mode" to mode.name.lowercase(),
        "scoped_list" to scopedList,
    )

    private fun taskTelemetryData(
        mode: TodoListMode,
        payload: CreateTaskPayload? = null,
        scope: TaskRescheduleScope? = null,
    ): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()
        data.putAll(modeTelemetryData(mode = mode))
        if (payload != null) {
            data["has_due"] = payload.due != null
            data["has_repeat"] = !payload.rrule.isNullOrBlank()
            data["has_list"] = !payload.listId.isNullOrBlank()
            data["has_description"] = !payload.description.isNullOrBlank()
        }
        if (scope != null) {
            data["scope"] = scope.name.lowercase()
        }
        return data
    }

    private fun listTelemetryData(
        mode: TodoListMode,
        color: String?,
        iconKey: String?,
    ): Map<String, Any?> = mapOf(
        "kind" to if (mode == TodoListMode.FLOATER) "floater" else "scheduled",
        "scoped_list" to !_uiState.value.listId.isNullOrBlank(),
        "has_color" to !color.isNullOrBlank(),
        "has_icon" to !iconKey.isNullOrBlank(),
    )

    private companion object {
        const val TAG = "TodoListViewModel"
    }
}
