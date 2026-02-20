package com.ohmz.tday.compose.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoListUiState(
    val isLoading: Boolean = false,
    val title: String = "Tasks",
    val mode: TodoListMode = TodoListMode.TODAY,
    val listId: String? = null,
    val items: List<TodoItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoListUiState())
    val uiState: StateFlow<TodoListUiState> = _uiState.asStateFlow()

    fun load(mode: TodoListMode, listId: String? = null, listName: String? = null) {
        _uiState.update {
            it.copy(
                mode = mode,
                listId = listId,
                title = when (mode) {
                    TodoListMode.TODAY -> "Today"
                    TodoListMode.SCHEDULED -> "Scheduled"
                    TodoListMode.ALL -> "All Tasks"
                    TodoListMode.PRIORITY -> "Priority"
                    TodoListMode.LIST -> listName ?: "List"
                },
            )
        }
        refreshInternal(
            forceSync = false,
            showLoading = false,
        )
    }

    fun refresh() {
        refreshInternal(
            forceSync = true,
            showLoading = true,
        )
    }

    private fun refreshInternal(
        forceSync: Boolean,
        showLoading: Boolean,
    ) {
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
                    // Pull-to-refresh should fetch latest server state first.
                    repository.syncCachedData(force = true).onFailure { /* fall back to local cache */ }
                }
                repository.fetchTodos(mode = mode, listId = listId)
            }.onSuccess { todos ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = if (current.items == todos) current.items else todos,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load tasks",
                    )
                }
            }
        }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        val listId = _uiState.value.listId
        val mode = _uiState.value.mode

        viewModelScope.launch {
            runCatching {
                repository.createTodo(title = title, listId = listId)
            }.onSuccess {
                runCatching { repository.fetchTodosCached(mode = mode, listId = listId) }
                    .onSuccess { todos ->
                        _uiState.update { current ->
                            current.copy(
                                items = if (current.items == todos) current.items else todos,
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure {
                        refreshInternal(
                            forceSync = false,
                            showLoading = false,
                        )
                    }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Could not create task") }
            }
        }
    }

    fun toggleComplete(todo: TodoItem) {
        val previousItems = _uiState.value.items
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.completeTodo(todo)
            }.onSuccess {
                refreshInternal(
                    forceSync = false,
                    showLoading = false,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        items = previousItems,
                        errorMessage = error.message ?: "Could not complete task",
                    )
                }
            }
        }
    }

    fun delete(todo: TodoItem) {
        val previousItems = _uiState.value.items
        val mode = _uiState.value.mode
        val listId = _uiState.value.listId
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
                runCatching { repository.fetchTodosCached(mode = mode, listId = listId) }
                    .onSuccess { todos ->
                        _uiState.update { current ->
                            current.copy(
                                items = if (current.items == todos) current.items else todos,
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure {
                        refreshInternal(
                            forceSync = false,
                            showLoading = false,
                        )
                    }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        items = previousItems,
                        errorMessage = error.message ?: "Could not delete task",
                    )
                }
            }
        }
    }

    fun togglePin(todo: TodoItem) {
        val targetPinned = !todo.pinned
        _uiState.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id == todo.id) item.copy(pinned = targetPinned) else item
                },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.setTodoPinned(todo, targetPinned)
            }.onSuccess {
                // Keep the local optimistic state; background sync reconciles if needed.
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        items = current.items.map { item ->
                            if (item.id == todo.id) item.copy(pinned = todo.pinned) else item
                        },
                        errorMessage = error.message ?: "Could not update pin",
                    )
                }
            }
        }
    }
}
