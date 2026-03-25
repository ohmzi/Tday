package com.ohmz.tday.compose.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

internal class ApiCallException(
    val statusCode: Int,
    override val message: String,
) : IllegalStateException(message)

internal fun <T> requireApiBody(response: Response<T>, fallback: String): T {
    if (response.isSuccessful && response.body() != null) {
        @Suppress("UNCHECKED_CAST")
        return response.body() as T
    }
    throw ApiCallException(
        statusCode = response.code(),
        message = extractApiErrorMessage(response, fallback),
    )
}

internal fun extractApiErrorMessage(response: Response<*>, fallback: String): String {
    val raw = runCatching { response.errorBody()?.string() }.getOrNull()
    if (raw.isNullOrBlank()) return fallback

    return runCatching {
        val element = Json.parseToJsonElement(raw)
        when (element) {
            is JsonObject -> element["message"]?.jsonPrimitive?.contentOrNull ?: fallback
            is JsonPrimitive -> element.content
            else -> fallback
        }
    }.getOrElse { fallback }
}

internal fun isLikelyConnectivityIssue(error: Throwable): Boolean {
    val message = error.message.orEmpty().lowercase()
    return message.contains("failed to connect") ||
        message.contains("econnrefused") ||
        message.contains("timed out") ||
        message.contains("unable to resolve host") ||
        message.contains("network is unreachable")
}

internal fun isLikelyUnrecoverableMutationError(
    error: Throwable,
    mutation: PendingMutationRecord,
): Boolean {
    if (error is ApiCallException) {
        if (error.statusCode == 401 || error.statusCode == 403) return false
        if (
            mutation.kind == MutationKind.CREATE_LIST ||
            mutation.kind == MutationKind.CREATE_TODO ||
            mutation.kind == MutationKind.UPDATE_LIST ||
            mutation.kind == MutationKind.UPDATE_TODO
        ) {
            return false
        }
        return error.statusCode in 400..499 &&
            error.statusCode != 408 &&
            error.statusCode != 429
    }
    val message = error.message.orEmpty().lowercase()
    return message.contains("bad request") ||
        message.contains("bad / malformed") ||
        message.contains("invalid request") ||
        message.contains("invalid request body") ||
        message.contains("you provided invalid values") ||
        message.contains("method not allowed") ||
        message.contains("http 405") ||
        message.contains("record to delete does not exist") ||
        message.contains("record to update not found") ||
        message.contains("todo id is required")
}
