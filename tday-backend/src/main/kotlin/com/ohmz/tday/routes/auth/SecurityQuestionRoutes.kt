package com.ohmz.tday.routes.auth

import com.ohmz.tday.di.inject
import com.ohmz.tday.models.request.RequestAdminResetRequest
import com.ohmz.tday.models.request.SelfServiceResetRequest
import com.ohmz.tday.models.response.SecurityQuestionsResponse
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.CaptchaService
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.SecurityQuestions
import com.ohmz.tday.security.ThrottleAction
import com.ohmz.tday.services.ResetOutcome
import com.ohmz.tday.services.SecurityQuestionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.securityQuestionRoutes() {
    val service by inject<SecurityQuestionService>()
    val authThrottle by inject<AuthThrottle>()
    val captchaService by inject<CaptchaService>()
    val eventLogger by inject<SecurityEventLogger>()

    route("/security-questions") {
        // The static catalogue, for the signup form's question pickers.
        get("/all") {
            call.respond(HttpStatusCode.OK, SecurityQuestionsResponse(SecurityQuestions.ALL))
        }

        // The two questions to challenge for a given username. Always returns two
        // questions (a stable decoy pair for unknown/unconfigured accounts) so the
        // response can't be used to enumerate usernames.
        get {
            val username = call.request.queryParameters["username"]?.trim()
            if (username.isNullOrEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "username is required"))
                return@get
            }

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, username)
            if (!throttle.allowed) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf(
                        "message" to "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                        "reason" to (throttle.reasonCode ?: "auth_limit"),
                        "retryAfterSeconds" to throttle.retryAfterSeconds,
                    ),
                )
                return@get
            }

            val questions = service.questionsForUsername(username)
            call.respond(HttpStatusCode.OK, SecurityQuestionsResponse(questions))
        }
    }

    // Self-service reset: verify both answers and set a new password.
    route("/reset-password") {
        post {
            val body = call.receive<SelfServiceResetRequest>()

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, body.username)
            if (!throttle.allowed) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf(
                        "message" to "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                        "reason" to (throttle.reasonCode ?: "auth_limit"),
                        "retryAfterSeconds" to throttle.retryAfterSeconds,
                    ),
                )
                return@post
            }

            if (authThrottle.requiresCaptcha(ThrottleAction.credentials, call.request, body.username)) {
                if (!captchaService.isConfigured()) {
                    eventLogger.log(
                        "auth_captcha_misconfigured",
                        mapOf("action" to "self_service_reset", "reason" to "captcha_secret_missing"),
                    )
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf(
                            "message" to "Additional verification is temporarily unavailable.",
                            "reason" to "captcha_unavailable",
                        ),
                    )
                    return@post
                }
                val captchaResult = captchaService.verify(body.captchaToken, call.request, "self_service_reset")
                if (!captchaResult.ok) {
                    eventLogger.log("self_service_reset_captcha_failed", mapOf("reason" to captchaResult.reason))
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("message" to "Additional verification required.", "reason" to "captcha_required"),
                    )
                    return@post
                }
            }

            // The selection itself is validated before any account lookup so a malformed
            // request never reaches the (timing-sensitive) verification path.
            if (SecurityQuestions.validateSelection(body.answers) != null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Unable to reset password. Check your answers.", "reason" to "reset_failed"),
                )
                return@post
            }
            if (body.newPassword.length < 8) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "password cannot be smaller than 8"))
                return@post
            }
            if (!body.newPassword.any { it.isUpperCase() }) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "password must have at least one uppercase letter"))
                return@post
            }
            if (!body.newPassword.any { !it.isLetterOrDigit() }) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "password must have at least one special character"))
                return@post
            }

            when (service.verifyAndReset(body.username, body.answers, body.newPassword)) {
                ResetOutcome.SUCCESS -> {
                    authThrottle.clearFailures(call.request, body.username)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "password reset"))
                }
                ResetOutcome.LOCKED -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf(
                            "message" to "Too many attempts. Please request a reset from an administrator.",
                            "reason" to "reset_locked",
                        ),
                    )
                }
                ResetOutcome.FAILED -> {
                    authThrottle.recordFailure(call.request, body.username)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Unable to reset password. Check your answers.", "reason" to "reset_failed"),
                    )
                }
            }
        }
    }

    // A locked-out user asks an admin to reset their password. Always responds the
    // same way regardless of whether the account exists.
    route("/request-admin-reset") {
        post {
            val body = call.receive<RequestAdminResetRequest>()

            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentials, call.request, body.username)
            if (!throttle.allowed) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf(
                        "message" to "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                        "reason" to (throttle.reasonCode ?: "auth_limit"),
                        "retryAfterSeconds" to throttle.retryAfterSeconds,
                    ),
                )
                return@post
            }

            service.requestAdminReset(body.username)
            call.respond(
                HttpStatusCode.OK,
                mapOf("message" to "If the account exists, an administrator has been notified."),
            )
        }
    }
}
