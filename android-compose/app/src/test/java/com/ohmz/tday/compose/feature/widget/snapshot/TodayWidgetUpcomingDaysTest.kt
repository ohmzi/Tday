package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Today snapshot carries the next six local days as well as its own, so the widget turns
 * over at midnight from what is already on disk — no cache write, no network, and no
 * WidgetHydrateWorker (which OEM battery managers can defer for hours). Mirrors iOS's
 * `TodayWidgetDayWindow`.
 */
class TodayWidgetUpcomingDaysTest {
    private val zoneId = ZoneId.of("America/Toronto")
    private val today = LocalDate.of(2026, 9, 27)

    private fun startOf(day: LocalDate) = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private fun noonOf(day: LocalDate) = startOf(day) + 12 * HOUR_MS

    @Test
    fun `should carry the next six days when building the Today snapshot`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo("today", noonOf(today)),
                    todo("tomorrow", noonOf(today.plusDays(1))),
                    todo("sixth", noonOf(today.plusDays(6))),
                    todo("seventh", noonOf(today.plusDays(7))),
                ),
            ),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(listOf("today"), snapshot.rows.map { it.id })
        assertEquals(6, snapshot.upcomingDays.size)
        assertEquals(listOf("tomorrow"), snapshot.upcomingDays[0].rows.map { it.id })
        assertEquals(listOf("sixth"), snapshot.upcomingDays[5].rows.map { it.id })
        assertTrue(snapshot.upcomingDays.none { day -> day.rows.any { it.id == "seventh" } })
        assertEquals(startOf(today.plusDays(1)), snapshot.upcomingDays[0].dayStartEpochMs)
        assertEquals(startOf(today.plusDays(7)), snapshot.upcomingDays[5].dayEndEpochMs)
    }

    @Test
    fun `should cap an upcoming day's rows but keep its full count`() {
        val tomorrow = today.plusDays(1)
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = (0 until 25).map { todo("t$it", startOf(tomorrow) + it * 60_000L) },
            ),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(25, snapshot.upcomingDays[0].taskCount)
        assertEquals(UPCOMING_DAY_TASK_LIMIT, snapshot.upcomingDays[0].rows.size)
    }

    @Test
    fun `should show tomorrow's tasks when a snapshot built yesterday is read today`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(todos = listOf(todo("due-today", noonOf(today)))),
            workspaceConfigured = true,
            today = today.minusDays(1),
            zoneId = zoneId,
        )

        val day = requireNotNull(snapshot.todayAt(nowEpochMs = noonOf(today)))

        assertEquals(WidgetSnapshotStatus.TASKS, day.status)
        assertEquals(1, day.taskCount)
        assertEquals(listOf("due-today"), day.rows.map { it.id })
    }

    @Test
    fun `should show the snapshot's own day while it is still that day`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(WidgetSnapshotStatus.EMPTY, snapshot.todayAt(nowEpochMs = noonOf(today))?.status)
    }

    @Test
    fun `should have no day to show once the snapshot runs out of days`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today.minusDays(7),
            zoneId = zoneId,
        )

        assertNull(snapshot.todayAt(nowEpochMs = noonOf(today)))
    }

    @Test
    fun `should have no day to show for an older single-day snapshot read the next day`() {
        // Written before upcomingDays existed: only its own day is covered.
        val legacy = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today.minusDays(1),
            zoneId = zoneId,
        ).copy(upcomingDays = emptyList())

        assertNull(legacy.todayAt(nowEpochMs = noonOf(today)))
    }

    @Test
    fun `should always show a setup snapshot whatever the day`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = false,
            today = today.minusDays(30),
            zoneId = zoneId,
        )

        assertEquals(WidgetSnapshotStatus.SETUP, snapshot.todayAt(nowEpochMs = noonOf(today))?.status)
    }

    private fun todo(id: String, dueEpochMs: Long) = CachedTodoRecord(
        id = id,
        canonicalId = id,
        title = id,
        dueEpochMs = dueEpochMs,
    )

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
