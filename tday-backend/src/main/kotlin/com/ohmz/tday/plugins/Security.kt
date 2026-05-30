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
import com.ohmz.tday.observability.TdayObservability
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

private const val EVENT_DETAIL_USER_ID = "userId"
private const val EVENT_DETAIL_PATH = "path"

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
        val versionBlock = mobileVersionBlock(call, config)
        if (versionBlock != null) {
            call.respondMobileVersionBlock(versionBlock)
            finish()
            return@intercept
        }

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
                            EVENT_DETAIL_USER_ID to claims.id,
                            EVENT_DETAIL_PATH to securityEventPath(call),
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
                                    EVENT_DETAIL_USER_ID to hydratedClaims.id,
                                    EVENT_DETAIL_PATH to securityEventPath(call),
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
                                EVENT_DETAIL_USER_ID to claims.id,
                                EVENT_DETAIL_PATH to securityEventPath(call),
                            ),
                        )
                    }
                } else {
                    call.clearSessionCookie(config)
                    eventLogger.log(
                        "auth_session_user_missing",
                        mapOf(
                            EVENT_DETAIL_USER_ID to claims.id,
                            EVENT_DETAIL_PATH to securityEventPath(call),
                        ),
                    )
                }
            }
        }
    }
}

private data class MobileVersionBlock(
    val status: HttpStatusCode,
    val reason: String,
    val message: String,
    val appVersion: String,
    val requiredVersion: String,
)

private fun mobileVersionBlock(
    call: ApplicationCall,
    config: com.ohmz.tday.config.AppConfig,
): MobileVersionBlock? {
    if (!config.probeUpdateRequired) return null
    if (!config.probeCompatibilityMode.equals("exact", ignoreCase = true)) return null

    val path = call.request.path()
    if (!path.startsWith("/api/") || path == "/api/mobile/probe") return null

    val client = call.request.headers["X-Tday-Client"]?.lowercase()
    if (client != "android-compose" && client != "ios") return null

    val appVersion = call.request.headers["X-Tday-App-Version"]
        ?.trim()
        ?.ifEmpty { null }
        ?: return null
    val requiredVersion = config.probeAppVersion?.trim()?.ifEmpty { null } ?: return null
    val comparison = compareVersions(appVersion, requiredVersion)

    return when {
        comparison < 0 -> MobileVersionBlock(
            status = HttpStatusCode(426, "Upgrade Required"),
            reason = "app_update_required",
            message = "This version of the app is out of date. Please update to continue.",
            appVersion = appVersion,
            requiredVersion = requiredVersion,
        )
        comparison > 0 -> MobileVersionBlock(
            status = HttpStatusCode.Conflict,
            reason = "server_update_required",
            message = "Your app requires a newer server version. Please update the server to continue.",
            appVersion = appVersion,
            requiredVersion = requiredVersion,
        )
        else -> null
    }
}

private suspend fun ApplicationCall.respondMobileVersionBlock(block: MobileVersionBlock) {
    respond(
        block.status,
        buildJsonObject {
            put("code", JsonPrimitive(block.status.value))
            put("message", JsonPrimitive(block.message))
            put("reason", JsonPrimitive(block.reason))
            put("appVersion", JsonPrimitive(block.appVersion))
            put("requiredVersion", JsonPrimitive(block.requiredVersion))
        },
    )
}

private fun compareVersions(a: String, b: String): Int {
    val aParts = a.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val bParts = b.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val maxLength = maxOf(aParts.size, bParts.size)
    for (index in 0 until maxLength) {
        val av = aParts.getOrNull(index) ?: 0
        val bv = bParts.getOrNull(index) ?: 0
        if (av != bv) return av.compareTo(bv)
    }
    return 0
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

private fun securityEventPath(call: ApplicationCall): String =
    TdayObservability.sanitizePath(call.request.path())

private fun shouldSkipSessionRenewal(call: ApplicationCall): Boolean {
    return when (call.request.path()) {
        "/api/auth/callback/credentials",
        "/api/auth/logout",
        "/api/user/change-password" -> true
        else -> false
    }
}
