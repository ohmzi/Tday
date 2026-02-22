package com.ohmz.tday.compose.feature.calendar

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val isLoading: Boolean = false,
    val items: List<TodoItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
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
                    loadInternal(
                        forceSync = false,
                        showLoading = false,
                    )
                }
        }
    }

    fun load() {
        hasLoadedScreen = true
        loadInternal(
            forceSync = false,
            showLoading = false,
        )
    }

    fun refresh() {
        hasLoadedScreen = true
        loadInternal(
            forceSync = true,
            showLoading = true,
        )
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
                repository.fetchTodos(mode = TodoListMode.SCHEDULED)
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
                        errorMessage = error.message ?: "Failed to load calendar",
                    )
                }
            }
        }
    }
}
