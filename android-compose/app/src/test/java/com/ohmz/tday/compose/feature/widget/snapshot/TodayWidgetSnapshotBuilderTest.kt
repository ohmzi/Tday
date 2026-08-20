package com.ohmz.tday.compose.feature.widget.snapshot

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayWidgetSnapshotBuilderTest {
    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 30)
    private val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Test
    fun `snapshot includes only pending scheduled tasks due today`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "yesterday", title = "Yesterday", dueEpochMs = dayStart - 1),
                    todo(
                        id = "completed",
                        title = "Completed",
                        dueEpochMs = dayStart + 9_000,
                        completed = true
                    ),
                    todo(id = "tomorrow", title = "Tomorrow", dueEpochMs = dayStart + 86_400_000),
                    todo(id = "later", title = "Later", dueEpochMs = dayStart + 17 * 3_600_000),
                    todo(id = "soon", title = "Soon", dueEpochMs = dayStart + 9 * 3_600_000),
                    todo(id = "floater-shaped", title = "No due", dueEpochMs = null),
                ),
            ),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(WidgetSnapshotStatus.TASKS, snapshot.status)
        assertEquals(2, snapshot.taskCount)
        assertEquals(listOf("soon", "later"), snapshot.rows.map { it.id })
    }

    @Test
    fun `snapshot sorts same-time tasks by title`() {
        val due = dayStart + 10 * 3_600_000
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "b", title = "Beta", dueEpochMs = due),
                    todo(id = "a", title = "Alpha", dueEpochMs = due),
                ),
            ),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(listOf("a", "b"), snapshot.rows.map { it.id })
    }

    @Test
    fun `snapshot caps display tasks but preserves total count`() {
        val todos = (0 until 55).map { index ->
            todo(
                id = "task-$index",
                title = "Task $index",
                dueEpochMs = dayStart + index * 60_000L,
            )
        }

        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(todos = todos),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(55, snapshot.taskCount)
        assertEquals(50, snapshot.rows.size)
        assertEquals("task-0", snapshot.rows.first().id)
        assertEquals("task-49", snapshot.rows.last().id)
    }

    @Test
    fun `snapshot exposes empty state for configured workspaces without due-today tasks`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(WidgetSnapshotStatus.EMPTY, snapshot.status)
        assertEquals(0, snapshot.taskCount)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `snapshot exposes setup state before workspace configuration`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(todo(id = "today", title = "Today", dueEpochMs = dayStart + 60_000)),
            ),
            workspaceConfigured = false,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(WidgetSnapshotStatus.SETUP, snapshot.status)
        assertEquals(0, snapshot.taskCount)
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `snapshot buckets priority into the ring`() {
        val snapshot = buildTodayWidgetSnapshot(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "high", title = "High", dueEpochMs = dayStart, priority = "High"),
                    todo(
                        id = "medium",
                        title = "Medium",
                        dueEpochMs = dayStart + 60_000,
                        priority = "Medium",
                    ),
                    todo(id = "low", title = "Low", dueEpochMs = dayStart + 120_000, priority = "Low"),
                ),
            ),
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(
            mapOf(
                "high" to WidgetPriorityRing.HIGH,
                "medium" to WidgetPriorityRing.MEDIUM,
                "low" to WidgetPriorityRing.LOW,
            ),
            snapshot.rows.associate { it.id to it.priorityRing },
        )
    }

    private fun todo(
        id: String,
        title: String,
        dueEpochMs: Long?,
        completed: Boolean = false,
        priority: String = "Low",
    ) = CachedTodoRecord(
        id = id,
        canonicalId = id,
        title = title,
        dueEpochMs = dueEpochMs,
        completed = completed,
        priority = priority,
        updatedAtEpochMs = dueEpochMs ?: 0L,
    )
}
