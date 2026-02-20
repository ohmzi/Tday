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
                    TodoListMode.FLAGGED -> "Flagged"
                    TodoListMode.LIST -> listName ?: "List"
                },
            )
        }
        refresh()
    }

    fun refresh() {
        val mode = _uiState.value.mode
        val listId = _uiState.value.listId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.fetchTodos(mode = mode, listId = listId)
            }.onSuccess { todos ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = todos,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
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

        viewModelScope.launch {
            runCatching {
                repository.createTodo(title = title, listId = listId)
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Could not create task") }
            }
        }
    }

    fun toggleComplete(todo: TodoItem) {
        viewModelScope.launch {
            runCatching {
                repository.completeTodo(todo)
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Could not complete task") }
            }
        }
    }

    fun delete(todo: TodoItem) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTodo(todo)
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Could not delete task") }
            }
        }
    }

    fun togglePin(todo: TodoItem) {
        viewModelScope.launch {
            runCatching {
                repository.setTodoPinned(todo, !todo.pinned)
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Could not update pin") }
            }
        }
    }
}
