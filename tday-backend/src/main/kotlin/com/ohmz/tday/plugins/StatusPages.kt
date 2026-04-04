package com.ohmz.tday.plugins

import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.sentry.Sentry
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.ohmz.tday.plugins.StatusPages")

@Deprecated("Migrate to AppError sealed interface with Either<AppError, T>", ReplaceWith("AppError"))
open class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

@Deprecated("Use AppError.BadRequest instead", ReplaceWith("AppError.BadRequest(message)"))
class BadRequestException(message: String = "The server received bad/malformed values") : ApiException(HttpStatusCode.BadRequest, message)

@Deprecated("Use AppError.Unauthorized instead", ReplaceWith("AppError.Unauthorized(message)"))
class UnauthorizedException(message: String = "Authentication required") : ApiException(HttpStatusCode.Unauthorized, message)

@Deprecated("Use AppError.Forbidden instead", ReplaceWith("AppError.Forbidden(message)"))
class ForbiddenException(message: String = "Access denied") : ApiException(HttpStatusCode.Forbidden, message)

@Deprecated("Use AppError.NotFound instead", ReplaceWith("AppError.NotFound(message)"))
class NotFoundException(message: String = "Resource not found") : ApiException(HttpStatusCode.NotFound, message)

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
        @Suppress("DEPRECATION")
        exception<ApiException> { call, cause ->
            call.respondApiError(cause.status, cause.message.ifBlank { cause.status.description })
        }
        exception<Throwable> { call, cause ->
            Sentry.captureException(cause)
            logger.error("api_error", cause)
            call.respondApiError(HttpStatusCode.InternalServerError, "An unexpected error occurred")
        }
    }
}
