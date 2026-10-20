package com.ohmz.tday.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * The slice of the Model Context Protocol T'Day speaks: JSON-RPC 2.0 over a single
 * POST, tools only.
 *
 * Deliberately hand-rolled rather than pulled from an SDK. The server exposes no
 * prompts, resources, sampling or roots, which leaves five methods; keeping them here
 * means auth, rate limiting and Sentry stay in the Ktor plugins that already handle
 * every other route. Keep this file free of T'Day domain logic so swapping in an SDK
 * later stays a local change.
 *
 * Spec: https://modelcontextprotocol.io/specification/2025-06-18
 */
object McpProtocol {
    const val JSON_RPC_VERSION = "2.0"

    /** Protocol revisions this server can speak, newest first. */
    val SUPPORTED_PROTOCOL_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
    val LATEST_PROTOCOL_VERSION = SUPPORTED_PROTOCOL_VERSIONS.first()

    const val SERVER_NAME = "tday"

    // JSON-RPC error codes.
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    object Method {
        const val INITIALIZE = "initialize"
        const val PING = "ping"
        const val TOOLS_LIST = "tools/list"
        const val TOOLS_CALL = "tools/call"
        const val NOTIFICATION_PREFIX = "notifications/"
    }

    /**
     * Negotiate a protocol revision. Echo the client's request when we speak it,
     * otherwise answer with our latest and let the client decide whether to continue —
     * which is what the spec prescribes.
     */
    fun negotiateProtocolVersion(requested: String?): String =
        if (requested != null && requested in SUPPORTED_PROTOCOL_VERSIONS) requested else LATEST_PROTOCOL_VERSION
}

/** One parsed JSON-RPC request. [id] is absent for notifications. */
data class McpRequest(
    val method: String,
    val id: JsonElement?,
    val params: JsonObject,
) {
    val isNotification: Boolean get() = id == null

    companion object {
        /** Returns null when [element] is not a well-formed JSON-RPC request object. */
        fun parse(element: JsonElement): McpRequest? {
            val obj = element as? JsonObject ?: return null
            val method = (obj["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val id = obj["id"]?.takeUnless { it is JsonNull }
            val params = (obj["params"] as? JsonObject) ?: JsonObject(emptyMap())
            return McpRequest(method = method, id = id, params = params)
        }
    }
}

fun mcpResult(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
    put("jsonrpc", JsonPrimitive(McpProtocol.JSON_RPC_VERSION))
    put("id", id)
    put("result", result)
}

fun mcpError(id: JsonElement?, code: Int, message: String, data: JsonObject? = null): JsonObject = buildJsonObject {
    put("jsonrpc", JsonPrimitive(McpProtocol.JSON_RPC_VERSION))
    put("id", id ?: JsonNull)
    put(
        "error",
        buildJsonObject {
            put("code", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
            if (data != null) put("data", data)
        },
    )
}

/**
 * A `tools/call` result. MCP draws a hard line between transport failures (JSON-RPC
 * errors, which the model never sees) and tool failures ([isError], which it does) —
 * anything the model should read and act on has to come back this way.
 */
data class McpToolResult(
    val text: String,
    val isError: Boolean = false,
    val structured: JsonObject? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(text))
                    },
                )
            },
        )
        if (structured != null) put("structuredContent", structured)
        put("isError", JsonPrimitive(isError))
    }

    companion object {
        fun ok(text: String, structured: JsonObject? = null) = McpToolResult(text, isError = false, structured = structured)
        fun failed(text: String, structured: JsonObject? = null) = McpToolResult(text, isError = true, structured = structured)
    }
}

/**
 * Argument readers. Models are loose with JSON types — a boolean can arrive as the
 * string "true", a number as "3" — so each reader coerces from the primitive's text
 * rather than insisting on the schema's declared type. `null` always reads as absent.
 */
private fun JsonObject.primitive(name: String): JsonPrimitive? =
    (this[name] as? JsonPrimitive)?.takeUnless { it is JsonNull }

fun JsonObject.stringArg(name: String): String? =
    primitive(name)?.content?.trim()?.ifEmpty { null }

fun JsonObject.boolArg(name: String): Boolean? = when (primitive(name)?.content?.lowercase()) {
    "true" -> true
    "false" -> false
    else -> null
}

fun JsonObject.intArg(name: String): Int? = primitive(name)?.content?.toIntOrNull()

/** The tool-call arguments object, or empty when the client sent none. */
fun JsonObject.toolArguments(): JsonObject = (this["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
