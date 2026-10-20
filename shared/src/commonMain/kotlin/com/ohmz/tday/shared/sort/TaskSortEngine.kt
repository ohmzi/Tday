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
    val priorityRank: Int = UNKNOWN_PRIORITY_RANK,
    val updatedAtEpochMs: Long? = null,
) {
    companion object {
        /** Rank of the real `Priority.Medium` tier. */
        const val MEDIUM_PRIORITY_RANK: Int = 1

        /**
         * Rank of the real `Priority.Low` tier (wire `Low`, UI label "Normal", and the
         * platform default priority).
         */
        const val LOW_PRIORITY_RANK: Int = 2

        /**
         * Rank of the real `Priority.Lowest` tier (wire `Lowest`, UI label "Low") — the least
         * urgent tier there is, so it sorts after everything else.
         */
        const val LOWEST_PRIORITY_RANK: Int = 3

        /**
         * Fallback rank used ONLY for a genuinely unrecognized/garbage priority string — one
         * that is not any of the four known wire values. Deliberately kept equal to
         * [LOW_PRIORITY_RANK] rather than [LOWEST_PRIORITY_RANK]: "we don't understand this
         * input" must keep degrading to the default/Normal rank exactly as it always has, and
         * must never silently sort even lower than a task someone genuinely tagged Lowest.
         *
         * This used to be conflated into a single `LOWEST_PRIORITY_RANK = 2` constant that
         * served both as "the real Low tier's rank" and "the unknown-priority fallback" —
         * that name now belongs to the real `Lowest` tier above, so the unknown-fallback
         * meaning gets its own name instead of silently reusing (and shadowing) it.
         */
        const val UNKNOWN_PRIORITY_RANK: Int = LOW_PRIORITY_RANK
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

    /** 0 = highest priority (sorts first). Unknown/absent priority → Low (Normal). */
    fun priorityRank(priority: Priority): Int = when (priority) {
        Priority.High -> 0
        Priority.Medium -> TaskSortKey.MEDIUM_PRIORITY_RANK
        Priority.Low -> TaskSortKey.LOW_PRIORITY_RANK
        Priority.Lowest -> TaskSortKey.LOWEST_PRIORITY_RANK
    }

    // Tolerant of every priority spelling the app stores: canonical Lowest/Low/Medium/High, the
    // server/legacy vocabulary normal/important/urgent, and any case. Realtime-synced rows
    // arrive un-normalized, so a strict enum-name match would collapse them all to Low and the
    // sort would silently ignore priority (the reported "flag updates but list doesn't re-sort"
    // bug). A genuinely unrecognized/garbage string (not one of the four known values) falls
    // back to TaskSortKey.UNKNOWN_PRIORITY_RANK — the same numeric value as Low, but named
    // separately so it never gets confused with a real Lowest-tagged task. Mirrors android
    // canonicalPriorityValue; keep the iOS/web twins identical.
    fun priorityRank(priority: String?): Int = when (priority?.trim()?.lowercase()) {
        "high", "urgent" -> priorityRank(Priority.High)
        "medium", "important" -> priorityRank(Priority.Medium)
        "lowest" -> priorityRank(Priority.Lowest)
        "low", "normal" -> priorityRank(Priority.Low)
        else -> TaskSortKey.UNKNOWN_PRIORITY_RANK
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
