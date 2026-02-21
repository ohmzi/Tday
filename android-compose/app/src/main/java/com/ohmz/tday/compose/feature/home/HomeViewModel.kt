package com.ohmz.tday.compose.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.CreateTaskPayload
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
        refreshFromCache()
    }

    fun refreshFromCache() {
        viewModelScope.launch {
            runCatching {
                repository.fetchDashboardSummaryCached()
            }.onSuccess { summary ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        summary = if (current.summary == summary) current.summary else summary,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load dashboard",
                    )
                }
            }
        }
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
                    // Pull-to-refresh should force a remote sync before re-reading cached summary.
                    repository.syncCachedData(
                        force = true,
                        replayPendingMutations = true,
                    ).onFailure { /* fall back to local cache */ }
                }
                repository.fetchDashboardSummary()
            }.onSuccess { summary ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        summary = if (current.summary == summary) current.summary else summary,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
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
                .onSuccess {
                    refreshInternal(
                        forceSync = false,
                        showLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not create list")
                    }
                }
        }
    }

    fun createTask(payload: CreateTaskPayload) {
        if (payload.title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createTodo(payload) }
                .onSuccess {
                    runCatching { repository.fetchDashboardSummaryCached() }
                        .onSuccess { summary ->
                            _uiState.update { current ->
                                current.copy(
                                    summary = if (current.summary == summary) current.summary else summary,
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
                }
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
