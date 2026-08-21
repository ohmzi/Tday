package com.ohmz.tday.mcp

import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpListTasksTest {

    private fun todayAt(hour: Int): LocalDateTime =
        LocalDate.now(ZoneOffset.UTC).atTime(hour, 0)

    @Test
    fun `today shows only tasks due today`() = testApplication {
        val world = McpTestWorld()
        world.addTodo("Due today", todayAt(10))
        world.addTodo("Due next week", todayAt(10).plusDays(7))
        application { configureMcpTestApp(world) }

        val result = client.callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today"}""")

        val text = result.toolText()
        assertTrue(text.contains("Due today"), text)
        assertFalse(text.contains("Due next week"), text)
    }

    @Test
    fun `anytime shows floaters and not scheduled tasks`() = testApplication {
        val world = McpTestWorld()
        world.addTodo("Due today", todayAt(10))
        world.addFloater("Buy batteries")
        application { configureMcpTestApp(world) }

        val text = client.callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"anytime"}""").toolText()

        assertTrue(text.contains("Buy batteries"), text)
        assertFalse(text.contains("Due today"), text)
    }

    @Test
    fun `completed tasks are hidden unless asked for`() = testApplication {
        val world = McpTestWorld()
        world.addTodo("Already done", todayAt(10), completed = true)
        application { configureMcpTestApp(world) }

        val hidden = client.callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today"}""").toolText()
        val shown = client
            .callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today","includeCompleted":true}""")
            .toolText()

        assertFalse(hidden.contains("Already done"), hidden)
        assertTrue(shown.contains("Already done"), shown)
    }

    @Test
    fun `a recurring series is expanded into occurrences`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Standup", todayAt(9), rrule = "RRULE:FREQ=DAILY;INTERVAL=1")
        world.setRecurrence(todo.id)
        application { configureMcpTestApp(world) }

        val text = client
            .callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"upcoming","limit":10}""")
            .toolText()

        // A daily series over the default 14-day upcoming window, not one template row.
        assertTrue(text.split("Standup").size - 1 > 3, text)
        assertTrue(text.contains("occurrenceDate:"), text)
    }

    @Test
    fun `a cancelled occurrence is not listed`() = testApplication {
        val world = McpTestWorld()
        val todo = world.addTodo("Standup", todayAt(9), rrule = "RRULE:FREQ=DAILY;INTERVAL=1")
        world.setRecurrence(todo.id, exdates = listOf(todayAt(9)))
        application { configureMcpTestApp(world) }

        val text = client.callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today"}""").toolText()

        assertTrue(text.contains("No task(s) due today"), text)
    }

    @Test
    fun `a list filter narrows the results`() = testApplication {
        val world = McpTestWorld()
        val work = world.addList("Work")
        world.addTodo("In Work", todayAt(10), listId = work.id)
        world.addTodo("Unfiled", todayAt(11))
        application { configureMcpTestApp(world) }

        val text = client
            .callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today","listName":"Work"}""")
            .toolText()

        assertTrue(text.contains("In Work"), text)
        assertFalse(text.contains("Unfiled"), text)
    }

    @Test
    fun `filtering by a list that does not exist explains rather than returning everything`() = testApplication {
        val world = McpTestWorld()
        world.addList("Work")
        world.addTodo("In Work", todayAt(10))
        application { configureMcpTestApp(world) }

        val text = client
            .callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today","listName":"Hardware"}""")
            .toolText()

        assertTrue(text.contains("No list named \"Hardware\" exists"), text)
        assertFalse(text.contains("In Work"), text)
    }

    @Test
    fun `an unknown view is refused`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val result = client.callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"someday"}""")

        assertTrue(result.isToolError())
    }

    @Test
    fun `times are rendered in the user's timezone`() = testApplication {
        val world = McpTestWorld(timeZone = "Europe/London")
        // Stored as a UTC wall clock; London is UTC+1 in July.
        world.addTodo("Standup", LocalDateTime.of(2030, 7, 29, 8, 0))
        application { configureMcpTestApp(world) }

        val text = client
            .callTool(
                MCP_READ_KEY,
                McpToolCatalog.LIST_TASKS,
                """{"view":"all","from":"2030-07-29","to":"2030-07-29"}""",
            )
            .toolText()

        assertTrue(text.contains("09:00"), text)
        assertFalse(text.contains("08:00"), text)
    }

    @Test
    fun `search matches titles and notes across both entities`() = testApplication {
        val world = McpTestWorld()
        world.addTodo("Submit quarterly report", todayAt(10))
        world.addFloater("Buy batteries")
        application { configureMcpTestApp(world) }

        val hit = client.callTool(MCP_READ_KEY, McpToolCatalog.SEARCH_TASKS, """{"query":"report"}""").toolText()
        val miss = client.callTool(MCP_READ_KEY, McpToolCatalog.SEARCH_TASKS, """{"query":"unicorn"}""").toolText()

        assertTrue(hit.contains("Submit quarterly report"), hit)
        assertFalse(hit.contains("Buy batteries"), hit)
        assertTrue(miss.contains("No tasks match"), miss)
    }

    @Test
    fun `get_context lists both namespaces and the write capability`() = testApplication {
        val world = McpTestWorld().apply {
            addList("Work")
            addFloaterList("Groceries")
        }
        application { configureMcpTestApp(world) }

        val full = client.callTool(MCP_FULL_KEY, McpToolCatalog.GET_CONTEXT).toolText()

        assertTrue(full.contains("Scheduled lists"), full)
        assertTrue(full.contains("Work"), full)
        assertTrue(full.contains("Anytime lists"), full)
        assertTrue(full.contains("Groceries"), full)
        assertTrue(full.contains("Access: full"), full)
    }

    @Test
    fun `handles round-trip from a read tool into a write tool`() = testApplication {
        val world = McpTestWorld()
        world.addFloater("Buy batteries")
        application { configureMcpTestApp(world) }

        val listed = client.callTool(MCP_FULL_KEY, McpToolCatalog.LIST_TASKS, """{"view":"anytime"}""").toolText()
        val handle = Regex("floater:\\S+").find(listed)?.value ?: error("no handle in: $listed")

        val completed = client.callTool(MCP_FULL_KEY, McpToolCatalog.COMPLETE_TASK, """{"taskId":"$handle"}""")

        assertFalse(completed.isToolError(), completed.toolText())
        assertTrue(world.floaters.values.single().completed)
    }

    @Test
    fun `an empty result says so rather than returning nothing`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val text = client.callTool(MCP_READ_KEY, McpToolCatalog.LIST_TASKS, """{"view":"today"}""").toolText()

        assertEquals("No task(s) due today found.", text)
    }
}
