package com.ohmz.tday.compose.feature.todos

import android.util.Log
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoListUiState(
    val isLoading: Boolean = false,
    val title: String = "Tasks",
    val mode: TodoListMode = TodoListMode.TODAY,
    val listId: String? = null,
    val lists: List<ListSummary> = emptyList(),
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
                    repository.syncCachedData(
                        force = true,
                        replayPendingMutations = true,
                    ).onFailure { /* fall back to local cache */ }
                }
                val todos = repository.fetchTodos(mode = mode, listId = listId)
                val lists = repository.fetchLists()
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
                        errorMessage = error.message ?: "Failed to load tasks",
                    )
                }
            }
        }
    }

    fun addTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return
        val mode = _uiState.value.mode
        val listId = payload.listId

        viewModelScope.launch {
            runCatching {
                repository.createTodo(payload)
            }.onSuccess {
                runCatching {
                    val todos = repository.fetchTodosCached(mode = mode, listId = listId)
                    val lists = repository.fetchLists()
                    todos to lists
                }.onSuccess { (todos, lists) ->
                    _uiState.update { current ->
                        current.copy(
                            lists = if (current.lists == lists) current.lists else lists,
                            items = if (current.items == todos) current.items else todos,
                            errorMessage = null,
                        )
                    }
                }.onFailure {
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
                runCatching {
                    val todos = repository.fetchTodosCached(mode = mode, listId = listId)
                    val lists = repository.fetchLists()
                    todos to lists
                }.onSuccess { (todos, lists) ->
                    _uiState.update { current ->
                        current.copy(
                            lists = if (current.lists == lists) current.lists else lists,
                            items = if (current.items == todos) current.items else todos,
                            errorMessage = null,
                        )
                    }
                }.onFailure {
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

    fun updateListSettings(
        listId: String,
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        val trimmedName = name.trim()
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
            "updateListSettings requested rawId=$listId resolvedId=$resolvedListId name=$trimmedName color=$color iconKey=$iconKey",
        )

        val previousState = currentState
        _uiState.update { current ->
            current.copy(
                title = if (current.mode == TodoListMode.LIST) {
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
                repository.updateList(
                    listId = resolvedListId,
                    name = trimmedName,
                    color = color,
                    iconKey = iconKey,
                )
            }.onSuccess {
                Log.d(TAG, "updateListSettings persisted listId=$resolvedListId")
            }.onFailure { error ->
                Log.e(TAG, "updateListSettings failed listId=$resolvedListId", error)
                _uiState.value = previousState.copy(
                    errorMessage = error.message ?: "Could not update list",
                )
            }
        }
    }

    private companion object {
        const val TAG = "TodoListViewModel"
    }
}
