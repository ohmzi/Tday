package com.ohmz.tday.mcp

import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rule that defines the whole tool surface: a date decides which entity gets
 * written, and a named list has to already exist unless the caller says otherwise.
 */
class McpCreateTaskTest {

    @Test
    fun `a task with a due date becomes a scheduled todo`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Submit report","due":"2030-07-29T20:00"}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(1, world.todos.size)
        assertTrue(world.floaters.isEmpty())
        assertEquals("2030-07-29T20:00", world.todos.values.single().due)
        assertTrue(result.toolText().contains("scheduled task"), result.toolText())
    }

    @Test
    fun `a task with no due date becomes an Anytime floater`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(MCP_FULL_KEY, McpToolCatalog.CREATE_TASK, """{"title":"Buy batteries"}""")

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(1, world.floaters.size)
        assertTrue(world.todos.isEmpty())
        assertTrue(result.toolText().contains("Anytime"), result.toolText())
    }

    @Test
    fun `a date with no time is due at end of that day`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Renew passport","due":"2030-07-29"}""",
        )

        assertEquals("2030-07-29T23:59", world.todos.values.single().due)
        assertTrue(result.toolText().contains("end of that day"), result.toolText())
    }

    @Test
    fun `a local due is converted from the user timezone to the stored UTC wall clock`() = testApplication {
        val world = McpTestWorld(timeZone = "Europe/London")
        application { configureMcpTestApp(world) }

        // 09:00 BST is 08:00 UTC — the wire format carries no offset, so this has to be
        // converted rather than passed through.
        client.callTool(MCP_FULL_KEY, McpToolCatalog.CREATE_TASK, """{"title":"Standup","due":"2030-07-29T09:00"}""")

        assertEquals("2030-07-29T08:00", world.todos.values.single().due)
    }

    @Test
    fun `an ISO instant is accepted as-is`() = testApplication {
        val world = McpTestWorld(timeZone = "Europe/London")
        application { configureMcpTestApp(world) }

        client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Standup","due":"2030-07-29T20:00:00.000Z"}""",
        )

        assertEquals("2030-07-29T20:00", world.todos.values.single().due)
    }

    @Test
    fun `an unreadable due is refused without writing anything`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Something","due":"next tuesday-ish"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(world.todos.isEmpty())
        assertTrue(world.floaters.isEmpty())
    }

    @Test
    fun `recurrence without a due date is refused`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Water plants","recurrence":"RRULE:FREQ=WEEKLY"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(result.toolText().contains("needs a due date"), result.toolText())
        assertTrue(world.todos.isEmpty())
        assertTrue(world.floaters.isEmpty())
    }

    @Test
    fun `an unknown list name creates nothing and reports what does exist`() = testApplication {
        val world = McpTestWorld().apply {
            addList("Work")
            addList("Errands")
        }
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Submit report","due":"2030-07-29T20:00","listName":"Hardware"}""",
        )

        assertTrue(result.isToolError())
        val text = result.toolText()
        assertTrue(text.contains("No scheduled list named \"Hardware\" exists"), text)
        assertTrue(text.contains("Work"), text)
        assertTrue(text.contains("Errands"), text)
        assertTrue(world.todos.isEmpty(), "nothing should be created on a list miss")
        assertEquals(2, world.lists.size, "no list should be created either")
    }

    @Test
    fun `a miss never offers a list from the other namespace as available`() = testApplication {
        val world = McpTestWorld().apply { addList("Work") }
        application { configureMcpTestApp(world) }

        // "Work" is a scheduled list; an undated task cannot go in it, so it must not be
        // listed as something to pick.
        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Buy fuses","listName":"Hardware"}""",
        )

        val text = result.toolText()
        assertTrue(text.contains("You have no Anytime lists yet"), text)
        assertFalse(text.contains("Available: Work"), text)
        assertTrue(text.contains("can't take this task"), text)
    }

    @Test
    fun `a near-miss list name comes back as a suggestion`() = testApplication {
        val world = McpTestWorld().apply { addList("Work") }
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Submit report","due":"2030-07-29T20:00","listName":"Werk"}""",
        )

        assertTrue(result.toolText().contains("Closest match: \"Work\""), result.toolText())
    }

    @Test
    fun `createListIfMissing files the task in a new list of the right namespace`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val dated = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Submit report","due":"2030-07-29T20:00","listName":"Work","createListIfMissing":true}""",
        )
        val undated = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Buy batteries","listName":"Hardware","createListIfMissing":true}""",
        )

        assertFalse(dated.isToolError(), dated.toolText())
        assertFalse(undated.isToolError(), undated.toolText())
        assertEquals(listOf("Work"), world.lists.values.map { it.name })
        assertEquals(listOf("Hardware"), world.floaterLists.values.map { it.name })
        assertEquals(world.lists.keys.single(), world.todos.values.single().listID)
        assertEquals(world.floaterLists.keys.single(), world.floaters.values.single().listID)
    }

    @Test
    fun `naming an Anytime list for a dated task explains the two namespaces`() = testApplication {
        val world = McpTestWorld().apply { addFloaterList("Groceries") }
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Order the cake","due":"2030-07-29T20:00","listName":"Groceries"}""",
        )

        assertTrue(result.isToolError())
        val text = result.toolText()
        assertTrue(text.contains("Anytime list"), text)
        assertTrue(text.contains("scheduled list"), text)
        assertTrue(world.todos.isEmpty())
    }

    @Test
    fun `an existing list is matched case-insensitively`() = testApplication {
        val world = McpTestWorld().apply { addList("Work") }
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Submit report","due":"2030-07-29T20:00","listName":"work"}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(1, world.lists.size)
        assertEquals(world.lists.keys.single(), world.todos.values.single().listID)
    }

    @Test
    fun `create_list refuses a duplicate name and returns the existing list`() = testApplication {
        val world = McpTestWorld().apply { addList("Work") }
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_LIST,
            """{"name":"Work","kind":"scheduled"}""",
        )

        assertFalse(result.isToolError())
        assertTrue(result.toolText().contains("There is already a scheduled list"), result.toolText())
        assertEquals(1, world.lists.size)
    }

    @Test
    fun `create_list allows the same name in the other namespace`() = testApplication {
        val world = McpTestWorld().apply { addList("Work") }
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_LIST,
            """{"name":"Work","kind":"anytime"}""",
        )

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(1, world.lists.size)
        assertEquals(1, world.floaterLists.size)
    }

    @Test
    fun `create_list rejects a colour T'Day does not have`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_LIST,
            """{"name":"Work","kind":"scheduled","color":"chartreuse"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(world.lists.isEmpty())
    }

    @Test
    fun `find_list reports a miss with the available names`() = testApplication {
        val world = McpTestWorld().apply {
            addList("Work")
            addFloaterList("Groceries")
        }
        application { configureMcpTestApp(world) }

        val hit = client.callTool(MCP_READ_KEY, McpToolCatalog.FIND_LIST, """{"name":"Groceries"}""")
        val miss = client.callTool(MCP_READ_KEY, McpToolCatalog.FIND_LIST, """{"name":"Hardware"}""")

        assertTrue(hit.toolText().contains("Found \"Groceries\""), hit.toolText())
        assertTrue(hit.toolText().contains("Anytime list"), hit.toolText())
        assertTrue(miss.toolText().contains("No list named \"Hardware\" exists"), miss.toolText())
    }

    @Test
    fun `a list id from the wrong namespace is refused`() = testApplication {
        val world = McpTestWorld()
        val anytimeList = world.addFloaterList("Groceries")
        application { configureMcpTestApp(world) }

        val result = client.callTool(
            MCP_FULL_KEY,
            McpToolCatalog.CREATE_TASK,
            """{"title":"Order the cake","due":"2030-07-29T20:00","listId":"${anytimeList.id}"}""",
        )

        assertTrue(result.isToolError())
        assertTrue(world.todos.isEmpty())
        assertNull(world.todos.values.firstOrNull()?.listID)
    }
}
