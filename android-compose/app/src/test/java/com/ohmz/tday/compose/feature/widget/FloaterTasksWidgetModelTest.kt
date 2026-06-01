package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.OfflineSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloaterTasksWidgetModelTest {
    @Test
    fun `model includes only pending floater tasks`() {
        val model = buildFloaterTasksWidgetModel(
            state = OfflineSyncState(
                floaters = listOf(
                    floater(id = "open", title = "Open"),
                    floater(id = "listed", title = "Listed", listId = "list-1"),
                    floater(id = "completed", title = "Completed", completed = true),
                ),
            ),
            title = "Floater Tasks",
            workspaceConfigured = true,
        )

        assertEquals(FloaterTasksWidgetStatus.TASKS, model.status)
        assertEquals(2, model.taskCount)
        assertEquals(listOf("listed", "open"), model.tasks.map { it.id })
    }

    @Test
    fun `model sorts by pinned priority title and id`() {
        val model = buildFloaterTasksWidgetModel(
            state = OfflineSyncState(
                floaters = listOf(
                    floater(id = "low-a", title = "Alpha", priority = "Low"),
                    floater(id = "high-b", title = "Beta", priority = "High"),
                    floater(id = "medium-a", title = "Alpha", priority = "Medium"),
                    floater(id = "pinned-low", title = "Zulu", priority = "Low", pinned = true),
                    floater(id = "urgent-a", title = "Alpha", priority = "Urgent"),
                    floater(id = "urgent-b", title = "Alpha", priority = "Important"),
                ),
            ),
            title = "Floater Tasks",
            workspaceConfigured = true,
        )

        assertEquals(
            listOf("pinned-low", "urgent-a", "urgent-b", "high-b", "medium-a", "low-a"),
            model.tasks.map { it.id },
        )
    }

    @Test
    fun `model caps display tasks but preserves total count`() {
        val floaters = (0 until 55).map { index ->
            floater(id = "task-$index", title = "Task ${index.toString().padStart(2, '0')}")
        }

        val model = buildFloaterTasksWidgetModel(
            state = OfflineSyncState(floaters = floaters),
            title = "Floater Tasks",
            workspaceConfigured = true,
        )

        assertEquals(55, model.taskCount)
        assertEquals(50, model.tasks.size)
        assertEquals(5, model.overflowCount)
        assertEquals("task-0", model.tasks.first().id)
        assertEquals("task-49", model.tasks.last().id)
    }

    @Test
    fun `model exposes empty state for configured workspaces without floater tasks`() {
        val model = buildFloaterTasksWidgetModel(
            state = OfflineSyncState(),
            title = "Floater Tasks",
            workspaceConfigured = true,
        )

        assertEquals(FloaterTasksWidgetStatus.EMPTY, model.status)
        assertEquals(0, model.taskCount)
        assertTrue(model.tasks.isEmpty())
    }

    @Test
    fun `model exposes setup state before workspace configuration`() {
        val model = buildFloaterTasksWidgetModel(
            state = OfflineSyncState(
                floaters = listOf(floater(id = "floater", title = "Floater")),
            ),
            title = "Floater Tasks",
            workspaceConfigured = false,
        )

        assertEquals(FloaterTasksWidgetStatus.SETUP, model.status)
        assertEquals(0, model.taskCount)
        assertTrue(model.tasks.isEmpty())
    }

    private fun floater(
        id: String,
        title: String,
        priority: String = "Low",
        pinned: Boolean = false,
        completed: Boolean = false,
        listId: String? = null,
    ) = CachedFloaterRecord(
        id = id,
        canonicalId = id,
        title = title,
        priority = priority,
        pinned = pinned,
        completed = completed,
        listId = listId,
        updatedAtEpochMs = 0L,
    )
}
