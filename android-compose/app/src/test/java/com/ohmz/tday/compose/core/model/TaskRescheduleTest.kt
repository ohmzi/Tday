package com.ohmz.tday.compose.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TaskRescheduleTest {
    private val zoneId = ZoneId.of("America/Toronto")

    @Test
    fun `movedDuePreservingTime keeps original local time`() {
        val due = Instant.parse("2026-05-15T18:45:30Z")
        val moved = movedDuePreservingTime(
            due = due,
            targetDate = LocalDate.parse("2026-06-03"),
            zoneId = zoneId,
        )

        val movedLocal = moved.atZone(zoneId)
        assertEquals(LocalDate.parse("2026-06-03"), movedLocal.toLocalDate())
        assertEquals(14, movedLocal.hour)
        assertEquals(45, movedLocal.minute)
        assertEquals(30, movedLocal.second)
    }

    @Test
    fun `timelineRescheduleTargetDate resolves exact day sections`() {
        val today = LocalDate.parse("2026-05-24")

        assertEquals(
            LocalDate.parse("2026-05-27"),
            timelineRescheduleTargetDate("day-2026-05-27", today),
        )
    }

    @Test
    fun `timelineRescheduleTargetDate resolves current month rest to horizon start`() {
        val today = LocalDate.parse("2026-05-24")

        assertEquals(
            LocalDate.parse("2026-05-31"),
            timelineRescheduleTargetDate("rest-2026-05", today),
        )
    }

    @Test
    fun `timelineRescheduleTargetDate resolves future month buckets to first day`() {
        val today = LocalDate.parse("2026-05-24")

        assertEquals(
            LocalDate.parse("2026-07-01"),
            timelineRescheduleTargetDate("month-2026-07", today),
        )
    }

    @Test
    fun `timelineRescheduleTargetDate resolves earlier to yesterday and rejects past month targets`() {
        val today = LocalDate.parse("2026-05-24")

        assertEquals(LocalDate.parse("2026-05-23"), timelineRescheduleTargetDate("earlier", today))
        assertNull(timelineRescheduleTargetDate("day-2026-04-30", today))
        assertNull(timelineRescheduleTargetDate("month-2026-04", today))
    }
}
