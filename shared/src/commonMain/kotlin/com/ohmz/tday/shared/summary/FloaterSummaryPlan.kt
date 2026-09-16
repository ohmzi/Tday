package com.ohmz.tday.shared.summary

import com.ohmz.tday.shared.floater.FloaterResting
import com.ohmz.tday.shared.floater.FloaterRestingTier

/**
 * How big the undated pile is, as a shape rather than a number.
 *
 * Bands, not counts, on purpose. The count is already on screen — the rows are right there —
 * so printing it back is the redundancy this rewrite exists to remove. A band also survives
 * translation: a whole sentence per band needs no numeral agreement, whereas "{{count}} tasks"
 * would need Russian's three plural forms and Malay's none, and [SummaryStrings.t] is a naive
 * `{{param}}` replace with no plural machinery at all.
 */
internal enum class FloaterPileBand { ONE, FEW, SOME, MANY }

/**
 * The single extra thing worth saying about the pile, beyond its shape. At most one is chosen:
 * a summary that lists every observation is the list again, in prose.
 */
internal enum class FloaterNote {
    /** Nothing in the set stands out — say only what the band says. */
    NONE,
    PINNED_ONE,
    PINNED_MANY,
    RESTING_ONE,
    RESTING_MANY,

    /** Every floater in the set has gone dormant. */
    RESTING_ALL,
    PRIORITY_ONE,
    PRIORITY_MANY,
}

/**
 * The structure of an "Anytime" summary, decided before a single word is chosen.
 *
 * Splitting the decision from the wording is what makes the copy testable: a test can assert
 * that a set of twelve dormant floaters produces [FloaterNote.RESTING_ALL] without pinning one
 * syllable of any of the ten locales, and the locale tests can then assert *qualities* of the
 * rendered string instead of hardcoding it. It is also the only telemetry-safe shape of this
 * feature — band and note are structural; [noteTitle] is user text and must never be logged.
 */
internal data class FloaterSummaryPlan(
    val band: FloaterPileBand,
    val note: FloaterNote,
    /** The one task the note names, raw and uncompacted, or null when the note names none. */
    val noteTitle: String?,
)

/**
 * Turns a set of undated tasks into a [FloaterSummaryPlan].
 *
 * Pure: same tasks + same clock in, same plan out, on every platform. It reads only facts the
 * caller supplied — the size of the set, the pin flag, the priority, and the resting tier
 * derived from `updatedAtEpochMs` — so there is nothing here that could describe a task that
 * was not passed in.
 */
internal object FloaterSummaryPlanner {

    /** Upper bound of [FloaterPileBand.FEW]: still countable at a glance. */
    const val FEW_MAX = 4

    /** Upper bound of [FloaterPileBand.SOME]: more than a screenful starts to feel like a backlog. */
    const val SOME_MAX = 11

    fun plan(tasks: List<SummaryTaskInput>, nowEpochMs: Long): FloaterSummaryPlan {
        require(tasks.isNotEmpty()) { "a floater plan needs at least one task" }

        // Mirrors TaskSortEngine.compareFloaters (pinned, then priority, then most recently
        // modified) so the task the summary names is the task sitting at the top of the list
        // beneath it. The old engine ranked floaters by priority alone and routinely opened
        // with a task that was not the first row.
        val ranked = tasks.sortedWith(
            compareByDescending<SummaryTaskInput> { it.pinned }
                .thenByDescending { priorityRankOf(it.priority) }
                .thenByDescending { it.updatedAtEpochMs ?: 0L },
        )

        val pinned = tasks.count { it.pinned }
        val resting = tasks.count {
            FloaterResting.tierFor(it.updatedAtEpochMs, nowEpochMs) == FloaterRestingTier.RESTING
        }
        val high = tasks.count { priorityRankOf(it.priority) == HIGH_PRIORITY_RANK }

        val note = when {
            // A wholly dormant pile outranks a pin: when nothing has been touched in months,
            // "you pinned one of these" is not the story — the dormancy is. Below that, the pin
            // wins, because it is the one mark the person made deliberately.
            resting == tasks.size -> FloaterNote.RESTING_ALL
            pinned == 1 -> FloaterNote.PINNED_ONE
            pinned > 1 -> FloaterNote.PINNED_MANY
            resting == 1 -> FloaterNote.RESTING_ONE
            resting > 1 -> FloaterNote.RESTING_MANY
            high == 1 -> FloaterNote.PRIORITY_ONE
            high > 1 -> FloaterNote.PRIORITY_MANY
            else -> FloaterNote.NONE
        }

        // Naming the only row on screen is not a summary, it is an echo. A single-task pile
        // keeps the notes that COUNT something (dormancy) and drops the ones that point at a
        // task, because with one task there is nothing to point away from.
        val single = tasks.size == 1
        val noteTitle = when (note) {
            FloaterNote.PINNED_ONE, FloaterNote.PINNED_MANY ->
                ranked.first { it.pinned }.title

            FloaterNote.PRIORITY_ONE, FloaterNote.PRIORITY_MANY ->
                ranked.first { priorityRankOf(it.priority) == HIGH_PRIORITY_RANK }.title

            // The resting notes deliberately name nobody: `updatedAtEpochMs` is a last-write
            // clock, so "this one has waited longest" would be a claim about creation time that
            // a rename silently falsifies. Counting dormant tasks is safe; ranking them is not.
            else -> null
        }

        val naming = noteTitle != null
        return FloaterSummaryPlan(
            band = bandFor(tasks.size),
            note = if (single && naming) FloaterNote.NONE else note,
            noteTitle = if (single && naming) null else noteTitle,
        )
    }

    fun bandFor(count: Int): FloaterPileBand = when {
        count <= 1 -> FloaterPileBand.ONE
        count <= FEW_MAX -> FloaterPileBand.FEW
        count <= SOME_MAX -> FloaterPileBand.SOME
        else -> FloaterPileBand.MANY
    }
}
