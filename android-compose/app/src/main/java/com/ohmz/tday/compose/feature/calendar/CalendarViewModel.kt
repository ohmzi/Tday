package com.ohmz.tday.compose.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.completed.CompletedRepository
import com.ohmz.tday.compose.core.data.list.ListRepository
import com.ohmz.tday.compose.core.data.todo.TodoRepository
import com.ohmz.tday.compose.core.domain.CompleteTodoUseCase
import com.ohmz.tday.compose.core.domain.CreateTodoUseCase
import com.ohmz.tday.compose.core.domain.SyncAndRefreshUseCase
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
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
    val completedItems: List<CompletedItem> = emptyList(),
    val lists: List<ListSummary> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val completedRepository: CompletedRepository,
    private val listRepository: ListRepository,
    private val syncAndRefresh: SyncAndRefreshUseCase,
    private val createTodoUseCase: CreateTodoUseCase,
    private val completeTodoUseCase: CompleteTodoUseCase,
    private val cacheManager: OfflineCacheManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        runCatching {
            CalendarUiState(
                isLoading = false,
                items = todoRepository.fetchTodosSnapshot(mode = TodoListMode.SCHEDULED),
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
        hydrateFromCache()
    }

    fun refresh() {
        hasLoadedScreen = true
        loadInternal(forceSync = true, showLoading = true)
    }

    suspend fun parseTaskTitleNlp(
        text: String,
        referenceStartEpochMs: Long,
        referenceDueEpochMs: Long,
    ): TodoTitleNlpResponse? {
        return todoRepository.parseTodoTitleNlp(
            text = text,
            referenceStartEpochMs = referenceStartEpochMs,
            referenceDueEpochMs = referenceDueEpochMs,
        )
    }

    private fun hydrateFromCache() {
        runCatching {
            val todos = todoRepository.fetchTodosSnapshot(mode = TodoListMode.SCHEDULED)
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
                    syncAndRefresh(force = true, replayPendingMutations = false)
                        .onFailure { /* fall back to cache */ }
                }
                val todos = todoRepository.fetchTodos(mode = TodoListMode.SCHEDULED)
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
                        errorMessage = error.message ?: "Failed to load calendar",
                    )
                }
            }
        }
    }

    fun createTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                createTodoUseCase(payload)
            }.onSuccess {
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(errorMessage = error.message ?: "Could not create task")
                }
            }
        }
    }

    fun complete(todo: TodoItem) {
        val previousItems = _uiState.value.items
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                completeTodoUseCase(todo)
            }.onSuccess {
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        items = previousItems,
                        errorMessage = error.message ?: "Could not complete task",
                    )
                }
            }
        }
    }

    fun uncomplete(item: CompletedItem) {
        val previousCompletedItems = _uiState.value.completedItems
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
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        completedItems = previousCompletedItems,
                        errorMessage = error.message ?: "Could not restore task",
                    )
                }
            }
        }
    }

    fun updateTask(todo: TodoItem, payload: CreateTaskPayload) {
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
                todoRepository.updateTodo(
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
                loadInternal(forceSync = false, showLoading = false)
            }.onFailure { error ->
                _uiState.value = previousState.copy(
                    errorMessage = error.message ?: "Could not update task",
                )
            }
        }
    }

    fun delete(todo: TodoItem, onDeleted: (() -> Unit)? = null) {
        val previousItems = _uiState.value.items
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot { it.id == todo.id },
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                todoRepository.deleteTodo(todo)
            }.onSuccess {
                onDeleted?.invoke()
                loadInternal(forceSync = false, showLoading = false)
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
