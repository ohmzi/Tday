package com.ohmz.tday.compose.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.core.data.TdayRepository
import com.ohmz.tday.compose.core.model.NoteItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val isLoading: Boolean = false,
    val notes: List<NoteItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: TdayRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    fun load() {
        loadInternal(showLoading = true)
    }

    private fun loadInternal(showLoading: Boolean) {
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
                repository.fetchNotes()
            }.onSuccess { notes ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        notes = if (current.notes == notes) current.notes else notes,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load notes",
                    )
                }
            }
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createNote(name) }
                .onSuccess { loadInternal(showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not create note")
                    }
                }
        }
    }

    fun delete(noteId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(noteId) }
                .onSuccess { loadInternal(showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not delete note")
                    }
                }
        }
    }
}
