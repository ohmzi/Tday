package com.ohmz.tday.plugins

import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.observability.TdayObservability
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
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.services.UserApiKeyService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.invoke
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.ktor.ext.inject

private const val EVENT_DETAIL_USER_ID = "userId"
private const val EVENT_DETAIL_PATH = "path"
private const val API_KEY_PREFIX = "tday_"

val AuthUserKey = AttributeKey<JwtUserClaims>("AuthUser")

/** Present only when the request authenticated via an API key; carries that key's scope. */
val ApiKeyScopeKey = AttributeKey<ApiKeyScope>("ApiKeyScope")

fun ApplicationCall.authUser(): JwtUserClaims? = attributes.getOrNull(AuthUserKey)

fun ApplicationCall.apiKeyScope(): ApiKeyScope? = attributes.getOrNull(ApiKeyScopeKey)

private fun isSafeMethod(method: HttpMethod): Boolean =
    method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options

fun ApplicationCall.requireUser(): JwtUserClaims =
    authUser() ?: throw IllegalStateException("Authentication required")

fun Application.configureSecurity() {
    val config by inject<com.ohmz.tday.config.AppConfig>()
    val jwtService by inject<JwtService>()
    val authUserCache by inject<AuthUserCache>()
    val eventLogger by inject<SecurityEventLogger>()
    val userApiKeyService by inject<UserApiKeyService>()

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
        if (token != null && token.startsWith(API_KEY_PREFIX)) {
            // Per-user API key (e.g. dashboard widgets). Resolves to the owning user and
            // populates the same auth principal the session path uses, then skips renewal.
            val resolved = userApiKeyService.resolveKey(token)
            if (resolved != null) {
                // READ-scoped keys may only issue safe requests. A mutating method is
                // rejected before any handler runs, regardless of route.
                if (resolved.scope == ApiKeyScope.READ && !isSafeMethod(call.request.httpMethod)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        buildJsonObject {
                            put("code", JsonPrimitive(HttpStatusCode.Forbidden.value))
                            put("message", JsonPrimitive("This API key is read-only"))
                            put("reason", JsonPrimitive("api_key_read_only"))
                        },
                    )
                    finish()
                    return@intercept
                }
                val user = loadCachedAuthUser(authUserCache, resolved.userId)
                if (user != null) {
                    call.attributes.put(ApiKeyScopeKey, resolved.scope)
                    call.attributes.put(
                        AuthUserKey,
                        JwtUserClaims(
                            id = resolved.userId,
                            role = user.role,
                            approvalStatus = user.approvalStatus,
                            tokenVersion = user.tokenVersion,
                            timeZone = user.timeZone,
                            requirePasswordChange = user.requirePasswordChange,
                            requireSecurityQuestions = user.requireSecurityQuestions,
                        ),
                    )
                }
            }
            return@intercept
        }

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

                val user = loadCachedAuthUser(authUserCache, claims.id)

                if (user != null) {
                    if (claims.tokenVersion == null || claims.tokenVersion == user.tokenVersion) {
                        val hydratedClaims = claims.copy(
                            role = user.role,
                            approvalStatus = user.approvalStatus,
                            tokenVersion = user.tokenVersion,
                            timeZone = user.timeZone,
                            requirePasswordChange = user.requirePasswordChange,
                            requireSecurityQuestions = user.requireSecurityQuestions,
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

private suspend fun loadCachedAuthUser(
    authUserCache: AuthUserCache,
    userId: String
): AuthCachedUser? {
    authUserCache.get(userId)?.let { return it }
    val dbUser = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll().where { Users.id eq userId }.firstOrNull()
    } ?: return null
    val fetched = AuthCachedUser(
        role = dbUser[Users.role].name,
        approvalStatus = dbUser[Users.approvalStatus].name,
        tokenVersion = dbUser[Users.tokenVersion],
        timeZone = dbUser[Users.timeZone],
        requirePasswordChange = dbUser[Users.requirePasswordChange],
        requireSecurityQuestions = dbUser[Users.requireSecurityQuestions],
    )
    authUserCache.put(userId, fetched)
    return fetched
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
