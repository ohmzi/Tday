package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Today snapshot is baked for one local day at write time, and nothing rewrites it at
 * midnight — only a cache write with UI changes does. Offline there are no such writes, so the
 * widget kept rendering yesterday's window ("No tasks due today") while Room held today's tasks.
 */
class TodayWidgetSnapshotStalenessTest {
    private val zoneId = ZoneId.of("America/Toronto")
    private val yesterday = LocalDate.of(2026, 9, 26)
    private val today = yesterday.plusDays(1)
    private val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val todayNoon = todayStart + 12 * HOUR_MS

    @Test
    fun `should be outside the window when the snapshot was built for yesterday`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = yesterday,
            zoneId = zoneId,
        )

        assertTrue(snapshot.isOutsideTodayWindow(nowEpochMs = todayNoon))
    }

    @Test
    fun `should stay inside the window until midnight when built for today`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(todos = listOf(todo(dueEpochMs = todayNoon))),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertFalse(snapshot.isOutsideTodayWindow(nowEpochMs = todayStart))
        assertFalse(snapshot.isOutsideTodayWindow(nowEpochMs = todayStart + 24 * HOUR_MS - 1))
        assertTrue(snapshot.isOutsideTodayWindow(nowEpochMs = todayStart + 24 * HOUR_MS))
    }

    @Test
    fun `should never go stale when the snapshot is a setup snapshot`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = false,
            today = yesterday,
            zoneId = zoneId,
        )

        assertFalse(snapshot.isOutsideTodayWindow(nowEpochMs = todayNoon))
    }

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

    private fun todo(dueEpochMs: Long) = CachedTodoRecord(
        id = "today",
        canonicalId = "today",
        title = "Today",
        dueEpochMs = dueEpochMs,
    )

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
