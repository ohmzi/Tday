package com.ohmz.tday.mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The MCP endpoint is exempt from the pipeline's read-only method guard, because every
 * MCP message is a POST. These tests pin both halves of that trade: a READ key must get
 * *through* to the handler, and must still be refused at the tool boundary.
 */
class McpScopeGuardTest {

    @Test
    fun `a read-only key reaches the endpoint instead of being blanket-rejected`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val response = client.mcp(MCP_READ_KEY, """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `a read-only key can call a read tool`() = testApplication {
        val world = McpTestWorld().apply { addList("Work") }
        application { configureMcpTestApp(world) }

        val result = client.callTool(MCP_READ_KEY, McpToolCatalog.GET_CONTEXT)

        assertFalse(result.isToolError())
        assertTrue(result.toolText().contains("read-only"), result.toolText())
        assertTrue(result.toolText().contains("Work"))
    }

    @Test
    fun `a read-only key is refused on every write tool, with an explanation`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val writeTools = McpToolCatalog.tools.filter { it.requiresWrite }
        assertTrue(writeTools.isNotEmpty())

        for (tool in writeTools) {
            val result = client.callTool(MCP_READ_KEY, tool.name, """{"title":"x","name":"x","kind":"scheduled","taskId":"todo:1"}""")
            assertTrue(result.isToolError(), "${tool.name} should refuse")
            val text = result.toolText()
            assertTrue(text.contains("read-only"), "${tool.name}: $text")
            assertTrue(text.contains("Dashboard access"), "${tool.name}: $text")
        }
        // Nothing was written despite every write tool being called.
        assertTrue(world.todos.isEmpty())
        assertTrue(world.floaters.isEmpty())
        assertTrue(world.lists.isEmpty())
        assertTrue(world.floaterLists.isEmpty())
    }

    @Test
    fun `a full key can write`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.callTool(MCP_FULL_KEY, McpToolCatalog.CREATE_TASK, """{"title":"Buy milk"}""")

        assertFalse(result.isToolError(), result.toolText())
        assertEquals(1, world.floaters.size)
    }

    @Test
    fun `an unknown tool name is a tool error, not a protocol error`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val result = client.callTool(MCP_FULL_KEY, "tday_do_something_else")

        assertTrue(result.isToolError())
        assertTrue(result.toolText().contains(McpToolCatalog.LIST_TASKS))
    }
}
