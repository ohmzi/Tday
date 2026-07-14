package com.ohmz.tday.shared.summary

/**
 * Which task view is being summarized. Mirrors the backend `SummaryScope` and the
 * web `TodoSummaryMode`, unified here so every platform filters identically.
 */
enum class SummaryScope(val responseMode: String, val usesFloaters: Boolean = false) {
    TODAY("today"),
    OVERDUE("overdue"),
    SCHEDULED("scheduled"),
    ALL("all"),
    PRIORITY("priority"),
    LIST("list"),
    FLOATER("floater", usesFloaters = true),
    WEEK("week");

    companion object {
        fun from(value: String?): SummaryScope? {
            return when (value?.trim()?.lowercase()) {
                null, "", "today" -> TODAY
                "overdue" -> OVERDUE
                "scheduled" -> SCHEDULED
                "all" -> ALL
                "priority" -> PRIORITY
                "list" -> LIST
                "floater", "anytime" -> FLOATER
                "week" -> WEEK
                else -> null
            }
        }
    }
}

/**
 * Platform-agnostic task the deterministic summary engine consumes. Each platform
 * maps its own model into this:
 *  - backend: `TodoResponse` / `FloaterResponse`
 *  - native: cached todos / floaters
 *
 * [dueEpochMs] is the absolute instant (UTC epoch millis) the task is due, or null
 * for an undated / "anytime" task. The engine zones it with the caller's timezone.
 */
data class SummaryTaskInput(
    val title: String,
    val priority: String?,
    val dueEpochMs: Long?,
    val pinned: Boolean = false,
    val recurring: Boolean = false,
    val listId: String? = null,
    val completed: Boolean = false,
    val kind: String = "task",
    /** When the task was completed (UTC epoch millis), for the WEEK retrospective. */
    val completedAtEpochMs: Long? = null,
)
