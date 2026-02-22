package com.ohmz.tday.compose.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val isLoading: Boolean = false,
    val items: List<TodoItem> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        runCatching {
            CalendarUiState(
                isLoading = false,
                items = repository.fetchTodosSnapshot(mode = TodoListMode.SCHEDULED),
                lists = repository.fetchListsSnapshot(),
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
            repository.cacheDataVersion
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
        loadInternal(
            forceSync = true,
            showLoading = true,
        )
    }

    private fun hydrateFromCache() {
        runCatching {
            val todos = repository.fetchTodosSnapshot(mode = TodoListMode.SCHEDULED)
            val lists = repository.fetchListsSnapshot()
            todos to lists
        }.onSuccess { (todos, lists) ->
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    items = if (current.items == todos) current.items else todos,
                    lists = if (current.lists == lists) current.lists else lists,
                    errorMessage = null,
                )
            }
        }
    }

    private fun loadInternal(
        forceSync: Boolean,
        showLoading: Boolean,
    ) {
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
                    repository.syncCachedData(
                        force = true,
                        replayPendingMutations = false,
                    ).onFailure { /* fall back to cache */ }
                }
                val todos = repository.fetchTodos(mode = TodoListMode.SCHEDULED)
                val lists = repository.fetchLists()
                todos to lists
            }.onSuccess { (todos, lists) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = if (current.items == todos) current.items else todos,
                        lists = if (current.lists == lists) current.lists else lists,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load calendar",
                    )
                }
            }
        }
    }

    fun updateTask(
        todo: TodoItem,
        payload: CreateTaskPayload,
    ) {
        val normalizedTitle = payload.title.trim()
        if (normalizedTitle.isBlank()) return

        val normalizedPriority = when (payload.priority.trim()) {
            "Medium" -> "Medium"
            "High" -> "High"
            else -> "Low"
        }
        val normalizedStart = payload.dtstart
        val normalizedDue = if (payload.due > normalizedStart) {
            payload.due
        } else {
            normalizedStart.plusSeconds(60L * 60L)
        }
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }

        val previousState = _uiState.value
        val optimisticTodo = todo.copy(
            title = normalizedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            dtstart = normalizedStart,
            due = normalizedDue,
            rrule = payload.rrule,
            listId = normalizedListId,
        )

        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id == todo.id) optimisticTodo else item
                },
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                repository.updateTodo(
                    todo = todo,
                    payload = CreateTaskPayload(
                        title = normalizedTitle,
                        description = normalizedDescription,
                        priority = normalizedPriority,
                        dtstart = normalizedStart,
                        due = normalizedDue,
                        rrule = payload.rrule,
                        listId = normalizedListId,
                    ),
                )
            }.onSuccess {
                loadInternal(
                    forceSync = false,
                    showLoading = false,
                )
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = error.message ?: "Could not update task",
                )
            }
        }
    }

    fun delete(
        todo: TodoItem,
        onDeleted: (() -> Unit)? = null,
    ) {
        val previousItems = _uiState.value.items
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.deleteTodo(todo)
            }.onSuccess {
                onDeleted?.invoke()
                loadInternal(
                    forceSync = false,
                    showLoading = false,
                )
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        items = previousItems,
                        errorMessage = error.message ?: "Could not delete task",
                    )
                }
            }
        }
    }
}
