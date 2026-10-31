package com.ohmz.tday.shared.summary

import com.ohmz.tday.shared.floater.FloaterResting
import com.ohmz.tday.shared.floater.FloaterRestingTier

/**
 * How big the undated pile is, as a shape. The band picks the sentence's wording; the count
 * itself is stated, so a reader learns the size without counting rows.
 *
 * Each band is its own sentence rather than one "{{count}} tasks" frame, because
 * [SummaryStrings.t] is a naive `{{param}}` replace with no plural machinery: a per-band
 * sentence keeps the numeral out of any noun agreement that Russian's three plural forms or
 * Malay's none would otherwise need to get right.
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

    /** Weaker than [PRIORITY_ONE]/[PRIORITY_MANY]: named only when nothing High is present. */
    MEDIUM_ONE,
    MEDIUM_MANY,

    /**
     * Exactly one floater has gone quiet for a month or more (the list's FADING tier, short of
     * RESTING). Named, because it is the only candidate: no ranking is claimed, only that this
     * row's own last-write clock is old.
     */
    FADING_ONE,

    /** Several floaters have gone quiet for a month or more; none is singled out. */
    FADING_MANY,

    /** The one-row pile is itself quiet for a month or more. Unnamed: the row is on screen. */
    FADING_SOLE,

    /**
     * Nothing else is worth saying, so point at the row the list itself puts first.
     *
     * The only note that is chosen for what is ABSENT: a pile with no pin, no High and no
     * dormancy used to be described and never directed — "A handful of undated things are
     * waiting here." and no sense of where to start. Falls to [NONE] for a single-row pile,
     * where naming the only task is an echo rather than a summary.
     */
    START_WITH,
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
    /** How many open floaters the pile holds. A count, not a claim: it is the size of the input. */
    val count: Int,
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

        fun tierOf(task: SummaryTaskInput) =
            FloaterResting.tierFor(task.updatedAtEpochMs, nowEpochMs)

        val pinned = tasks.count { it.pinned }
        val resting = tasks.count { tierOf(it) == FloaterRestingTier.RESTING }
        val fading = tasks.count { tierOf(it) == FloaterRestingTier.FADING }
        val high = tasks.count { priorityRankOf(it.priority) == HIGH_PRIORITY_RANK }
        val medium = tasks.count { priorityRankOf(it.priority) == MEDIUM_PRIORITY_RANK }

        val note = noteFor(
            total = tasks.size,
            pinned = pinned,
            resting = resting,
            high = high,
            medium = medium,
            fading = fading,
        )

        // Nothing stood out, so point at the row the list itself puts first — the same nudge
        // the dated path opens with ("Start with X."). Only for a pile bigger than one row:
        // naming the sole task back to the reader is an echo, not a summary. The claim is
        // only "this is the first row", which the sort above makes true by construction —
        // unlike a "waited longest" ranking, a rename cannot falsify it.
        val chosen = if (note == FloaterNote.NONE && tasks.size > 1) {
            FloaterNote.START_WITH
        } else {
            note
        }

        // Naming the only row on screen is not a summary, it is an echo. A single-task pile
        // keeps the notes that COUNT something (dormancy) and drops the ones that point at a
        // task, because with one task there is nothing to point away from. A lone fading row
        // therefore gets its unnamed sentence instead of the named one.
        val single = tasks.size == 1
        val effective = if (single && chosen == FloaterNote.FADING_ONE) FloaterNote.FADING_SOLE else chosen
        val noteTitle = when (effective) {
            FloaterNote.PINNED_ONE, FloaterNote.PINNED_MANY ->
                ranked.first { it.pinned }.title

            FloaterNote.PRIORITY_ONE, FloaterNote.PRIORITY_MANY ->
                ranked.first { priorityRankOf(it.priority) == HIGH_PRIORITY_RANK }.title

            FloaterNote.MEDIUM_ONE, FloaterNote.MEDIUM_MANY ->
                ranked.first { priorityRankOf(it.priority) == MEDIUM_PRIORITY_RANK }.title

            // FADING_ONE is only chosen when exactly one row is fading, so this is that row.
            FloaterNote.FADING_ONE ->
                ranked.first { tierOf(it) == FloaterRestingTier.FADING }.title

            FloaterNote.START_WITH -> ranked.first().title

            // The resting notes deliberately name nobody: `updatedAtEpochMs` is a last-write
            // clock, so "this one has waited longest" would be a claim about creation time that
            // a rename silently falsifies. Counting dormant tasks is safe; ranking them is not.
            else -> null
        }

        val naming = noteTitle != null
        return FloaterSummaryPlan(
            band = bandFor(tasks.size),
            count = tasks.size,
            note = if (single && naming) FloaterNote.NONE else effective,
            noteTitle = if (single && naming) null else noteTitle,
        )
    }

    fun bandFor(count: Int): FloaterPileBand = when {
        count <= 1 -> FloaterPileBand.ONE
        count <= FEW_MAX -> FloaterPileBand.FEW
        count <= SOME_MAX -> FloaterPileBand.SOME
        else -> FloaterPileBand.MANY
    }

    /**
     * The single note the pile earns, by precedence. Split out of [plan] so that function reads as
     * "count, choose, name" and this ladder can be read on its own.
     */
    private fun noteFor(
        total: Int,
        pinned: Int,
        resting: Int,
        high: Int,
        medium: Int,
        fading: Int,
    ): FloaterNote = when {
        // A wholly dormant pile outranks a pin: when nothing has been touched in months,
        // "you pinned one of these" is not the story — the dormancy is. Below that, the pin
        // wins, because it is the one mark the person made deliberately.
        resting == total -> FloaterNote.RESTING_ALL
        pinned == 1 -> FloaterNote.PINNED_ONE
        pinned > 1 -> FloaterNote.PINNED_MANY
        resting == 1 -> FloaterNote.RESTING_ONE
        resting > 1 -> FloaterNote.RESTING_MANY
        high == 1 -> FloaterNote.PRIORITY_ONE
        high > 1 -> FloaterNote.PRIORITY_MANY
        // Below High: Medium is still worth naming over saying nothing about priority at
        // all, but it never outranks High — a pile with both gets the High note only.
        medium == 1 -> FloaterNote.MEDIUM_ONE
        medium > 1 -> FloaterNote.MEDIUM_MANY
        // Stale but not dormant: the row the list is quietly fading. Below priority, because
        // a month untouched is a nudge, not a deadline the person has already been given.
        fading == 1 -> FloaterNote.FADING_ONE
        fading > 1 -> FloaterNote.FADING_MANY
        else -> FloaterNote.NONE
    }
}
