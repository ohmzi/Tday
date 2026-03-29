package com.ohmz.tday.plugins

import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.security.AuthCachedUser
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtUserClaims
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

private const val SECURE_SESSION_COOKIE_NAME = "__Secure-authjs.session-token"
private const val SESSION_COOKIE_NAME = "authjs.session-token"

private val SESSION_COOKIE_NAMES = listOf(
    SECURE_SESSION_COOKIE_NAME,
    SESSION_COOKIE_NAME,
)

fun sessionCookieName(isProduction: Boolean): String =
    if (isProduction) SECURE_SESSION_COOKIE_NAME else SESSION_COOKIE_NAME

val AuthUserKey = AttributeKey<JwtUserClaims>("AuthUser")

fun ApplicationCall.authUser(): JwtUserClaims? = attributes.getOrNull(AuthUserKey)

fun ApplicationCall.requireUser(): JwtUserClaims =
    authUser() ?: throw IllegalStateException("Authentication required")

fun Application.configureSecurity() {
    val jwtService by inject<JwtService>()
    val authUserCache by inject<AuthUserCache>()

    install(Authentication) {
        bearer("jwt") {
            authenticate { tokenCredential ->
                val claims = jwtService.decode(tokenCredential.token)
                if (claims != null) UserIdPrincipal(claims.id) else null
            }
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        val token = resolveSessionToken(call)
        if (token != null) {
            val claims = jwtService.decode(token)
            if (claims != null) {
                val cached = authUserCache.get(claims.id)
                val user = if (cached != null) {
                    cached
                } else {
                    val dbUser = newSuspendedTransaction(Dispatchers.IO) {
                        Users.selectAll().where { Users.id eq claims.id }
                            .firstOrNull()
                    }
                    if (dbUser != null) {
                        val fetched = AuthCachedUser(
                            role = dbUser[Users.role].name,
                            approvalStatus = dbUser[Users.approvalStatus].name,
                            tokenVersion = dbUser[Users.tokenVersion],
                            timeZone = dbUser[Users.timeZone],
                        )
                        authUserCache.put(claims.id, fetched)
                        fetched
                    } else null
                }

                if (user != null) {
                    if (claims.tokenVersion == null || claims.tokenVersion == user.tokenVersion) {
                        call.attributes.put(
                            AuthUserKey,
                            claims.copy(
                                role = user.role,
                                approvalStatus = user.approvalStatus,
                                tokenVersion = user.tokenVersion,
                                timeZone = user.timeZone,
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
