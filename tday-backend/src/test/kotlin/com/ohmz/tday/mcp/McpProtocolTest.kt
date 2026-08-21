package com.ohmz.tday.mcp

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpProtocolTest {

    @Test
    fun `initialize negotiates the requested protocol version and advertises tools`() = testApplication {
        val world = McpTestWorld()
        application { configureMcpTestApp(world) }

        val result = client.mcp(
            MCP_FULL_KEY,
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}""",
        ).result()

        assertEquals("2025-03-26", result.getValue("protocolVersion").jsonPrimitive.content)
        assertTrue(result.getValue("capabilities").jsonObject.containsKey("tools"))
        assertEquals("tday", result.getValue("serverInfo").jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `initialize falls back to the latest version for an unknown one`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val result = client.mcp(
            MCP_FULL_KEY,
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""",
        ).result()

        assertEquals(McpProtocol.LATEST_PROTOCOL_VERSION, result.getValue("protocolVersion").jsonPrimitive.content)
    }

    @Test
    fun `initialize instructions explain the read-only key`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val readInstructions = client
            .mcp(MCP_READ_KEY, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
            .result().getValue("instructions").jsonPrimitive.content
        val fullInstructions = client
            .mcp(MCP_FULL_KEY, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
            .result().getValue("instructions").jsonPrimitive.content

        assertTrue(readInstructions.contains("READ-only"), readInstructions)
        assertFalse(fullInstructions.contains("READ-only"), fullInstructions)
    }

    @Test
    fun `tools list returns the catalog`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val tools = client.mcp(MCP_FULL_KEY, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
            .result().getValue("tools").jsonArray

        val names = tools.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(McpToolCatalog.tools.map { it.name }.toSet(), names.toSet())
    }

    @Test
    fun `tools list flags write tools as unavailable on a read-only key`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val tools = client.mcp(MCP_READ_KEY, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
            .result().getValue("tools").jsonArray

        val descriptions = tools.associate {
            it.jsonObject.getValue("name").jsonPrimitive.content to
                it.jsonObject.getValue("description").jsonPrimitive.content
        }
        assertTrue(descriptions.getValue(McpToolCatalog.CREATE_TASK).startsWith(McpTool.READ_ONLY_PREFIX))
        assertFalse(descriptions.getValue(McpToolCatalog.LIST_TASKS).startsWith(McpTool.READ_ONLY_PREFIX))
    }

    @Test
    fun `ping answers with an empty result`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val result = client.mcp(MCP_FULL_KEY, """{"jsonrpc":"2.0","id":3,"method":"ping"}""").result()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `an unknown method returns method not found`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val error = client.mcp(MCP_FULL_KEY, """{"jsonrpc":"2.0","id":4,"method":"resources/list"}""").errorObject()

        assertEquals(McpProtocol.METHOD_NOT_FOUND, error.getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `a notification is accepted with no body`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val response = client.mcp(MCP_FULL_KEY, """{"jsonrpc":"2.0","method":"notifications/initialized"}""")

        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    @Test
    fun `malformed json is a parse error`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val response = client.mcp(MCP_FULL_KEY, "not json at all")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(McpProtocol.PARSE_ERROR, response.errorObject().getValue("code").jsonPrimitive.int)
    }

    @Test
    fun `a batched request is rejected rather than half-answered`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val response = client.mcp(MCP_FULL_KEY, """[{"jsonrpc":"2.0","id":1,"method":"ping"}]""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET is not allowed`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val response = client.get("/mcp") { header(HttpHeaders.Authorization, "Bearer $MCP_FULL_KEY") }

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals("POST", response.headers[HttpHeaders.Allow])
    }

    @Test
    fun `an unauthenticated request is rejected`() = testApplication {
        application { configureMcpTestApp(McpTestWorld()) }

        val response = client.mcp(null, """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("Dashboard access"), response.bodyAsText())
    }
}
