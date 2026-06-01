package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayTasksWidgetModelTest {
    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 30)
    private val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Test
    fun `model includes only pending scheduled tasks due today`() {
        val model = buildTodayTasksWidgetModel(
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
            title = "Today's Tasks",
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(TodayTasksWidgetStatus.TASKS, model.status)
        assertEquals(2, model.taskCount)
        assertEquals(listOf("soon", "later"), model.tasks.map { it.id })
    }

    @Test
    fun `model sorts same-time tasks by title`() {
        val due = dayStart + 10 * 3_600_000
        val model = buildTodayTasksWidgetModel(
            state = OfflineSyncState(
                todos = listOf(
                    todo(id = "b", title = "Beta", dueEpochMs = due),
                    todo(id = "a", title = "Alpha", dueEpochMs = due),
                ),
            ),
            title = "Today's Tasks",
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(listOf("a", "b"), model.tasks.map { it.id })
    }

    @Test
    fun `model caps display tasks but preserves total count`() {
        val todos = (0 until 55).map { index ->
            todo(
                id = "task-$index",
                title = "Task $index",
                dueEpochMs = dayStart + index * 60_000L,
            )
        }

        val model = buildTodayTasksWidgetModel(
            state = OfflineSyncState(todos = todos),
            title = "Today's Tasks",
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(55, model.taskCount)
        assertEquals(50, model.tasks.size)
        assertEquals(5, model.overflowCount)
        assertEquals("task-0", model.tasks.first().id)
        assertEquals("task-49", model.tasks.last().id)
    }

    @Test
    fun `model exposes empty state for configured workspaces without due-today tasks`() {
        val model = buildTodayTasksWidgetModel(
            state = OfflineSyncState(),
            title = "Today's Tasks",
            workspaceConfigured = true,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(TodayTasksWidgetStatus.EMPTY, model.status)
        assertEquals(0, model.taskCount)
        assertTrue(model.tasks.isEmpty())
    }

    @Test
    fun `model exposes setup state before workspace configuration`() {
        val model = buildTodayTasksWidgetModel(
            state = OfflineSyncState(
                todos = listOf(todo(id = "today", title = "Today", dueEpochMs = dayStart + 60_000)),
            ),
            title = "Today's Tasks",
            workspaceConfigured = false,
            today = today,
            zoneId = zoneId,
        )

        assertEquals(TodayTasksWidgetStatus.SETUP, model.status)
        assertEquals(0, model.taskCount)
        assertTrue(model.tasks.isEmpty())
    }

    private fun todo(
        id: String,
        title: String,
        dueEpochMs: Long?,
        completed: Boolean = false,
    ) = CachedTodoRecord(
        id = id,
        canonicalId = id,
        title = title,
        dueEpochMs = dueEpochMs,
        completed = completed,
        updatedAtEpochMs = dueEpochMs ?: 0L,
    )
}
