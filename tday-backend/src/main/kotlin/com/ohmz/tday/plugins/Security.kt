package com.ohmz.tday.plugins

import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtUserClaims
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val SESSION_COOKIE_NAMES = listOf(
    "__Secure-authjs.session-token",
    "authjs.session-token",
    "__Secure-next-auth.session-token",
    "next-auth.session-token",
)

val AuthUserKey = AttributeKey<JwtUserClaims>("AuthUser")

fun ApplicationCall.authUser(): JwtUserClaims? = attributes.getOrNull(AuthUserKey)

fun ApplicationCall.requireUser(): JwtUserClaims =
    authUser() ?: throw IllegalStateException("Authentication required")

fun Application.configureSecurity() {
    install(Authentication) {
        bearer("jwt") {
            authenticate { tokenCredential ->
                val claims = JwtService.decode(tokenCredential.token)
                if (claims != null) UserIdPrincipal(claims.id) else null
            }
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        val token = resolveSessionToken(call)
        if (token != null) {
            val claims = JwtService.decode(token)
            if (claims != null) {
                val dbUser = transaction {
                    Users.selectAll().where { Users.id eq claims.id }
                        .firstOrNull()
                }

                if (dbUser != null) {
                    val dbTokenVersion = dbUser[Users.tokenVersion]
                    if (claims.tokenVersion == null || claims.tokenVersion == dbTokenVersion) {
                        call.attributes.put(
                            AuthUserKey,
                            claims.copy(
                                role = dbUser[Users.role].name,
                                approvalStatus = dbUser[Users.approvalStatus].name,
                                tokenVersion = dbTokenVersion,
                                timeZone = dbUser[Users.timeZone],
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun resolveSessionToken(call: ApplicationCall): String? {
    val authHeader = call.request.headers[HttpHeaders.Authorization]
    if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
        return authHeader.removePrefix("Bearer ").removePrefix("bearer ").trim().ifEmpty { null }
    }

    for (cookieName in SESSION_COOKIE_NAMES) {
        val cookie = call.request.cookies[cookieName]
        if (!cookie.isNullOrBlank()) return cookie
    }
    return null
}
