package com.ohmz.tday.compose.core.data.notes

import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.CreateNoteRequest
import com.ohmz.tday.compose.core.model.NoteItem
import com.ohmz.tday.compose.core.network.TdayApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val api: TdayApiService,
) {
    suspend fun fetchNotes(): List<NoteItem> {
        val response = requireApiBody(api.getNotes(), "Could not load notes")
        return response.notes.map {
            NoteItem(
                id = it.id,
                name = it.name,
                content = it.content,
            )
        }
    }

    suspend fun createNote(name: String) {
        requireApiBody(
            api.createNote(CreateNoteRequest(name = name.trim())),
            "Could not create note",
        )
    }

    suspend fun deleteNote(noteId: String) {
        requireApiBody(api.deleteNote(noteId), "Could not delete note")
    }
}
