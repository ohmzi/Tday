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

/** Stable, non-localized codes the repository emits so the UI layer can map each
 *  failure to a specific, localized, user-readable message instead of one
 *  catch-all. Never shown to the user directly — [AuthViewModel] maps them. */
internal object AuthErrorCode {
    const val CANNOT_REACH = "tday.err.cannot_reach"
    const val SERVER_UNAVAILABLE = "tday.err.server_unavailable"
    const val APP_OUTDATED = "tday.err.app_outdated"
    const val SERVER_OUTDATED = "tday.err.server_outdated"
}

internal enum class ConnectionFailureKind { CANNOT_REACH, SERVER_UNAVAILABLE, NONE }

/**
 * Distinguishes "couldn't reach the server at all" (transport: no network, DNS,
 * refused, timeout) from "the server answered but is unhealthy" (5xx / gateway /
 * origin-down), so the UI can tell the user whether to check their own connection
 * or report the server as down to an admin.
 */
internal fun classifyConnectionFailure(error: Throwable): ConnectionFailureKind {
    if (error is TimeoutCancellationException) return ConnectionFailureKind.CANNOT_REACH

    var current: Throwable? = error
    while (current != null) {
        if (current is ApiCallException && isLikelyServerUnavailableStatus(current.statusCode)) {
            return ConnectionFailureKind.SERVER_UNAVAILABLE
        }

        val message = current.message.orEmpty().lowercase()
        if (
            message.contains("bad gateway") ||
            message.contains("service unavailable") ||
            message.contains("gateway timeout") ||
            message.contains("origin unreachable") ||
            message.contains("web server is down")
        ) {
            return ConnectionFailureKind.SERVER_UNAVAILABLE
        }
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
            message.contains("connection refused")
        ) {
            return ConnectionFailureKind.CANNOT_REACH
        }
        current = current.cause?.takeIf { it !== current }
    }
    return ConnectionFailureKind.NONE
}

internal fun isLikelyConnectivityIssue(error: Throwable): Boolean =
    classifyConnectionFailure(error) != ConnectionFailureKind.NONE

/** Maps a backend version-gate response (HTTP 426 / `app_update_required` /
 *  `server_update_required`) to the matching [AuthErrorCode], or null. */
internal fun versionMismatchAuthCode(error: Throwable): String? {
    var current: Throwable? = error
    while (current != null) {
        if (current is ApiCallException) {
            if (current.statusCode == 426 || current.reason == "app_update_required") {
                return AuthErrorCode.APP_OUTDATED
            }
            if (current.reason == "server_update_required") {
                return AuthErrorCode.SERVER_OUTDATED
            }
        }
        current = current.cause?.takeIf { it !== current }
    }
    return null
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
    // Any 5xx means the address is alive but the backend can't fulfill the request —
    // the backend container is down/restarting (502/503/504), the database is down (500),
    // etc. Sync genuinely can't happen, so treat it exactly like being offline (keep the
    // session, defer sync, show the same "can't reach server" notice). 408 is a request
    // timeout. 4xx stays a real client error, handled elsewhere.
    return statusCode == 408 || statusCode in 500..599
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
