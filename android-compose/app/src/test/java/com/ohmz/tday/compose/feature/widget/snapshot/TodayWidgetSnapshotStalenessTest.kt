package com.ohmz.tday.compose.feature.widget.snapshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The cheap "was this snapshot written on an earlier local day" check the app's save path and the
 * widget receiver use to decide whether to rewrite or recompose — a file stat, not a decrypt.
 * What the widget shows for a given day is covered by TodayWidgetUpcomingDaysTest.
 */
class TodayWidgetSnapshotStalenessTest {
    private val zoneId = ZoneId.of("America/Toronto")
    private val today = LocalDate.of(2026, 9, 27)
    private val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val todayNoon = todayStart + 12 * HOUR_MS

    @Test
    fun `should predate today when the file was written before local midnight`() {
        assertTrue(
            wasWrittenBeforeLocalDay(
                lastModifiedEpochMs = todayStart - 1,
                nowEpochMs = todayNoon,
                zoneId = zoneId,
            ),
        )
        assertFalse(
            wasWrittenBeforeLocalDay(
                lastModifiedEpochMs = todayStart,
                nowEpochMs = todayNoon,
                zoneId = zoneId,
            ),
        )
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
