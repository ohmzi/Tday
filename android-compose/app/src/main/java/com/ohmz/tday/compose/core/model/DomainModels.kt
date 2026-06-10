package com.ohmz.tday.compose.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

enum class TodoListMode {
    TODAY,
    OVERDUE,
    SCHEDULED,
    ALL,
    PRIORITY,
    FLOATER,
    LIST,
}

enum class TaskRescheduleScope {
    OCCURRENCE,
    SERIES,
}

@Immutable
data class CreateTaskPayload(
    val title: String,
    val description: String? = null,
    val priority: String = "Low",
    val due: Instant?,
    val rrule: String? = null,
    val listId: String? = null,
)

@Immutable
data class TodoItem(
    val id: String,
    val canonicalId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val due: Instant?,
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

fun TodoListMode.supportsTaskReschedule(): Boolean {
    return when (this) {
        TodoListMode.TODAY,
        TodoListMode.SCHEDULED,
        TodoListMode.ALL,
        TodoListMode.PRIORITY,
        TodoListMode.LIST,
            -> true

        TodoListMode.FLOATER,
        TodoListMode.OVERDUE,
            -> false
    }
}

fun TodoItem.repositoryTargetForReschedule(scope: TaskRescheduleScope): TodoItem {
    return when (scope) {
        TaskRescheduleScope.OCCURRENCE -> this
        TaskRescheduleScope.SERIES -> copy(id = canonicalId, instanceDate = null)
    }
}

fun movedDuePreservingTime(
    due: Instant,
    targetDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Instant {
    val dueTime = due.atZone(zoneId).toLocalTime()
    return ZonedDateTime.of(targetDate, dueTime, zoneId).toInstant()
}

// Keep the task's date, set its time-of-day to [hour]:00. The inverse of
// [movedDuePreservingTime]; used by the Today screen's drag-between-time-buckets
// interaction (Morning / Afternoon / Tonight).
fun movedDueToTimeOfDay(
    due: Instant,
    hour: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Instant {
    val date = due.atZone(zoneId).toLocalDate()
    return ZonedDateTime.of(date, LocalTime.of(hour, 0), zoneId).toInstant()
}

fun createMovedTaskPayload(
    todo: TodoItem,
    targetDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CreateTaskPayload {
    val due = todo.due ?: ZonedDateTime.now(zoneId).toInstant()
    return CreateTaskPayload(
        title = todo.title,
        description = todo.description,
        priority = todo.priority,
        due = movedDuePreservingTime(due, targetDate, zoneId),
        rrule = todo.rrule,
        listId = todo.listId,
    )
}

fun timelineRescheduleTargetDate(
    sectionKey: String,
    today: LocalDate = LocalDate.now(),
): LocalDate? {
    val currentMonth = YearMonth.from(today)
    if (sectionKey == "earlier") {
        return today.minusDays(1)
    }

    if (sectionKey.startsWith("day-")) {
        val date = runCatching { LocalDate.parse(sectionKey.removePrefix("day-")) }.getOrNull()
            ?: return null
        return date.takeIf { YearMonth.from(it) >= currentMonth }
    }

    if (sectionKey.startsWith("rest-")) {
        val month = runCatching { YearMonth.parse(sectionKey.removePrefix("rest-")) }.getOrNull()
            ?: return null
        val horizonStart = today.plusDays(7)
        return horizonStart.takeIf {
            month == currentMonth && YearMonth.from(it) == month
        }
    }

    if (sectionKey.startsWith("month-")) {
        val month = runCatching { YearMonth.parse(sectionKey.removePrefix("month-")) }.getOrNull()
            ?: return null
        return month.takeIf { it >= currentMonth }?.atDay(1)
    }

    return null
}

@Immutable
data class ListSummary(
    val id: String,
    val name: String,
    val color: String?,
    val iconKey: String?,
    val todoCount: Int,
    val updatedAt: Instant? = null,
    val createdAt: Instant? = null,
    // Sharing metadata; legacy servers omit it, in which case the user owns the list.
    val myRole: String = "OWNER",
    val isShared: Boolean = false,
    val memberCount: Int = 0,
    val ownerUsername: String? = null,
) {
    val isViewer: Boolean
        get() = myRole.equals("VIEWER", ignoreCase = true)

    val isOwner: Boolean
        get() = myRole.equals("OWNER", ignoreCase = true)
}

@Immutable
data class DashboardSummary(
    val todayCount: Int,
    val scheduledCount: Int,
    val allCount: Int,
    val priorityCount: Int,
    val floaterCount: Int,
    val completedCount: Int,
    val lists: List<ListSummary>,
)

@Immutable
data class CompletedItem(
    val id: String,
    val originalTodoId: String?,
    val title: String,
    val description: String? = null,
    val priority: String,
    val due: Instant?,
    val completedAt: Instant? = null,
    val rrule: String?,
    val instanceDate: Instant?,
    val listId: String? = null,
    val listName: String? = null,
    val listColor: String? = null,
)

@Immutable
data class RegisterOutcome(
    val success: Boolean,
    val requiresApproval: Boolean,
    val message: String,
)

@Stable
sealed interface AuthResult {
    data object Success : AuthResult
    data object PendingApproval : AuthResult
    data class Error(val message: String) : AuthResult
}

@Stable
sealed interface PasswordResetOutcome {
    data object Success : PasswordResetOutcome
    data class Failed(val message: String) : PasswordResetOutcome
    data object Locked : PasswordResetOutcome
}

/** Result of verifying security answers without resetting (the staged reset wizard's gate). */
@Stable
sealed interface VerifyAnswersOutcome {
    data object Valid : VerifyAnswersOutcome
    data class Invalid(val results: List<SecurityAnswerResult>) : VerifyAnswersOutcome
    data object Locked : VerifyAnswersOutcome
    data class Error(val message: String) : VerifyAnswersOutcome
}
