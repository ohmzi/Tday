package com.ohmz.tday.compose.feature.car

import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.ui.theme.TdayFloaterAccent
import com.ohmz.tday.compose.ui.theme.TdayTodayBlue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CarTaskSurfaceModelsTest {
    @Test
    fun mapsTodayModeToTodayRouteAndAccent() {
        assertEquals(TodoListMode.TODAY, CarTaskMode.TODAY.todoListMode)
        assertEquals("today", CarTaskMode.TODAY.telemetryName)
        assertEquals(TdayTodayBlue, CarTaskMode.TODAY.plusColor)
    }

    @Test
    fun mapsFloaterModeToFloaterRouteAndAccent() {
        assertEquals(TodoListMode.FLOATER, CarTaskMode.FLOATER.todoListMode)
        assertEquals("floater", CarTaskMode.FLOATER.telemetryName)
        assertEquals(TdayFloaterAccent, CarTaskMode.FLOATER.plusColor)
    }

    @Test
    fun buildsPendingTaskRowsOnly() {
        val state = buildCarTaskSurfaceState(
            mode = CarTaskMode.TODAY,
            todos = listOf(
                todo(id = "todo-1", title = "Buy milk", completed = false),
                todo(id = "todo-2", title = "Done task", completed = true),
            ),
            dueLabelFor = { "9:00 AM" },
        )

        assertFalse(state.isEmpty)
        assertEquals(CarTaskMode.TODAY, state.mode)
        assertEquals(1, state.items.size)
        assertEquals("Buy milk", state.items.single().title)
        assertEquals("9:00 AM", state.items.single().dueLabel)
    }

    @Test
    fun exposesEmptyStateWhenNoPendingRowsExist() {
        val state = buildCarTaskSurfaceState(
            mode = CarTaskMode.FLOATER,
            todos = listOf(todo(id = "floater-1", completed = true)),
        )

        assertTrue(state.isEmpty)
        assertEquals(CarTaskMode.FLOATER, state.mode)
    }

    private fun todo(
        id: String,
        title: String = "Task",
        completed: Boolean = false,
    ): TodoItem {
        return TodoItem(
            id = id,
            canonicalId = id,
            title = title,
            description = null,
            priority = "Low",
            due = Instant.parse("2026-06-01T14:00:00Z"),
            rrule = null,
            instanceDate = null,
            pinned = false,
            completed = completed,
            listId = null,
            updatedAt = null,
        )
    }
}
