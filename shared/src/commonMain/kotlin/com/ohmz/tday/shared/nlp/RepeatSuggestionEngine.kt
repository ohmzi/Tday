package com.ohmz.tday.shared.nlp

import kotlin.math.abs

/**
 * Deterministic "Make this repeat?" engine, shared across platforms. Given the local
 * completed-task history and the title being created, it detects a title the user
 * finishes on a regular cadence and suggests the matching preset RRULE.
 *
 * All math is pure and unit-tested; each platform feeds it its own completed cache
 * (Room / SwiftData / TanStack) and renders the dismissible suggestion chip.
 */
object RepeatSuggestionEngine {
    /** One past completion. [title] is the raw saved title; the engine normalizes it. */
    data class Completion(val title: String, val completedAtEpochMs: Long)

    private data class Target(val days: Double, val rrule: String, val toleranceDays: Double)

    private val targets = listOf(
        Target(1.0, "RRULE:FREQ=DAILY;INTERVAL=1", 0.5),
        Target(7.0, "RRULE:FREQ=WEEKLY;INTERVAL=1", 2.0),
        Target(30.0, "RRULE:FREQ=MONTHLY;INTERVAL=1", 7.0),
        Target(365.0, "RRULE:FREQ=YEARLY;INTERVAL=1", 45.0),
    )

    /** Minimum matching completions before a cadence is trustworthy. */
    const val MIN_COMPLETIONS = 3
    private const val MS_PER_DAY = 86_400_000.0
    private const val SAME_DAY_INTERVAL_DAYS = 0.25

    /**
     * Returns a preset RRULE to suggest for [currentTitle], or null when there's no
     * clear regular cadence in [completions].
     */
    fun suggest(currentTitle: String, completions: List<Completion>): String? {
        val norm = normalize(currentTitle)
        if (norm.isBlank()) return null

        val times = completions
            .filter { normalize(it.title) == norm }
            .map { it.completedAtEpochMs }
            .sorted()
        if (times.size < MIN_COMPLETIONS) return null

        val intervals = times.zipWithNext { a, b -> (b - a) / MS_PER_DAY }
            .filter { it > SAME_DAY_INTERVAL_DAYS } // ignore same-day duplicate completions
        if (intervals.size < MIN_COMPLETIONS - 1) return null

        val median = medianOf(intervals)
        val target = targets.firstOrNull { abs(median - it.days) <= it.toleranceDays } ?: return null

        // Require the cadence to be consistent: at most ~1/3 of the gaps may be outliers.
        val consistent = intervals.count { abs(it - median) <= target.toleranceDays }
        if (consistent < intervals.size - intervals.size / 3) return null

        return target.rrule
    }

    /** Lowercased, whitespace-collapsed, recurrence/priority-stripped title. */
    fun normalize(title: String): String =
        RecurrencePriorityGrammar.parse(title).cleanTitle
            .lowercase()
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    private fun medianOf(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
