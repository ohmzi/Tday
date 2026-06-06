package com.ohmz.tday.shared.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryEngineTest {

    // 2026-06-06T09:00:00Z — a Saturday morning.
    private val nowMs = 1_780_390_800_000L
    private val utc = "UTC"

    private fun task(
        title: String,
        priority: String? = "Low",
        dueOffsetMs: Long? = null,
        kind: String = "task",
        listId: String? = null,
        completed: Boolean = false,
    ) = SummaryTaskInput(
        title = title,
        priority = priority,
        dueEpochMs = dueOffsetMs?.let { nowMs + it },
        listId = listId,
        completed = completed,
        kind = kind,
    )

    private val hour = 3_600_000L
    private val day = 86_400_000L

    @Test
    fun emptyViewReturnsClearMessage() {
        val out = SummaryEngine.summarize(emptyList(), SummaryScope.TODAY, nowMs, utc, "en")
        assertEquals("You're clear for now. No tasks need attention in this view.", out)
    }

    @Test
    fun completedTasksAreIgnored() {
        val out = SummaryEngine.summarize(
            listOf(task("done", dueOffsetMs = 2 * hour, completed = true)),
            SummaryScope.TODAY, nowMs, utc, "en",
        )
        assertEquals("You're clear for now. No tasks need attention in this view.", out)
    }

    @Test
    fun singleTodayTaskStartsWithIt() {
        // due 09:00 same day -> afternoon? 09:00Z is morning. "due today in the morning"
        val out = SummaryEngine.summarize(
            listOf(task("Buy milk", dueOffsetMs = 0)),
            SummaryScope.TODAY, nowMs, utc, "en",
        )
        assertEquals("Start with Buy milk, which is due today in the morning.", out)
    }

    @Test
    fun overdueIsPhrasedInPastTense() {
        val out = SummaryEngine.summarize(
            listOf(task("Pay rent", dueOffsetMs = -2 * day)),
            SummaryScope.OVERDUE, nowMs, utc, "en",
        )
        // yesterday-2 days falls into the far branch (not -1), so "on <date>"
        assertTrue(out.startsWith("Start with Pay rent, which was due"), "was: $out")
        assertTrue(out.contains("overdue"), "expected overdue catch-up note: $out")
    }

    @Test
    fun startThenOrderingByUrgency() {
        val out = SummaryEngine.summarize(
            listOf(
                task("Later task", dueOffsetMs = 5 * day),
                task("Now task", dueOffsetMs = 2 * hour),
            ),
            SummaryScope.ALL, nowMs, utc, "en",
        )
        assertTrue(out.startsWith("Start with Now task"), "was: $out")
        assertTrue(out.contains("Next up"), "was: $out")
    }

    @Test
    fun localeChangesLanguage() {
        val en = SummaryEngine.summarize(
            listOf(task("Acheter du pain", dueOffsetMs = 2 * hour)),
            SummaryScope.TODAY, nowMs, utc, "en",
        )
        val fr = SummaryEngine.summarize(
            listOf(task("Acheter du pain", dueOffsetMs = 2 * hour)),
            SummaryScope.TODAY, nowMs, utc, "fr",
        )
        assertTrue(en.startsWith("Start with"), "en was: $en")
        assertTrue(fr != en && fr.contains("Acheter du pain"), "fr was: $fr")
    }

    @Test
    fun undatedTaskUsesAnytimeLabel() {
        val out = SummaryEngine.summarize(
            listOf(task("Someday idea", dueOffsetMs = null, kind = "anytime")),
            SummaryScope.FLOATER, nowMs, utc, "en",
        )
        assertEquals("Start with Someday idea, which is anytime.", out)
    }
}
