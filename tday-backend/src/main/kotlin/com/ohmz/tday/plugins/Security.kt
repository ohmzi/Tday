package com.ohmz.tday.plugins

import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.security.AuthCachedUser
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.clearSessionCookie
import com.ohmz.tday.security.isSessionPastAbsoluteLifetime
import com.ohmz.tday.security.issueSessionCookie
import com.ohmz.tday.security.sessionCookieNames
import com.ohmz.tday.security.shouldRenewSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.path
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

val AuthUserKey = AttributeKey<JwtUserClaims>("AuthUser")

fun ApplicationCall.authUser(): JwtUserClaims? = attributes.getOrNull(AuthUserKey)

fun ApplicationCall.requireUser(): JwtUserClaims =
    authUser() ?: throw IllegalStateException("Authentication required")

fun Application.configureSecurity() {
    val config by inject<com.ohmz.tday.config.AppConfig>()
    val jwtService by inject<JwtService>()
    val authUserCache by inject<AuthUserCache>()
    val eventLogger by inject<SecurityEventLogger>()

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
                val nowEpochSec = jwtService.currentEpochSeconds()

                if (isSessionPastAbsoluteLifetime(claims, config, nowEpochSec)) {
                    call.clearSessionCookie(config)
                    eventLogger.log(
                        "auth_session_absolute_expired",
                        mapOf(
                            "userId" to claims.id,
                            "path" to call.request.path(),
                        ),
                    )
                    return@intercept
                }

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
                        val hydratedClaims = claims.copy(
                            role = user.role,
                            approvalStatus = user.approvalStatus,
                            tokenVersion = user.tokenVersion,
                            timeZone = user.timeZone,
                        )
                        call.attributes.put(AuthUserKey, hydratedClaims)

                        if (!shouldSkipSessionRenewal(call) && shouldRenewSession(hydratedClaims, config, nowEpochSec)) {
                            val remainingSeconds = (hydratedClaims.expiresAtEpochSec ?: 0L) - nowEpochSec
                            call.issueSessionCookie(config, jwtService, hydratedClaims)
                            eventLogger.log(
                                "auth_session_renewed",
                                mapOf(
                                    "userId" to hydratedClaims.id,
                                    "path" to call.request.path(),
                                    "remainingSeconds" to remainingSeconds.coerceAtLeast(0),
                                ),
                            )
                        }
                    } else {
                        call.clearSessionCookie(config)
                        authUserCache.invalidate(claims.id)
                        eventLogger.log(
                            "auth_session_token_version_mismatch",
                            mapOf(
                                "userId" to claims.id,
                                "path" to call.request.path(),
                            ),
                        )
                    }
                } else {
                    call.clearSessionCookie(config)
                    eventLogger.log(
                        "auth_session_user_missing",
                        mapOf(
                            "userId" to claims.id,
                            "path" to call.request.path(),
                        ),
                    )
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

    for (cookieName in sessionCookieNames()) {
        val cookie = call.request.cookies[cookieName]
        if (!cookie.isNullOrBlank()) return cookie
    }
    return null
}

private fun shouldSkipSessionRenewal(call: ApplicationCall): Boolean {
    return when (call.request.path()) {
        "/api/auth/callback/credentials",
        "/api/auth/logout",
        "/api/user/change-password" -> true
        else -> false
    }
}
