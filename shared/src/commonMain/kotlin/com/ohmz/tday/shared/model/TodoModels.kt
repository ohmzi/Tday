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
    val locale: String? = null,
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
data class BrainDumpRequest(
    val text: String,
    val timeZone: String? = null,
    val locale: String? = null,
)

/** One candidate task parsed from a brain-dump fragment. Dated → Todo, undated → Floater. */
@Serializable
data class BrainDumpCandidate(
    val title: String,
    val dueEpochMs: Long? = null,
    val rrule: String? = null,
    val priority: String? = null,
)

@Serializable
data class BrainDumpResponse(
    val candidates: List<BrainDumpCandidate>,
)

@Serializable
data class TodoTitleNlpResponse(
    val cleanTitle: String,
    val matchedText: String? = null,
    val matchStart: Int? = null,
    val dueEpochMs: Long? = null,
    /** A preset RRULE captured from a phrase like "every day" (null when none). */
    val rrule: String? = null,
    /** A priority captured from "!"/"!!"/"high|low|medium" (null when none). */
    val priority: String? = null,
)

@Serializable
data class CreateTodoRequest(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
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
    val due: String,
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

/**
 * Response of `POST /api/todo/{id}/demote` — lets a stale todo float: the todo
 * row is consumed and an Anytime floater takes its place. Recurring todos are
 * rejected (their series would be silently destroyed).
 */
@Serializable
data class DemoteTodoResponse(
    val message: String? = null,
    val floater: FloaterDto? = null,
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
    val due: String? = null,
)

@Serializable
data class TodoInstanceDeleteRequest(
    val todoId: String,
    val instanceDate: String,
)
