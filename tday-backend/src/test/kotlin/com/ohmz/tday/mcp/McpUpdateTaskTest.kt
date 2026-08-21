package com.ohmz.tday.mcp

import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Editing a task's date is what moves it between T'Day's two entities. */
class McpUpdateTaskTest {

    @Test
    fun `giving an Anytime task a date promotes it to a scheduled task`() = testApplication {
        val world = McpTestWorld()
        val floater = world.addFloater("Buy batteries")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"floater:${floater.id}","due":"2030-07-29T20:00"}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(listOf(floater.id), world.promoted)
        assertTrue(world.floaters.isEmpty())
        assertEquals("2030-07-29T20:00", world.todos.values.single().due)
        assertTrue(result.toolText().contains("now a scheduled task"), result.toolText())
        assertTrue(result.toolText().contains("todo:"), result.toolText())
    }

    @Test
    fun `promoting out of an Anytime list says the list was left behind`() = testApplication {
        val world = McpTestWorld()
        val list = world.addFloaterList("Groceries")
        val floater = world.addFloater("Order the cake", listId = list.id)
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"floater:${floater.id}","due":"2030-07-29T20:00"}""",
        )

        assertTrue(result.toolText().contains("left its Anytime list behind"), result.toolText())
    }

    @Test
    fun `clearing the date demotes a scheduled task back to Anytime`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Submit report", LocalDateTime.of(2030, 7, 29, 20, 0))
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"todo:${todo.id}","clearDue":true}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(listOf(todo.id), world.demoted)
        assertTrue(world.todos.isEmpty())
        assertEquals(1, world.floaters.size)
        assertTrue(result.toolText().contains("floater:"), result.toolText())
    }

    @Test
    fun `demoting a repeating task explains why it cannot happen`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Standup", LocalDateTime.of(2030, 7, 29, 9, 0), rrule = "RRULE:FREQ=DAILY")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"todo:${todo.id}","clearDue":true}""",
        )

        assertTrue(result.isToolError())
        assertTrue(result.toolText().contains("series would be lost"), result.toolText())
        assertEquals(1, world.todos.size)
    }

    @Test
    fun `an occurrence edit touches only that occurrence`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Standup", LocalDateTime.of(2030, 7, 29, 9, 0), rrule = "RRULE:FREQ=DAILY")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"todo:${todo.id}","title":"Long standup","occurrenceDate":"2030-08-01T09:00"}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(listOf(todo.id to LocalDateTime.of(2030, 8, 1, 9, 0)), world.patchedInstances)
        assertEquals("Standup", world.todos.getValue(todo.id).title, "the series title must be untouched")
    }

    @Test
    fun `an occurrence edit on an Anytime task is refused`() = testApplication {
        val world = McpTestWorld()
        val floater = world.addFloater("Buy batteries")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"floater:${floater.id}","title":"x","occurrenceDate":"2030-08-01T09:00"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(result.toolText().contains("don't repeat"), result.toolText())
    }

    @Test
    fun `setting recurrence on an Anytime task is refused`() = testApplication {
        val world = McpTestWorld()
        val floater = world.addFloater("Water plants")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"floater:${floater.id}","recurrence":"RRULE:FREQ=WEEKLY"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(result.toolText().contains("can't repeat"), result.toolText())
    }

    @Test
    fun `a bare id is rejected with an explanation of the handle format`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Submit report", LocalDateTime.of(2030, 7, 29, 20, 0))
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.UPDATE_TASK,
            """{"taskId":"${todo.id}","title":"Renamed"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(result.toolText().contains("todo:<id>"), result.toolText())
        assertEquals("Submit report", world.todos.getValue(todo.id).title)
    }

    @Test
    fun `completing and reopening work on both entities`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Submit report", LocalDateTime.of(2030, 7, 29, 20, 0))
        val floater = world.addFloater("Buy batteries")
        application { configureMcpTestApp(world) }

        client.callTool(MCP_FULL_KEY, McpToolCatalog.COMPLETE_TASK, """{"taskId":"todo:${todo.id}"}""")
        client.callTool(MCP_FULL_KEY, McpToolCatalog.COMPLETE_TASK, """{"taskId":"floater:${floater.id}"}""")
        assertTrue(world.todos.getValue(todo.id).completed)
        assertTrue(world.floaters.getValue(floater.id).completed)

        client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.COMPLETE_TASK,
            """{"taskId":"todo:${todo.id}","completed":false}""",
        )
        assertFalse(world.todos.getValue(todo.id).completed)
    }

    @Test
    fun `deleting one occurrence keeps the series`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Standup", LocalDateTime.of(2030, 7, 29, 9, 0), rrule = "RRULE:FREQ=DAILY")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.DELETE_TASK,
            """{"taskId":"todo:${todo.id}","occurrenceDate":"2030-08-01T09:00"}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(listOf(todo.id to LocalDateTime.of(2030, 8, 1, 9, 0)), world.deletedInstances)
        assertTrue(world.todos.containsKey(todo.id), "the series must survive")
        assertTrue(result.toolText().contains("rest of the series is unchanged"), result.toolText())
    }

    @Test
    fun `deleting without an occurrence removes the whole task`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Standup", LocalDateTime.of(2030, 7, 29, 9, 0), rrule = "RRULE:FREQ=DAILY")
        application { configureMcpTestApp(world) }

        client.callTool(MCP_FULL_KEY, McpToolCatalog.DELETE_TASK, """{"taskId":"todo:${todo.id}"}""")

        assertTrue(world.todos.isEmpty())
        assertTrue(world.deletedInstances.isEmpty())
    }

    @Test
    fun `an update with no fields changes nothing`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Submit report", LocalDateTime.of(2030, 7, 29, 20, 0))
        application { configureMcpTestApp(world) }

        val result = client.callTool(MCP_FULL_KEY, McpToolCatalog.UPDATE_TASK, """{"taskId":"todo:${todo.id}"}""")

        assertTrue(result.isToolError())
        assertEquals("Submit report", world.todos.getValue(todo.id).title)
    }
}
