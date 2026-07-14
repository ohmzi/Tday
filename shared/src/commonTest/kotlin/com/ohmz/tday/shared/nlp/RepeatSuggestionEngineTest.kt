package com.ohmz.tday.shared.nlp

import com.ohmz.tday.shared.nlp.RepeatSuggestionEngine.Completion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepeatSuggestionEngineTest {
    private val day = 86_400_000L

    private fun weekly(count: Int, title: String = "water plants"): List<Completion> =
        (0 until count).map { Completion(title, base + it * 7 * day) }

    private val base = 1_700_000_000_000L

    @Test
    fun suggestsWeeklyForASteadySevenDayCadence() {
        assertEquals(
            "RRULE:FREQ=WEEKLY;INTERVAL=1",
            RepeatSuggestionEngine.suggest("water plants", weekly(4)),
        )
    }

    @Test
    fun suggestsDailyMonthlyYearly() {
        val daily = (0 until 4).map { Completion("stretch", base + it * day) }
        assertEquals("RRULE:FREQ=DAILY;INTERVAL=1", RepeatSuggestionEngine.suggest("stretch", daily))

        val monthly = (0 until 4).map { Completion("pay rent", base + it * 30L * day) }
        assertEquals("RRULE:FREQ=MONTHLY;INTERVAL=1", RepeatSuggestionEngine.suggest("pay rent", monthly))

        val yearly = (0 until 3).map { Completion("renew passport", base + it * 365L * day) }
        assertEquals("RRULE:FREQ=YEARLY;INTERVAL=1", RepeatSuggestionEngine.suggest("renew passport", yearly))
    }

    @Test
    fun requiresAtLeastThreeCompletions() {
        val two = (0 until 2).map { Completion("water plants", base + it * 7 * day) }
        assertNull(RepeatSuggestionEngine.suggest("water plants", two))
    }

    @Test
    fun ignoresIrregularCadence() {
        val irregular = listOf(0L, 3L, 20L, 21L).map { Completion("random chore", base + it * day) }
        assertNull(RepeatSuggestionEngine.suggest("random chore", irregular))
    }

    @Test
    fun matchesTitlesCaseAndPhraseInsensitively() {
        val completions = listOf(
            Completion("Water Plants", base),
            Completion("water plants", base + 7 * day),
            Completion("water plants !", base + 14 * day),
        )
        assertEquals(
            "RRULE:FREQ=WEEKLY;INTERVAL=1",
            RepeatSuggestionEngine.suggest("water plants every week", completions),
        )
    }

    @Test
    fun ignoresUnrelatedTitles() {
        val mixed = weekly(2) + listOf(Completion("something else", base + 100 * day))
        assertNull(RepeatSuggestionEngine.suggest("water plants", mixed))
    }

    @Test
    fun toleratesOneOutlierInAConsistentRun() {
        // Five weekly gaps, one off by a couple days — still weekly.
        val times = listOf(0L, 7L, 14L, 23L, 28L, 35L)
        val completions = times.map { Completion("gym", base + it * day) }
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=1", RepeatSuggestionEngine.suggest("gym", completions))
    }
}
