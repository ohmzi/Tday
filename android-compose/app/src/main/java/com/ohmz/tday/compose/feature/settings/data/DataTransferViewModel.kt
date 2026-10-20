package com.ohmz.tday.compose.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.export.DataTransferRepository
import com.ohmz.tday.shared.model.ImportCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Transient outcome of an export/import, surfaced once in the card. */
sealed interface DataTransferMessage {
    data object ExportDone : DataTransferMessage
    data class ImportDone(val count: Int) : DataTransferMessage
    data class Error(val detail: String?) : DataTransferMessage
}

data class DataTransferUiState(
    val loading: Boolean = true,
    val isLocalMode: Boolean = false,
    val taskCount: Int = 0,
    val listCount: Int = 0,
    val completedCount: Int = 0,
    val busy: Boolean = false,
    /** Non-null while a dry-run preview awaits the user's confirmation. */
    val preview: ImportCounts? = null,
    val message: DataTransferMessage? = null,
)

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val repository: DataTransferRepository,
    private val cacheManager: OfflineCacheManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataTransferUiState())
    val uiState: StateFlow<DataTransferUiState> = _uiState.asStateFlow()

    private var pendingJson: String? = null

    init {
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val state = cacheManager.loadOfflineState()
            _uiState.update {
                it.copy(
                    loading = false,
                    isLocalMode = repository.isLocalMode(),
                    taskCount = state.todos.size + state.floaters.size,
                    listCount = state.lists.size + state.floaterLists.size,
                    completedCount = state.completedItems.size + state.completedFloaters.size,
                )
            }
        }
    }

    /** Builds the bundle and hands it to [write] (which persists it to the SAF uri). */
    fun export(write: suspend (String) -> Unit) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching {
                val json = repository.buildExportJson()
                write(json)
            }.onSuccess {
                _uiState.update { it.copy(busy = false, message = DataTransferMessage.ExportDone) }
            }.onFailure { error ->
                _uiState.update { it.copy(busy = false, message = DataTransferMessage.Error(error.message)) }
            }
        }
    }

    /** Dry-run the picked file to preview counts before writing anything. */
    fun preview(rawJson: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching { repository.importJson(rawJson, dryRun = true) }
                .onSuccess { response ->
                    pendingJson = rawJson
                    _uiState.update { it.copy(busy = false, preview = response.imported) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false, message = DataTransferMessage.Error(error.message)) }
                }
        }
    }

    fun cancelImport() {
        pendingJson = null
        _uiState.update { it.copy(preview = null) }
    }

    fun confirmImport() {
        val json = pendingJson ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, preview = null) }
            runCatching { repository.importJson(json, dryRun = false) }
                .onSuccess { response ->
                    pendingJson = null
                    _uiState.update {
                        it.copy(busy = false, message = DataTransferMessage.ImportDone(response.imported.total()))
                    }
                    refreshCounts()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(busy = false, message = DataTransferMessage.Error(error.message)) }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

/** Rows an import added, for the success message and confirm dialog. */
internal fun ImportCounts.total(): Int =
    lists + floaterLists + todos + floaters + completedTodos + completedFloaters
