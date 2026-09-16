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
    /**
     * Last write to the task (UTC epoch millis), for the undated "Anytime" summary only.
     *
     * Defaulted because it is a signal, not a requirement: a caller that does not have it gets
     * a summary that simply never mentions dormancy, rather than one that guesses. It feeds
     * [com.ohmz.tday.shared.floater.FloaterResting.tierFor], the same clock the clients already
     * fade and group resting floaters by, so the summary and the list agree about what has gone
     * quiet. It is a last-write clock, never a created-at — see FloaterSummaryPlanner for why
     * that rules out naming the "oldest" floater.
     */
    val updatedAtEpochMs: Long? = null,
)

/** Rank used to order tasks by priority: High/Urgent/Important > Medium > everything else. */
internal const val HIGH_PRIORITY_RANK = 3
internal const val MEDIUM_PRIORITY_RANK = 2

/**
 * Shared by the engine's ranking and [FloaterSummaryPlanner], so "high priority" means one
 * thing in this module. Unknown or absent priorities degrade to the lowest rank rather than
 * inventing importance.
 */
internal fun priorityRankOf(priority: String?): Int =
    when ((priority ?: "Low").trim().lowercase()) {
        "high", "urgent", "important" -> HIGH_PRIORITY_RANK
        "medium" -> MEDIUM_PRIORITY_RANK
        else -> 1
    }
