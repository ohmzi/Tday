package com.ohmz.tday.domain

import com.ohmz.tday.models.response.*
import kotlinx.serialization.Serializable

@Serializable
sealed class DomainEvent {
    @Serializable
    data class TodoCreated(val todo: TodoResponse) : DomainEvent()

    @Serializable
    data class TodoUpdated(val todo: TodoResponse) : DomainEvent()

    @Serializable
    data class TodoDeleted(val id: String) : DomainEvent()

    @Serializable
    data class ListChanged(val list: ListResponse) : DomainEvent()

    @Serializable
    data class NoteChanged(val note: NoteResponse) : DomainEvent()
}
