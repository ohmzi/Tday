package com.ohmz.tday.shared.nlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurrencePriorityGrammarTest {
    @Test
    fun capturesTheFiveRecurrencePresets() {
        assertEquals("RRULE:FREQ=DAILY;INTERVAL=1", RecurrencePriorityGrammar.parse("water plants every day").rrule)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=1", RecurrencePriorityGrammar.parse("standup weekly").rrule)
        assertEquals(
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR",
            RecurrencePriorityGrammar.parse("gym every weekday").rrule,
        )
        assertEquals("RRULE:FREQ=MONTHLY;INTERVAL=1", RecurrencePriorityGrammar.parse("rent monthly").rrule)
        assertEquals("RRULE:FREQ=YEARLY;INTERVAL=1", RecurrencePriorityGrammar.parse("taxes annually").rrule)
    }

    @Test
    fun weekdayWinsOverWeekSubstring() {
        assertEquals(
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR",
            RecurrencePriorityGrammar.parse("report weekdays").rrule,
        )
    }

    @Test
    fun capturesPriority() {
        val bang = RecurrencePriorityGrammar.parse("call mom !!")
        assertEquals("High", bang.priority)
        assertEquals("call mom", bang.cleanTitle)
        assertEquals("Medium", RecurrencePriorityGrammar.parse("email boss !").priority)
        assertEquals("High", RecurrencePriorityGrammar.parse("fix bug high priority").priority)
        assertEquals("Low", RecurrencePriorityGrammar.parse("buy milk low").priority)
    }

    @Test
    fun ignoresNonTrailingBareWords() {
        assertNull(RecurrencePriorityGrammar.parse("buy low-fat milk").priority)
        assertNull(RecurrencePriorityGrammar.parse("review highlights").priority)
    }

    @Test
    fun capturesRecurrenceAndPriorityTogether() {
        val result = RecurrencePriorityGrammar.parse("gym every day !!")
        assertEquals("RRULE:FREQ=DAILY;INTERVAL=1", result.rrule)
        assertEquals("High", result.priority)
        assertEquals("gym", result.cleanTitle)
    }

    @Test
    fun leavesPlainTitlesUntouched() {
        val result = RecurrencePriorityGrammar.parse("buy groceries")
        assertEquals("buy groceries", result.cleanTitle)
        assertNull(result.rrule)
        assertNull(result.priority)
    }
}
