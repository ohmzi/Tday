package com.ohmz.tday.routes.auth

import com.ohmz.tday.models.request.LoginChallengeRequest
import com.ohmz.tday.security.*
import com.ohmz.tday.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.loginChallengeRoutes() {
    val userService by inject<UserService>()
    val authThrottle by inject<AuthThrottle>()
    val passwordProof by inject<PasswordProof>()

    route("/login-challenge") {
        post {
            val body = call.receive<LoginChallengeRequest>()
            val email = passwordProof.normalizeEmail(body.email)
            if (email.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Email is required."))
                return@post
            }

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, email)
            if (!throttle.allowed) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "message" to "Too many authentication requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    "reason" to (throttle.reasonCode ?: "auth_limit"),
                    "retryAfterSeconds" to throttle.retryAfterSeconds,
                ))
                return@post
            }

            val user = userService.findByEmail(email)
            val storedHash = user?.get("password") as? String

            val payload = passwordProof.issueChallenge(email, storedHash)
            call.respond(HttpStatusCode.OK, mapOf(
                "version" to payload.version,
                "algorithm" to payload.algorithm,
                "challengeId" to payload.challengeId,
                "saltHex" to payload.saltHex,
                "iterations" to payload.iterations,
                "expiresAt" to payload.expiresAt,
            ))
        }
    }
}
