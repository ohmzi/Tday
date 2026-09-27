package com.ohmz.tday.compose.feature.widget

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
    fun `should render loading instead of no tasks due today when the snapshot is yesterday's`() {
        val yesterdaysSnapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today.minusDays(1),
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.LOADING,
            todayContentState(isAppLocked = false, snapshot = yesterdaysSnapshot, nowEpochMs = todayNoon),
        )
    }

    @Test
    fun `should render empty when today's snapshot has no tasks`() {
        val todaysSnapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.EMPTY,
            todayContentState(isAppLocked = false, snapshot = todaysSnapshot, nowEpochMs = todayNoon),
        )
    }

    @Test
    fun `should render locked when the app is locked and the snapshot is stale`() {
        val yesterdaysSnapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today.minusDays(1),
            zoneId = zoneId,
        )

        assertEquals(
            TaskWidgetContentState.LOCKED,
            todayContentState(isAppLocked = true, snapshot = yesterdaysSnapshot, nowEpochMs = todayNoon),
        )
    }
}
