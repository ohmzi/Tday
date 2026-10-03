package com.ohmz.tday.plugins

import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.ApiError
import com.ohmz.tday.observability.FingerprintedFailure
import com.ohmz.tday.observability.TdayObservability
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.path
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.ohmz.tday.plugins.StatusPages")
private const val INVALID_REQUEST_BODY_MESSAGE = "Invalid request body"

fun appErrorStatus(error: AppError): HttpStatusCode = when (error) {
    is AppError.NotFound -> HttpStatusCode.NotFound
    is AppError.BadRequest -> HttpStatusCode.BadRequest
    is AppError.Unauthorized -> HttpStatusCode.Unauthorized
    is AppError.Forbidden -> HttpStatusCode.Forbidden
    is AppError.Conflict -> HttpStatusCode.Conflict
    is AppError.Internal -> HttpStatusCode.InternalServerError
}

private suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    message: String,
    field: String? = null,
) {
    respond(status, ApiError(status.value, message, field))
}

suspend fun ApplicationCall.respondAppError(error: AppError) {
    respondApiError(
        status = appErrorStatus(error),
        message = error.message,
        field = (error as? AppError.BadRequest)?.field,
    )
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ContentTransformationException> { call, _ ->
            call.respondApiError(HttpStatusCode.BadRequest, INVALID_REQUEST_BODY_MESSAGE)
        }
        exception<BadRequestException> { call, _ ->
            call.respondApiError(HttpStatusCode.BadRequest, INVALID_REQUEST_BODY_MESSAGE)
        }
        exception<Throwable> { call, cause ->
            TdayObservability.captureException(
                cause,
                operation = "api.unhandled",
                data = mapOf("route" to TdayObservability.sanitizePath(call.request.path())),
                // A failure that named its own identity keeps its own issue, instead of being grouped
                // with everything else that failed on this route.
                fingerprint = (cause as? FingerprintedFailure)?.issueFingerprint.orEmpty(),
            )
            logger.error("api_error", cause)
            call.respondApiError(HttpStatusCode.InternalServerError, "An unexpected error occurred")
        }
    }
}
