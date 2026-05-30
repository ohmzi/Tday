package com.ohmz.tday.compose.core.data

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response

internal class ApiCallException(
    val statusCode: Int,
    override val message: String,
    val reason: String? = null,
    val field: String? = null,
    val retryAfterSeconds: Int? = null,
) : IllegalStateException(message)

internal data class ApiErrorDetails(
    val message: String,
    val reason: String? = null,
    val field: String? = null,
    val retryAfterSeconds: Int? = null,
)

internal fun <T> requireApiBody(response: Response<T>, fallback: String): T {
    if (response.isSuccessful && response.body() != null) {
        @Suppress("UNCHECKED_CAST")
        return response.body() as T
    }
    val errorDetails = extractApiErrorDetails(response, fallback)
    throw ApiCallException(
        statusCode = response.code(),
        message = errorDetails.message,
        reason = errorDetails.reason,
        field = errorDetails.field,
        retryAfterSeconds = errorDetails.retryAfterSeconds,
    )
}

internal fun extractApiErrorMessage(response: Response<*>, fallback: String): String {
    return extractApiErrorDetails(response, fallback).message
}

internal fun extractApiErrorDetails(response: Response<*>, fallback: String): ApiErrorDetails {
    val raw = runCatching { response.errorBody()?.string() }.getOrNull()
    if (raw.isNullOrBlank()) return ApiErrorDetails(message = fallback)

    return runCatching {
        when (val element = Json.parseToJsonElement(raw)) {
            is JsonObject -> ApiErrorDetails(
                message = element["message"]?.jsonPrimitive?.contentOrNull ?: fallback,
                reason = element["reason"]?.jsonPrimitive?.contentOrNull
                    ?: element["code"]?.jsonPrimitive?.contentOrNull,
                field = element["field"]?.jsonPrimitive?.contentOrNull,
                retryAfterSeconds = element["retryAfterSeconds"]?.jsonPrimitive?.intOrNull,
            )

            is JsonPrimitive -> ApiErrorDetails(message = element.content)
            else -> ApiErrorDetails(message = fallback)
        }
    }.getOrElse { ApiErrorDetails(message = fallback) }
}

internal fun isLikelyConnectivityIssue(error: Throwable): Boolean {
    if (error is TimeoutCancellationException) {
        return true
    }

    var current: Throwable? = error
    while (current != null) {
        if (current is ApiCallException && isLikelyServerUnavailableStatus(current.statusCode)) {
            return true
        }

        val message = current.message.orEmpty().lowercase()
        if (
            message.contains("failed to connect") ||
            message.contains("econnrefused") ||
            message.contains("timed out") ||
            message.contains("unable to resolve host") ||
            message.contains("unknownhost") ||
            message.contains("no address associated with hostname") ||
            message.contains("network is unreachable") ||
            message.contains("not connected") ||
            message.contains("connection reset") ||
            message.contains("broken pipe") ||
            message.contains("software caused connection abort") ||
            message.contains("no route to host") ||
            message.contains("connection refused") ||
            message.contains("bad gateway") ||
            message.contains("service unavailable") ||
            message.contains("gateway timeout") ||
            message.contains("origin unreachable") ||
            message.contains("web server is down")
        ) {
            return true
        }
        current = current.cause?.takeIf { it !== current }
    }
    return false
}

internal fun isSessionAuthenticationIssue(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is ApiCallException && current.statusCode == 401) {
            return true
        }
        current = current.cause?.takeIf { it !== current }
    }
    return false
}

internal fun isLikelyServerUnavailableStatus(statusCode: Int): Boolean {
    return statusCode == 408 ||
            statusCode == 502 ||
            statusCode == 503 ||
            statusCode == 504 ||
            statusCode in 520..524
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
