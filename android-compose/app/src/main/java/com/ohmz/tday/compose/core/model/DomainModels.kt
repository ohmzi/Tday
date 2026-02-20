package com.ohmz.tday.compose.core.model

import java.time.Instant

enum class TodoListMode {
    TODAY,
    SCHEDULED,
    ALL,
    PRIORITY,
    LIST,
}

data class CreateTaskPayload(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val dtstart: Instant,
    val due: Instant,
    val rrule: String? = null,
    val listId: String? = null,
)

data class TodoItem(
    val id: String,
    val canonicalId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dtstart: Instant,
    val due: Instant,
    val rrule: String?,
    val instanceDate: Instant?,
    val pinned: Boolean,
    val completed: Boolean,
    val listId: String?,
    val updatedAt: Instant? = null,
) {
    val isRecurring: Boolean
        get() = !rrule.isNullOrBlank()

    val instanceDateEpochMillis: Long?
        get() = instanceDate?.toEpochMilli()
}

data class ListSummary(
    val id: String,
    val name: String,
    val color: String?,
    val iconKey: String?,
    val todoCount: Int,
    val updatedAt: Instant? = null,
)

data class DashboardSummary(
    val todayCount: Int,
    val scheduledCount: Int,
    val allCount: Int,
    val priorityCount: Int,
    val completedCount: Int,
    val lists: List<ListSummary>,
)

data class CompletedItem(
    val id: String,
    val originalTodoId: String?,
    val title: String,
    val priority: String,
    val due: Instant,
    val rrule: String?,
    val instanceDate: Instant?,
)

data class NoteItem(
    val id: String,
    val name: String,
    val content: String?,
)

data class RegisterOutcome(
    val success: Boolean,
    val requiresApproval: Boolean,
    val message: String,
)

sealed interface AuthResult {
    data object Success : AuthResult
    data object PendingApproval : AuthResult
    data class Error(val message: String) : AuthResult
}
