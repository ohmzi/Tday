package com.ohmz.tday.domain

import arrow.core.Either
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.plugins.respondAppError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

data class AuthenticatedUser(
    val id: String,
    val role: String?,
    val approvalStatus: String?,
    val timeZone: String?,
)

suspend fun ApplicationCall.respondError(error: AppError) = respondAppError(error)

suspend fun <T : Any> ApplicationCall.withAuth(
    status: HttpStatusCode = HttpStatusCode.OK,
    block: suspend (AuthenticatedUser) -> Either<AppError, T>,
) {
    val claims = authUser()
    if (claims == null) {
        respondError(AppError.Unauthorized())
        return
    }
    val user = AuthenticatedUser(claims.id, claims.role, claims.approvalStatus, claims.timeZone)
    when (val result = block(user)) {
        is Either.Right -> respond(status, result.value as Any)
        is Either.Left -> respondError(result.value)
    }
}
