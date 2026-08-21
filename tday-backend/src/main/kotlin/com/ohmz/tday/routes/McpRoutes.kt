package com.ohmz.tday.routes

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.di.inject
import com.ohmz.tday.mcp.McpCallContext
import com.ohmz.tday.mcp.McpProtocol
import com.ohmz.tday.mcp.McpRequest
import com.ohmz.tday.mcp.McpToolCatalog
import com.ohmz.tday.mcp.McpToolDispatcher
import com.ohmz.tday.mcp.mcpError
import com.ohmz.tday.mcp.mcpResult
import com.ohmz.tday.mcp.stringArg
import com.ohmz.tday.mcp.toolArguments
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.plugins.resolvedApiKey
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.shared.model.IntegrationApiKeyDto
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * The Model Context Protocol endpoint — see `docs/MCP.md`.
 *
 * Mounted outside `/api` so the connector URL a user pastes into their AI client is
 * just `<origin>/mcp`, and authenticated with the same per-user API keys the REST API
 * uses. Stateless Streamable HTTP: one POST carries one JSON-RPC message and the
 * reply comes straight back as JSON, so there is no session id and no SSE stream to
 * keep alive.
 *
 * Scope is enforced per tool by [McpToolDispatcher], not by HTTP method — see the
 * exemption note in `plugins/Security.kt`.
 */
fun Route.mcpRoutes() {
    val dispatcher by inject<McpToolDispatcher>()
    val config by inject<AppConfig>()
    val json = Json { ignoreUnknownKeys = true }

    route("/mcp") {
        post {
            val user = call.authUser()
            if (user == null || user.approvalStatus != APPROVED) {
                call.respondUnauthorized()
                return@post
            }

            val body = call.receiveText()
            val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
            if (element == null || element is JsonArray) {
                // Batching was removed from the spec in 2025-06-18; a client that still
                // sends an array gets a parse error rather than a partial response.
                call.respond(
                    HttpStatusCode.BadRequest,
                    mcpError(null, McpProtocol.PARSE_ERROR, "Expected a single JSON-RPC request object."),
                )
                return@post
            }

            val request = McpRequest.parse(element)
            if (request == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mcpError(null, McpProtocol.INVALID_REQUEST, "Not a JSON-RPC 2.0 request."),
                )
                return@post
            }

            // Notifications carry no id and expect no body back.
            if (request.isNotification) {
                call.respond(HttpStatusCode.Accepted, EmptyBody)
                return@post
            }
            val id = request.id!!

            val apiKey = call.resolvedApiKey()
            val ctx = McpCallContext(
                userId = user.id,
                timeZone = user.timeZone,
                apiKey = apiKey?.let {
                    IntegrationApiKeyDto(
                        scope = it.scope.name,
                        label = it.label,
                        keyPreview = it.keyPreview.ifEmpty { null },
                    )
                },
                // Fail closed: only an explicitly non-READ key (or a real session) writes.
                canWrite = apiKey == null || apiKey.scope != ApiKeyScope.READ,
            )

            when (request.method) {
                McpProtocol.Method.INITIALIZE -> call.respond(
                    HttpStatusCode.OK,
                    mcpResult(id, initializeResult(request.params, ctx, config.backendVersion)),
                )

                McpProtocol.Method.PING -> call.respond(HttpStatusCode.OK, mcpResult(id, JsonObject(emptyMap())))

                McpProtocol.Method.TOOLS_LIST -> call.respond(
                    HttpStatusCode.OK,
                    mcpResult(id, toolsListResult(ctx.canWrite)),
                )

                McpProtocol.Method.TOOLS_CALL -> {
                    val toolName = request.params.stringArg("name")
                    if (toolName == null) {
                        call.respond(
                            HttpStatusCode.OK,
                            mcpError(id, McpProtocol.INVALID_PARAMS, "tools/call needs a tool name."),
                        )
                        return@post
                    }
                    val result = dispatcher.call(ctx, toolName, request.params.toolArguments())
                    call.respond(HttpStatusCode.OK, mcpResult(id, result.toJson()))
                }

                else -> call.respond(
                    HttpStatusCode.OK,
                    mcpError(id, McpProtocol.METHOD_NOT_FOUND, "Unsupported method \"${request.method}\"."),
                )
            }
        }

        // No server-initiated stream and nothing to tear down: this server keeps no
        // per-connection state, so the spec's optional GET/DELETE handlers don't apply.
        get { call.respondMethodNotAllowed() }
        delete { call.respondMethodNotAllowed() }
    }
}

private const val APPROVED = "APPROVED"
private val EmptyBody = JsonObject(emptyMap())

private fun initializeResult(params: JsonObject, ctx: McpCallContext, serverVersion: String): JsonObject =
    buildJsonObject {
        put("protocolVersion", JsonPrimitive(McpProtocol.negotiateProtocolVersion(params.stringArg("protocolVersion"))))
        put(
            "capabilities",
            buildJsonObject {
                put("tools", buildJsonObject { put("listChanged", JsonPrimitive(false)) })
            },
        )
        put(
            "serverInfo",
            buildJsonObject {
                put("name", JsonPrimitive(McpProtocol.SERVER_NAME))
                put("title", JsonPrimitive("T'Day"))
                put("version", JsonPrimitive(serverVersion))
            },
        )
        put("instructions", JsonPrimitive(McpToolCatalog.instructions(ctx.canWrite)))
    }

private fun toolsListResult(canWrite: Boolean): JsonObject = buildJsonObject {
    put(
        "tools",
        buildJsonArray { McpToolCatalog.tools.forEach { add(it.toJson(readOnlyConnection = !canWrite)) } },
    )
}

private suspend fun ApplicationCall.respondUnauthorized() {
    response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"tday\"")
    respond(
        HttpStatusCode.Unauthorized,
        buildJsonObject {
            put("code", JsonPrimitive(HttpStatusCode.Unauthorized.value))
            put(
                "message",
                JsonPrimitive(
                    "A T'Day API key is required. Send it as \"Authorization: Bearer tday_<keyId>_<secret>\", " +
                        "or as \"X-API-Key: tday_<keyId>_<secret>\" if your client reserves the Authorization " +
                        "header for OAuth. Generate a key in Settings → Dashboard access.",
                ),
            )
        },
    )
}

private suspend fun ApplicationCall.respondMethodNotAllowed() {
    response.header(HttpHeaders.Allow, "POST")
    respondText(
        text = "The T'Day MCP endpoint accepts POST only.",
        status = HttpStatusCode.MethodNotAllowed,
    )
}
