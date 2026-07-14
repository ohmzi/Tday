package com.ohmz.tday.shared.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekReviewTest {
    // 2026-06-07T12:00:00Z — a Sunday.
    private val nowMs = 1_780_488_000_000L
    private val day = 86_400_000L

    private fun done(title: String, daysAgo: Long, dueDaysAgo: Long? = null) = SummaryTaskInput(
        title = title,
        priority = "Low",
        dueEpochMs = dueDaysAgo?.let { nowMs - it * day },
        completed = true,
        completedAtEpochMs = nowMs - daysAgo * day,
    )

    @Test
    fun emptyWeekReportsNothingCleared() {
        val out = SummaryEngine.summarize(emptyList(), SummaryScope.WEEK, nowMs, "UTC", "en")
        assertEquals("No tasks cleared this week yet.", out)
    }

    @Test
    fun countsClearedTasksWithinTheWeek() {
        val tasks = listOf(
            done("a", 1),
            done("b", 2),
            done("c", 10), // outside the 7-day window — excluded
        )
        val out = SummaryEngine.summarize(tasks, SummaryScope.WEEK, nowMs, "UTC", "en")
        assertTrue(out.contains("You cleared 2 tasks this week."), out)
    }

    @Test
    fun namesTheBusiestDayAndOldestCleared() {
        // Three cleared on the same weekday, one on another; oldest by due date.
        val tasks = listOf(
            done("x", 1, dueDaysAgo = 3),
            done("y", 1, dueDaysAgo = 20),  // oldest due
            done("z", 1, dueDaysAgo = 2),
            done("w", 4, dueDaysAgo = 1),
        )
        val out = SummaryEngine.summarize(tasks, SummaryScope.WEEK, nowMs, "UTC", "en")
        assertTrue(out.contains("You cleared 4 tasks this week."), out)
        assertTrue(out.contains("Busiest day was"), out)
        assertTrue(out.contains("(3)"), out) // three cleared one day ago
        assertTrue(out.contains("Oldest you finally cleared: y."), out)
    }

    @Test
    fun ignoresIncompleteTasks() {
        val tasks = listOf(
            done("a", 1),
            SummaryTaskInput(title = "pending", priority = "Low", dueEpochMs = nowMs, completed = false),
        )
        val out = SummaryEngine.summarize(tasks, SummaryScope.WEEK, nowMs, "UTC", "en")
        assertTrue(out.contains("You cleared 1 tasks this week."), out)
    }
}
