package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedCompletedRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.feature.widget.TaskWidgetContentState
import com.ohmz.tday.compose.feature.widget.todayContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the Today widget's header ring, Overdue section and empty-day preview read from the
 * snapshot. The rules are the app's own: done today is `completedTodayCount`, overdue is Today's
 * Earlier bucket (due before the day starts, never counted as due today).
 */
class TodayWidgetProgressAndOverdueTest {
    private val zoneId = ZoneId.of("America/Toronto")
    private val today = LocalDate.of(2026, 9, 27)

    private fun startOf(day: LocalDate) = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private fun noonOf(day: LocalDate) = startOf(day) + 12 * HOUR_MS

    @Test
    fun `should count done by due date, not by when it was checked off`() {
        // The on-device report: one open task today read "3 of 4 done", because three tasks due
        // on other days had been completed today and were counted as today's.
        val snapshot = build(
            OfflineSyncState(
                todos = listOf(todo("open today", noonOf(today))),
                completedItems = listOf(
                    completed("due yesterday, done today", due = noonOf(today.minusDays(1)), at = noonOf(today)),
                    completed("due tomorrow, done today", due = noonOf(today.plusDays(1)), at = noonOf(today)),
                    completed("due today, done yesterday", due = noonOf(today), at = noonOf(today.minusDays(1))),
                    completed("due today, done today", due = startOf(today) + HOUR_MS, at = noonOf(today)),
                    completed("due tonight", due = startOf(today.plusDays(1)) - 1, at = noonOf(today)),
                ),
            ),
        )

        assertEquals(3, snapshot.completedCount)
        assertEquals(1, snapshot.taskCount)
    }

    @Test
    fun `should read zero done when today's only task is still open`() {
        val snapshot = build(
            OfflineSyncState(
                todos = listOf(todo("open today", noonOf(today))),
                completedItems = listOf(
                    completed("other day", due = noonOf(today.minusDays(2)), at = noonOf(today)),
                ),
            ),
        )

        assertEquals(0, snapshot.completedCount)
    }

    @Test
    fun `should list tasks due before today as overdue, never as due today`() {
        val snapshot = build(
            OfflineSyncState(
                todos = listOf(
                    todo("last week", noonOf(today.minusDays(7))),
                    todo(YESTERDAY_ID, noonOf(today.minusDays(1))),
                    todo("early today", startOf(today) + HOUR_MS),
                    todo("done yesterday", noonOf(today.minusDays(1)), completed = true),
                ),
            ),
        )

        assertEquals(listOf("early today"), snapshot.rows.map { it.id })
        assertEquals(1, snapshot.taskCount)
        assertEquals(listOf("last week", YESTERDAY_ID), snapshot.overdueRows.map { it.id })
        assertEquals(2, snapshot.overdueCount)
    }

    @Test
    fun `should cap overdue rows but keep the true count`() {
        val snapshot = build(
            OfflineSyncState(
                todos = (0 until 30).map { todo("o$it", startOf(today) - (it + 1) * HOUR_MS) },
            ),
        )

        assertEquals(OVERDUE_TASK_LIMIT, snapshot.overdueRows.size)
        assertEquals(30, snapshot.overdueCount)
    }

    @Test
    fun `should count today's open tasks as overdue on the days after it`() {
        // Nothing rewrites the snapshot unless something changes, so a task still open when the
        // snapshot is read tomorrow was never completed, and is overdue by then.
        val snapshot = build(
            OfflineSyncState(
                todos = listOf(
                    todo(YESTERDAY_ID, noonOf(today.minusDays(1))),
                    todo(TODAY_ID, noonOf(today)),
                    todo("tomorrow", noonOf(today.plusDays(1))),
                ),
            ),
        )

        val tomorrow = requireNotNull(snapshot.todayAt(noonOf(today.plusDays(1))))
        assertEquals(listOf("tomorrow"), tomorrow.rows.map { it.id })
        assertEquals(listOf(YESTERDAY_ID, TODAY_ID), tomorrow.overdueRows.map { it.id })
        assertEquals(2, tomorrow.overdueCount)
        assertEquals(0, tomorrow.completedCount)
        assertEquals(startOf(today.plusDays(1)), tomorrow.dayStartEpochMs)
    }

    @Test
    fun `should carry each day's own done count`() {
        val snapshot = build(
            OfflineSyncState(
                completedItems = listOf(
                    completed("today's", due = noonOf(today), at = startOf(today) + HOUR_MS),
                    completed("tomorrow's, done early", due = noonOf(today.plusDays(1)), at = startOf(today) + HOUR_MS),
                ),
            ),
        )

        assertEquals(1, requireNotNull(snapshot.todayAt(noonOf(today))).completedCount)
        assertEquals(1, requireNotNull(snapshot.todayAt(noonOf(today.plusDays(1)))).completedCount)
        assertEquals(0, requireNotNull(snapshot.todayAt(noonOf(today.plusDays(2)))).completedCount)
    }

    @Test
    fun `should render an overdue-only day as a list, not as empty`() {
        val snapshot = build(OfflineSyncState(todos = listOf(todo("late", noonOf(today.minusDays(1))))))

        assertEquals(WidgetSnapshotStatus.EMPTY, snapshot.status)
        assertEquals(
            TaskWidgetContentState.TASKS,
            todayContentState(isAppLocked = false, snapshot = snapshot, nowEpochMs = noonOf(today)),
        )
    }

    @Test
    fun `should preview the next day that has tasks, skipping empty ones`() {
        val snapshot = build(
            OfflineSyncState(
                todos = listOf(
                    todo("in three days", noonOf(today.plusDays(3))),
                    todo("in five days", noonOf(today.plusDays(5))),
                ),
            ),
        )

        val next = requireNotNull(snapshot.nextDayWithTasks(noonOf(today)))
        assertEquals(startOf(today.plusDays(3)), next.dayStartEpochMs)
        assertEquals(listOf("in three days"), next.rows.map { it.id })
        // Read on day three itself, the preview moves on to day five.
        val afterDayThree = requireNotNull(snapshot.nextDayWithTasks(noonOf(today.plusDays(3))))
        assertEquals(startOf(today.plusDays(5)), afterDayThree.dayStartEpochMs)
    }

    @Test
    fun `should preview nothing when no later day has tasks`() {
        val snapshot = build(OfflineSyncState(todos = listOf(todo(TODAY_ID, noonOf(today)))))

        assertNull(snapshot.nextDayWithTasks(noonOf(today)))
    }

    private fun build(state: OfflineSyncState) = buildTodayWidgetSnapshot(
        state = state,
        workspaceConfigured = true,
        today = today,
        zoneId = zoneId,
    )

    private fun todo(id: String, dueEpochMs: Long, completed: Boolean = false) = CachedTodoRecord(
        id = id,
        canonicalId = id,
        title = id,
        dueEpochMs = dueEpochMs,
        completed = completed,
    )

    private fun completed(id: String, due: Long, at: Long) = CachedCompletedRecord(
        id = id,
        title = id,
        priority = "Low",
        dueEpochMs = due,
        completedAtEpochMs = at,
    )

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val YESTERDAY_ID = "yesterday"
        const val TODAY_ID = "today"
    }
}
