package com.ohmz.tday.shared.sort

import com.ohmz.tday.shared.model.Priority

/**
 * Minimal, platform-neutral view of a task for T'Day's FIXED, non-configurable ordering.
 * Every surface — web, desktop, both native apps, and their home-screen widgets — maps its
 * own task model onto this so the presentation order is identical no matter where the user
 * opens their account.
 *
 * `dueEpochMs`/`updatedAtEpochMs` are UTC millis; a null due date sorts LAST. `priorityRank`
 * is 0-highest (see [TaskSortEngine.priorityRank]). `pinned` tasks always lead.
 */
data class TaskSortKey(
    val id: String,
    val pinned: Boolean = false,
    val dueEpochMs: Long? = null,
    val priorityRank: Int = LOWEST_PRIORITY_RANK,
    val updatedAtEpochMs: Long? = null,
) {
    companion object {
        const val LOWEST_PRIORITY_RANK: Int = 2
    }
}

/**
 * The single source of truth for how T'Day orders tasks. There is NO user setting — this is
 * simply how lists are presented everywhere. The web TS twin (`tday-web/src/lib/taskSort.ts`)
 * and the iOS Swift twin must mirror this logic exactly; keep them in sync by hand.
 *
 * - Todos (scheduled screen + custom lists): pinned first, then by due date+time (soonest
 *   first, undated last), then priority (High → Low), then most-recently-modified, then id.
 * - Floaters: pinned first, then priority (High → Low), then most-recently-modified, then id.
 */
object TaskSortEngine {

    fun <T> sortedTodos(items: List<T>, key: (T) -> TaskSortKey): List<T> =
        items.sortedWith { a, b -> compareTodos(key(a), key(b)) }

    fun <T> sortedFloaters(items: List<T>, key: (T) -> TaskSortKey): List<T> =
        items.sortedWith { a, b -> compareFloaters(key(a), key(b)) }

    fun compareTodos(a: TaskSortKey, b: TaskSortKey): Int {
        pin(a, b)?.let { return it }
        dueAscNullsLast(a, b)?.let { return it }
        priority(a, b)?.let { return it }
        modifiedDesc(a, b)?.let { return it }
        return a.id.compareTo(b.id)
    }

    fun compareFloaters(a: TaskSortKey, b: TaskSortKey): Int {
        pin(a, b)?.let { return it }
        priority(a, b)?.let { return it }
        modifiedDesc(a, b)?.let { return it }
        return a.id.compareTo(b.id)
    }

    /** 0 = highest priority (sorts first). Unknown/absent priority → Low. */
    fun priorityRank(priority: Priority): Int = when (priority) {
        Priority.High -> 0
        Priority.Medium -> 1
        Priority.Low -> 2
    }

    // Tolerant of every priority spelling the app stores: canonical Low/Medium/High, the
    // server/legacy vocabulary normal/important/urgent, and any case. Realtime-synced rows
    // arrive un-normalized, so a strict enum-name match would collapse them all to Low and the
    // sort would silently ignore priority (the reported "flag updates but list doesn't re-sort"
    // bug). Unknown/null → Low. Mirrors android canonicalPriorityValue; keep the iOS/web twins
    // identical.
    fun priorityRank(priority: String?): Int = when (priority?.trim()?.lowercase()) {
        "high", "urgent" -> priorityRank(Priority.High)
        "medium", "important" -> priorityRank(Priority.Medium)
        else -> priorityRank(Priority.Low)
    }

    private fun pin(a: TaskSortKey, b: TaskSortKey): Int? =
        (rank(b.pinned) - rank(a.pinned)).takeIf { it != 0 }

    private fun priority(a: TaskSortKey, b: TaskSortKey): Int? =
        a.priorityRank.compareTo(b.priorityRank).takeIf { it != 0 }

    private fun dueAscNullsLast(a: TaskSortKey, b: TaskSortKey): Int? {
        // Compare due at MINUTE precision. Times are shown to the minute ("9:41 PM"), so two
        // tasks in the same clock minute that differ only by seconds are the "same time" to
        // the user — we treat them as equal here so the next key (priority) breaks the tie.
        val x = a.dueEpochMs?.let(::floorToMinute)
        val y = b.dueEpochMs?.let(::floorToMinute)
        val d = when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            else -> x.compareTo(y)
        }
        return d.takeIf { it != 0 }
    }

    /** Floor a UTC-epoch-millis instant to its minute (drop seconds/millis). */
    private fun floorToMinute(epochMs: Long): Long = epochMs - (epochMs % 60_000L)

    private fun modifiedDesc(a: TaskSortKey, b: TaskSortKey): Int? {
        val x = a.updatedAtEpochMs
        val y = b.updatedAtEpochMs
        val d = when {
            x == null && y == null -> 0
            x == null -> 1              // no timestamp sorts last
            y == null -> -1
            else -> y.compareTo(x)      // DESC: most recently modified first
        }
        return d.takeIf { it != 0 }
    }

    private fun rank(pinned: Boolean): Int = if (pinned) 1 else 0
}
