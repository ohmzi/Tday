package com.ohmz.tday.routes.auth

import com.ohmz.tday.domain.respondRateLimit
import com.ohmz.tday.models.request.LoginChallengeRequest
import com.ohmz.tday.security.*
import com.ohmz.tday.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Route.loginChallengeRoutes() {
    val userService by inject<UserService>()
    val authThrottle by inject<AuthThrottle>()
    val passwordProof by inject<PasswordProof>()
    val abuseGuard by inject<AbuseGuard>()

    route("/login-challenge") {
        post {
            // Part of the sign-in path: blocked here too, or an abuser keeps the free PBKDF2
            // parameter lookup that precedes every credential attempt.
            if (call.rejectIfAbuseBlocked(abuseGuard, AbuseScope.auth)) return@post

            val body = call.receive<LoginChallengeRequest>()
            val username = passwordProof.normalizeUsername(body.username)
            if (username.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Username is required."))
                return@post
            }

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, username)
            if (!throttle.allowed) {
                call.respondRateLimit(
                    message = "Too many authentication requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    reason = throttle.reasonCode ?: "auth_limit",
                    retryAfterSeconds = throttle.retryAfterSeconds,
                )
                return@post
            }

            val user = userService.findByUsername(username)
            val storedHash = user?.get("password") as? String

            val payload = passwordProof.issueChallenge(username, storedHash)
            call.respond(
                HttpStatusCode.OK,
                buildJsonObject {
                    put("version", payload.version)
                    put("algorithm", payload.algorithm)
                    put("challengeId", payload.challengeId)
                    put("saltHex", payload.saltHex)
                    put("iterations", payload.iterations)
                    put("expiresAt", payload.expiresAt)
                },
            )
        }
    }
}
