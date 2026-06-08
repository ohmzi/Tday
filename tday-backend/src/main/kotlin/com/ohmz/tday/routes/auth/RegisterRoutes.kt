package com.ohmz.tday.routes.auth

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
    val captchaService by inject<CaptchaService>()
    val eventLogger by inject<SecurityEventLogger>()

    route("/register") {
        post {
            val body = call.receive<RegisterRequest>()

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.register, call.request, body.username)
            if (!throttle.allowed) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "message" to "Too many authentication requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    "reason" to (throttle.reasonCode ?: "auth_limit"),
                    "retryAfterSeconds" to throttle.retryAfterSeconds,
                ))
                return@post
            }

            // Only enforce captcha when a provider is configured; otherwise fall back to
            // the rate-limit + lockout above instead of hard-blocking with a 503 (which
            // would brick self-host registration). See CredentialsCallbackRoutes.
            if (captchaService.isConfigured() && authThrottle.requiresCaptcha(ThrottleAction.register, call.request, body.username)) {
                val captchaResult = captchaService.verify(body.captchaToken, call.request, "register")
                if (!captchaResult.ok) {
                    eventLogger.log("register_captcha_failed", mapOf("reason" to captchaResult.reason))
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "message" to "Additional verification required.",
                        "reason" to "captcha_required",
                    ))
                    return@post
                }
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
