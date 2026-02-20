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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.fetchNotes()
            }.onSuccess { notes ->
                _uiState.update { it.copy(isLoading = false, notes = notes) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load notes")
                }
            }
        }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createNote(name) }
                .onSuccess { load() }
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
                .onSuccess { load() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Could not delete note")
                    }
                }
        }
    }
}
