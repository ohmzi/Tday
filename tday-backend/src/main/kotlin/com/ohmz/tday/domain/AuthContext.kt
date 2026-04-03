package com.ohmz.tday.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.plugins.respondAppError
import com.ohmz.tday.security.JwtUserClaims
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

const val PENDING_APPROVAL_MESSAGE = "your account is awaiting admin approval"
const val ADMIN_ACCESS_REQUIRED_MESSAGE = "admin access required"

data class AuthenticatedUser(
    val id: String,
    val role: String?,
    val approvalStatus: String?,
    val timeZone: String?,
)

suspend fun ApplicationCall.respondRateLimit(
    message: String,
    reason: String,
    retryAfterSeconds: Int,
) {
    response.headers.append(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
    respond(
        HttpStatusCode.TooManyRequests,
        buildJsonObject {
            put("message", JsonPrimitive(message))
            put("reason", JsonPrimitive(reason))
            put("retryAfterSeconds", JsonPrimitive(retryAfterSeconds))
        },
    )
}

suspend fun ApplicationCall.respondError(error: AppError) = respondAppError(error)

fun JwtUserClaims.toAuthenticatedUser(): AuthenticatedUser =
    AuthenticatedUser(
        id = id,
        role = role,
        approvalStatus = approvalStatus,
        timeZone = timeZone,
    )

fun AuthenticatedUser.requireApproved(): Either<AppError, AuthenticatedUser> {
    if (approvalStatus != "APPROVED") {
        return Either.Left(AppError.Forbidden(PENDING_APPROVAL_MESSAGE))
    }
    return right()
}

fun AuthenticatedUser.requireAdminAccess(): Either<AppError, AuthenticatedUser> = either {
    requireApproved().bind()
    if (role != "ADMIN") raise(AppError.Forbidden(ADMIN_ACCESS_REQUIRED_MESSAGE))
    this@requireAdminAccess
}

fun ApplicationCall.requireApprovedAuthUser(): Either<AppError, AuthenticatedUser> {
    val claims = authUser() ?: return Either.Left(AppError.Unauthorized())
    return claims.toAuthenticatedUser().requireApproved()
}

suspend fun <T : Any> ApplicationCall.withAuth(
    status: HttpStatusCode = HttpStatusCode.OK,
    block: suspend (AuthenticatedUser) -> Either<AppError, T>,
) {
    val user = when (val authResult = requireApprovedAuthUser()) {
        is Either.Left -> {
            respondError(authResult.value)
            return
        }
        is Either.Right -> authResult.value
    }
    when (val result = block(user)) {
        is Either.Right -> respond(status, result.value as Any)
        is Either.Left -> respondError(result.value)
    }
}
