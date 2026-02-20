package com.ohmz.tday.compose.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.ListSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val errorMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                // Pull-to-refresh should force a remote sync before re-reading cached summary.
                repository.syncCachedData(force = true).onFailure { /* fall back to local cache */ }
                repository.fetchDashboardSummary()
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load dashboard",
                    )
                }
            }
        }
    }

    fun createList(
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createList(name, color = color, iconKey = iconKey) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not create list")
                    }
                }
        }
    }

    fun createTask(title: String, listId: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createTodo(title = title, listId = listId) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not create task")
                    }
                }
        }
    }

    val lists: List<ListSummary>
        get() = _uiState.value.summary.lists
}
