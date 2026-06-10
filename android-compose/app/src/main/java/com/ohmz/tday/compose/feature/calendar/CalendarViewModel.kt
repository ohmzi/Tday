package com.ohmz.tday.compose.feature.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.completed.CompletedRepository
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TaskRescheduleScope
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.movedDuePreservingTime
import com.ohmz.tday.compose.core.model.repositoryTargetForReschedule
import com.ohmz.tday.compose.core.notification.TaskReminderScheduler
import com.ohmz.tday.compose.core.observability.TdayTelemetry
import com.ohmz.tday.compose.core.ui.SnackbarManager
import com.ohmz.tday.compose.core.ui.UndoableDeleteCoordinator
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
import java.time.LocalDate
import javax.inject.Inject

data class CalendarUiState(
    val isLoading: Boolean = false,
    val items: List<TodoItem> = emptyList(),
    val completedItems: List<CompletedItem> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val completedRepository: CompletedRepository,
    private val listRepository: ListRepository,
    private val syncManager: SyncManager,
    private val cacheManager: OfflineCacheManager,
    private val reminderScheduler: TaskReminderScheduler,
    private val snackbarManager: SnackbarManager,
    private val undoableDeleteCoordinator: UndoableDeleteCoordinator,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

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
            CalendarUiState(
                isLoading = false,
                items = todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL)
                    .filter { it.due != null },
                completedItems = completedRepository.fetchCompletedItemsSnapshot(),
                lists = listRepository.fetchListsSnapshot(),
                errorMessage = null,
            )
        }.getOrElse { CalendarUiState() },
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
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
        TdayTelemetry.addBreadcrumb("calendar.load", data = calendarTelemetryData())
        hydrateFromCache()
    }

    fun refresh() {
        hasLoadedScreen = true
        TdayTelemetry.addBreadcrumb("calendar.refresh", data = calendarTelemetryData())
        loadInternal(forceSync = true, showLoading = true)
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

    private fun hydrateFromCache() {
        runCatching {
            val todos =
                todoRepository.fetchTodosSnapshot(mode = TodoListMode.ALL).filter { it.due != null }
            val completedItems = completedRepository.fetchCompletedItemsSnapshot()
            val lists = listRepository.fetchListsSnapshot()
            Triple(todos, completedItems, lists)
        }.onSuccess { (todos, completedItems, lists) ->
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    items = if (current.items == todos) current.items else todos,
                    completedItems = if (current.completedItems == completedItems) {
                        current.completedItems
                    } else {
                        completedItems
                    },
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
                        .onFailure { /* fall back to cache */ }
                }
                val todos =
                    todoRepository.fetchTodos(mode = TodoListMode.ALL).filter { it.due != null }
                val completedItems = completedRepository.fetchCompletedItems()
                val lists = listRepository.fetchLists()
                Triple(todos, completedItems, lists)
            }.onSuccess { (todos, completedItems, lists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = if (current.items == todos) current.items else todos,
                        completedItems = if (current.completedItems == completedItems) {
                            current.completedItems
                        } else {
                            completedItems
                        },
                        lists = if (current.lists == lists) current.lists else lists,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.userFacingMessage(appContext, R.string.error_load_calendar_failed),
                    )
                }
            }
        }
    }

    fun createTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return
        TdayTelemetry.addBreadcrumb(
            "calendar.task.create",
            data = calendarTelemetryData(payload),
        )
        viewModelScope.launch {
            runCatching {
                todoRepository.createTodo(payload)
            }.onSuccess {
                rescheduleReminders()
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        errorMessage = mutationFailureMessage(
                            error,
                            R.string.error_create_task_failed
                        )
                    )
                }
            }
        }
    }

    fun complete(todo: TodoItem) {
        val previousItems = _uiState.value.items
        TdayTelemetry.addBreadcrumb("calendar.task.complete", data = calendarTelemetryData())
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                todoRepository.completeTodo(todo)
            }.onSuccess {
                rescheduleReminders()
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        items = previousItems,
                        errorMessage = mutationFailureMessage(
                            error,
                            R.string.error_complete_task_failed
                        ),
                    )
                }
            }
        }
    }

    fun uncomplete(item: CompletedItem) {
        val previousCompletedItems = _uiState.value.completedItems
        TdayTelemetry.addBreadcrumb("calendar.task.restore", data = calendarTelemetryData())
        _uiState.update { current ->
            current.copy(
                completedItems = current.completedItems.filterNot { it.id == item.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                completedRepository.uncomplete(item)
            }.onSuccess {
                rescheduleReminders()
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        completedItems = previousCompletedItems,
                        errorMessage = mutationFailureMessage(
                            error,
                            R.string.error_restore_task_failed
                        ),
                    )
                }
            }
        }
    }

    fun updateTask(todo: TodoItem, payload: CreateTaskPayload) {
        TdayTelemetry.addBreadcrumb(
            "calendar.task.update",
            data = calendarTelemetryData(payload),
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
        val updatedTodo = todo.copy(due = movedDue)
        TdayTelemetry.addBreadcrumb(
            "calendar.task.reschedule",
            data = calendarTelemetryData() + mapOf("scope" to scope.name.lowercase()),
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
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = mutationFailureMessage(error, R.string.error_update_task_failed),
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

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDue = payload.due
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }

        val previousState = _uiState.value
        val optimisticTodo = visibleTodo.copy(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            due = normalizedDue,
            rrule = payload.rrule,
            listId = normalizedListId,
        )

        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id == visibleTodo.id) optimisticTodo else item
                },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                todoRepository.updateTodo(
                    todo = repositoryTodo,
                    payload = CreateTaskPayload(
                        title = normalizedTitle,
                        description = normalizedDescription,
                        priority = normalizedPriority,
                        due = normalizedDue,
                        rrule = payload.rrule,
                        listId = normalizedListId,
                    ),
                )
            }.onSuccess {
                rescheduleReminders()
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = mutationFailureMessage(error, R.string.error_update_task_failed),
                )
            }
        }
    }

    fun delete(todo: TodoItem) {
        val previousItems = _uiState.value.items
        TdayTelemetry.addBreadcrumb("calendar.task.delete", data = calendarTelemetryData())
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            // Delayed-commit delete: stage now (local-only removal), show the
            // undoable toast, and let the coordinator run the real delete after
            // the toast window — or restore the staged state on Undo.
            runCatching {
                todoRepository.stageDeleteTodo(todo)
            }.onSuccess { staged ->
                undoableDeleteCoordinator.showUndoableDelete(
                    message = appContext.getString(R.string.task_deleted_toast),
                    onCommit = { todoRepository.deleteTodo(todo) },
                    onUndo = {
                        todoRepository.undoStagedTodoDeletion(staged)
                        // Runs on the coordinator scope: this ViewModel may be gone
                        // by the time Undo restores a reminder-bearing task.
                        runCatching { reminderScheduler.rescheduleAll() }
                    },
                )
                rescheduleReminders()
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        items = previousItems,
                        errorMessage = mutationFailureMessage(
                            error,
                            R.string.error_delete_task_failed
                        ),
                    )
                }
            }
        }
    }

    private fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { reminderScheduler.rescheduleAll() }
        }
    }

    private fun calendarTelemetryData(payload: CreateTaskPayload? = null): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "surface" to "calendar",
            "scheduled_items" to _uiState.value.items.size,
            "completed_items" to _uiState.value.completedItems.size,
        )
        if (payload != null) {
            data["has_due"] = payload.due != null
            data["has_repeat"] = !payload.rrule.isNullOrBlank()
            data["has_list"] = !payload.listId.isNullOrBlank()
            data["has_description"] = !payload.description.isNullOrBlank()
        }
        return data
    }
}
