package com.ohmz.tday.plugins

import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.ohmz.tday.plugins.StatusPages")

open class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)
class BadRequestException(message: String = "The server received bad/malformed values") : ApiException(HttpStatusCode.BadRequest, message)
class UnauthorizedException(message: String = "Authentication required") : ApiException(HttpStatusCode.Unauthorized, message)
class ForbiddenException(message: String = "Access denied") : ApiException(HttpStatusCode.Forbidden, message)
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
        exception<ApiException> { call, cause ->
            call.respondApiError(cause.status, cause.message.ifBlank { cause.status.description })
        }
        exception<Throwable> { call, cause ->
            logger.error("api_error", cause)
            call.respondApiError(HttpStatusCode.InternalServerError, "An unexpected error occurred")
        }
    }
}
