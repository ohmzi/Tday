package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class TodosResponse(
    val todos: List<TodoDto> = emptyList(),
)

@Serializable
data class TodoSummaryRequest(
    val mode: String = "today",
    val listId: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class TodoSummaryResponse(
    val summary: String? = null,
    val source: String? = null,
    val mode: String? = null,
    val taskCount: Int? = null,
    val generatedAt: String? = null,
    val fallbackReason: String? = null,
    val reason: String? = null,
)

@Serializable
data class TodoTitleNlpRequest(
    val text: String,
    val locale: String? = null,
    val referenceEpochMs: Long? = null,
    val timezoneOffsetMinutes: Int? = null,
    val defaultDurationMinutes: Int? = null,
)

@Serializable
data class TodoTitleNlpResponse(
    val cleanTitle: String,
    val matchedText: String? = null,
    val matchStart: Int? = null,
    val startEpochMs: Long? = null,
    val dueEpochMs: Long? = null,
)

@Serializable
data class CreateTodoRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val dtstart: String,
    val due: String,
    val rrule: String? = null,
    val listID: String? = null,
)

@Serializable
data class TodoDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val pinned: Boolean = false,
    val priority: String = "Low",
    val dtstart: String,
    val due: String,
    val durationMinutes: Int? = null,
    val rrule: String? = null,
    val timeZone: String? = null,
    val instanceDate: String? = null,
    val completed: Boolean = false,
    val order: Int? = null,
    val listID: String? = null,
    val userID: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CreateTodoResponse(
    val message: String? = null,
    val todo: TodoDto? = null,
)

@Serializable
data class UpdateTodoRequest(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val pinned: Boolean? = null,
    val priority: String? = null,
    val completed: Boolean? = null,
    val dtstart: String? = null,
    val due: String? = null,
    val rrule: String? = null,
    val listID: String? = null,
    val dateChanged: Boolean? = null,
    val rruleChanged: Boolean? = null,
    val instanceDate: String? = null,
)

@Serializable
data class DeleteTodoRequest(
    val id: String,
)

@Serializable
data class TodoCompleteRequest(
    val id: String,
    val instanceDate: String? = null,
)

@Serializable
data class TodoUncompleteRequest(
    val id: String,
    val instanceDate: String? = null,
)

@Serializable
data class TodoPrioritizeRequest(
    val id: String,
    val priority: String,
    val instanceDate: String? = null,
)

@Serializable
data class TodoReorderRequest(
    val id: String,
    val order: Int,
)

@Serializable
data class TodoInstancePatchRequest(
    val todoId: String,
    val instanceDate: String,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val dtstart: String? = null,
    val due: String? = null,
    val durationMinutes: Int? = null,
)

@Serializable
data class TodoInstanceDeleteRequest(
    val todoId: String,
    val instanceDate: String,
)
