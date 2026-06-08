package com.ohmz.tday.routes.auth

import com.ohmz.tday.di.inject
import com.ohmz.tday.models.request.RequestAdminResetRequest
import com.ohmz.tday.models.request.SelfServiceResetRequest
import com.ohmz.tday.models.request.VerifySecurityAnswersRequest
import com.ohmz.tday.models.response.SecurityAnswerResult
import com.ohmz.tday.models.response.SecurityQuestionsResponse
import com.ohmz.tday.models.response.VerifySecurityAnswersResponse
import com.ohmz.tday.security.AuthThrottle
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

    route("/security-questions") {
        // The static catalogue, for the signup form's question pickers.
        get("/all") {
            call.respond(HttpStatusCode.OK, SecurityQuestionsResponse(SecurityQuestions.ALL))
        }

        // The reset wizard's first step: returns the account's stored questions (2–3) so
        // the client can validate the username and cycle questions. 404s when the account
        // doesn't exist — this intentionally reveals account existence (admin-accepted).
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

            val questions = service.lookupQuestionsForUsername(username)
            if (questions == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf(
                        "message" to "We couldn't find an account with that username.",
                        "reason" to "user_not_found",
                    ),
                )
                return@get
            }
            call.respond(HttpStatusCode.OK, SecurityQuestionsResponse(questions))
        }
    }

    // Verify security answers WITHOUT resetting the password — gates the new-password
    // screen and reports which answer was wrong so the client can swap in another question.
    route("/verify-security-answers") {
        post {
            val body = call.receive<VerifySecurityAnswersRequest>()

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

            if (SecurityQuestions.validateSelection(body.answers, required = 2) != null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("message" to "Unable to verify your answers.", "reason" to "reset_failed"),
                )
                return@post
            }

            val result = service.verifyAnswers(body.username, body.answers)
            if (result.locked) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf(
                        "message" to "Too many attempts. Please request a reset from an administrator.",
                        "reason" to "reset_locked",
                    ),
                )
                return@post
            }
            if (result.valid) {
                authThrottle.clearFailures(call.request, body.username)
            } else {
                authThrottle.recordFailure(call.request, body.username)
            }
            call.respond(
                HttpStatusCode.OK,
                VerifySecurityAnswersResponse(
                    valid = result.valid,
                    results = result.results.map { SecurityAnswerResult(it.questionId, it.correct) },
                ),
            )
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

            // The selection itself is validated before any account lookup so a malformed
            // request never reaches the (timing-sensitive) verification path.
            if (SecurityQuestions.validateSelection(body.answers, required = 2) != null) {
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
