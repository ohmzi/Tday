package com.ohmz.tday.routes.auth

import com.ohmz.tday.domain.respondRateLimit
import com.ohmz.tday.models.request.RegisterRequest
import com.ohmz.tday.security.*
import com.ohmz.tday.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val USERNAME_REGEX = Regex("^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$")

fun Route.registerRoutes() {
    val userService by inject<UserService>()
    val authThrottle by inject<AuthThrottle>()
    val abuseGuard by inject<AbuseGuard>()

    route("/register") {
        post {
            // Registration stays open; only a source that has already demonstrated abuse of THIS
            // path loses it, and only until the block expires.
            if (call.rejectIfAbuseBlocked(abuseGuard, AbuseScope.register)) return@post

            val body = call.receive<RegisterRequest>()

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.register, call.request, body.username)
            if (!throttle.allowed) {
                abuseGuard.recordSignal(AbuseScope.register, AbuseSignal.registerViolation, call.request)
                call.respondRateLimit(
                    message = "Too many authentication requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    reason = throttle.reasonCode ?: "auth_limit",
                    retryAfterSeconds = throttle.retryAfterSeconds,
                )
                return@post
            }

            if (body.fname.trim().length < 2) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "first name is at least two characters"))
                return@post
            }
            val normalizedUsername = body.username.trim().lowercase()
            if (!USERNAME_REGEX.matches(normalizedUsername)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "username is incorrect"))
                return@post
            }
            if (body.password.length < 8) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "password cannot be smaller than 8"))
                return@post
            }
            if (!body.password.any { it.isUpperCase() }) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "password must have at least one uppercase letter"))
                return@post
            }
            if (!body.password.any { !it.isLetterOrDigit() }) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "password must have at least one special character"))
                return@post
            }

            val securityError = SecurityQuestions.validateSelection(body.securityAnswers, required = 3)
            if (securityError != null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to securityError))
                return@post
            }

            if (userService.usernameExists(normalizedUsername)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "this username is taken"))
                return@post
            }

            val result = userService.register(body.fname, body.lname, normalizedUsername, body.password, body.securityAnswers.orEmpty())
            result.fold(
                { error ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("message" to error.message))
                },
                { reg ->
                    // The behavioural signal: an account that was created but still needs the
                    // owner's approval. One is a new user; a pile of them from one source in a
                    // day is mass registration, however slowly it was paced.
                    if (reg.requiresApproval) {
                        abuseGuard.recordSignal(AbuseScope.register, AbuseSignal.pendingSignup, call.request)
                    }
                    call.respond(
                        HttpStatusCode.OK,
                        buildJsonObject {
                            put(
                                "message",
                                if (reg.requiresApproval) "Account registered. Waiting for admin approval." else "account created",
                            )
                            put("requiresApproval", reg.requiresApproval)
                            put("isBootstrapAdmin", reg.isBootstrapAdmin)
                        },
                    )
                },
            )
        }
    }
}
