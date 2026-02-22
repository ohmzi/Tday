package com.ohmz.tday.compose.feature.completed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.CompletedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompletedUiState(
    val isLoading: Boolean = false,
    val items: List<CompletedItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class CompletedViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompletedUiState())
    val uiState: StateFlow<CompletedUiState> = _uiState.asStateFlow()
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
                    // Keep completed list aligned with latest server state on refresh.
                    repository.syncCachedData(
                        force = true,
                        replayPendingMutations = false,
                    ).onFailure { /* fall back to local cache */ }
                }
                repository.fetchCompletedItems()
            }.onSuccess { items ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = if (current.items == items) current.items else items,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load",
                    )
                }
            }
        }
    }

    fun uncomplete(item: CompletedItem) {
        viewModelScope.launch {
            runCatching { repository.uncomplete(item) }
                .onSuccess {
                    loadInternal(
                        forceSync = false,
                        showLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not restore task")
                    }
                }
        }
    }
}
