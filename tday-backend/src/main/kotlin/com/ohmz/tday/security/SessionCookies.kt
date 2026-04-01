package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import java.time.Instant

private const val SECURE_SESSION_COOKIE_NAME = "__Secure-authjs.session-token"
private const val SESSION_COOKIE_NAME = "authjs.session-token"
private const val SESSION_COOKIE_PATH = "/"
private const val SESSION_COOKIE_SAME_SITE = "Lax"

private val SESSION_COOKIE_NAMES = listOf(
    SECURE_SESSION_COOKIE_NAME,
    SESSION_COOKIE_NAME,
)

fun sessionCookieName(isProduction: Boolean): String =
    if (isProduction) SECURE_SESSION_COOKIE_NAME else SESSION_COOKIE_NAME

fun sessionCookieNames(): List<String> = SESSION_COOKIE_NAMES

fun ApplicationCall.issueSessionCookie(
    config: AppConfig,
    jwtService: JwtService,
    claims: JwtUserClaims,
): String {
    val token = jwtService.encode(claims)
    val activeCookieName = sessionCookieName(config.isProduction)

    SESSION_COOKIE_NAMES
        .filterNot { it == activeCookieName }
        .forEach { cookieName ->
            response.cookies.append(expiredSessionCookie(config, cookieName))
        }

    response.cookies.append(
        buildSessionCookie(
            config = config,
            cookieName = activeCookieName,
            value = token,
            maxAge = config.sessionMaxAgeSec,
        ),
    )

    return token
}

fun ApplicationCall.clearSessionCookie(config: AppConfig) {
    SESSION_COOKIE_NAMES.forEach { cookieName ->
        response.cookies.append(expiredSessionCookie(config, cookieName))
    }
}

fun isSessionPastAbsoluteLifetime(
    claims: JwtUserClaims,
    config: AppConfig,
    nowEpochSec: Long = Instant.now().epochSecond,
): Boolean {
    val sessionStartedAtEpochSec = claims.sessionStartedAtEpochSec ?: return false
    val absoluteExpiryEpochSec = sessionStartedAtEpochSec + config.sessionAbsoluteMaxAgeSec.toLong()
    return nowEpochSec >= absoluteExpiryEpochSec
}

fun shouldRenewSession(
    claims: JwtUserClaims,
    config: AppConfig,
    nowEpochSec: Long = Instant.now().epochSecond,
): Boolean {
    val expiresAtEpochSec = claims.expiresAtEpochSec ?: return false
    val remainingSeconds = expiresAtEpochSec - nowEpochSec
    return remainingSeconds in 1..config.sessionRenewThresholdSec.toLong() &&
        !isSessionPastAbsoluteLifetime(claims, config, nowEpochSec)
}

private fun expiredSessionCookie(config: AppConfig, cookieName: String): Cookie =
    buildSessionCookie(
        config = config,
        cookieName = cookieName,
        value = "",
        maxAge = 0,
    )

private fun buildSessionCookie(
    config: AppConfig,
    cookieName: String,
    value: String,
    maxAge: Int,
): Cookie = Cookie(
    name = cookieName,
    value = value,
    maxAge = maxAge,
    path = SESSION_COOKIE_PATH,
    secure = cookieName.startsWith("__Secure-") || config.isProduction,
    httpOnly = true,
    extensions = mapOf("SameSite" to SESSION_COOKIE_SAME_SITE),
)
