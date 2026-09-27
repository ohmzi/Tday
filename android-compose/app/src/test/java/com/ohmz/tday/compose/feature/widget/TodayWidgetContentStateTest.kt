package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.feature.widget.snapshot.buildTodayWidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayWidgetContentStateTest {
    private val zoneId = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 27)
    private val todayNoon = today.atStartOfDay(zoneId).toInstant().toEpochMilli() + 12 * 3_600_000L

    @Test
    fun `should render empty not loading when yesterday's snapshot has nothing due today`() {
        // The v0.7.41 regression: nothing changed overnight, so nothing rewrote the snapshot, and
        // an earlier-day snapshot rendered "Loading tasks…" until a WorkManager rebuild that
        // HyperOS could defer indefinitely.
        val yesterdaysSnapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today.minusDays(1),
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.EMPTY,
            todayContentState(isAppLocked = false, snapshot = yesterdaysSnapshot, nowEpochMs = todayNoon),
        )
    }

    @Test
    fun `should render tasks when yesterday's snapshot has tasks due today`() {
        val yesterdaysSnapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(CachedTodoRecord(id = "a", canonicalId = "a", title = "A", dueEpochMs = todayNoon)),
            ),
            workspaceConfigured = true,
            today = today.minusDays(1),
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.TASKS,
            todayContentState(isAppLocked = false, snapshot = yesterdaysSnapshot, nowEpochMs = todayNoon),
        )
    }

    @Test
    fun `should render loading when the snapshot has run out of days`() {
        val weekOldSnapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today.minusDays(7),
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.LOADING,
            todayContentState(isAppLocked = false, snapshot = weekOldSnapshot, nowEpochMs = todayNoon),
        )
    }

    @Test
    fun `should render locked when the app is locked`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.LOCKED,
            todayContentState(isAppLocked = true, snapshot = snapshot, nowEpochMs = todayNoon),
        )
    }
}
