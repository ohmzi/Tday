package com.ohmz.tday.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.ohmz.tday.plugins.StatusPages")

@Serializable
data class ErrorResponse(val message: String)

open class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)
class BadRequestException(message: String = "The server received bad/malformed values") : ApiException(HttpStatusCode.BadRequest, message)
class UnauthorizedException(message: String = "Authentication required") : ApiException(HttpStatusCode.Unauthorized, message)
class ForbiddenException(message: String = "Access denied") : ApiException(HttpStatusCode.Forbidden, message)
class NotFoundException(message: String = "Resource not found") : ApiException(HttpStatusCode.NotFound, message)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message))
        }
        exception<Throwable> { call, cause ->
            logger.error("api_error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("An unexpected error occurred"))
        }
    }
}
