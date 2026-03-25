package com.ohmz.tday.routes.auth

import com.ohmz.tday.models.request.RegisterRequest
import com.ohmz.tday.plugins.BadRequestException
import com.ohmz.tday.security.*
import com.ohmz.tday.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.registerRoutes() {
    route("/register") {
        post {
            val body = call.receive<RegisterRequest>()

            val throttle = AuthThrottle.enforceRateLimit(ThrottleAction.register, call.request, body.email)
            if (!throttle.allowed) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "message" to "Too many authentication requests. Try again in ${AuthThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    "reason" to (throttle.reasonCode ?: "auth_limit"),
                    "retryAfterSeconds" to throttle.retryAfterSeconds,
                ))
                return@post
            }

            if (AuthThrottle.requiresCaptcha(ThrottleAction.register, call.request, body.email)) {
                val captchaResult = CaptchaService.verify(body.captchaToken, call.request, "register")
                if (!captchaResult.ok) {
                    SecurityEventLogger.log("register_captcha_failed", mapOf("reason" to captchaResult.reason))
                    call.respond(HttpStatusCode.Forbidden, mapOf(
                        "message" to "Additional verification required.",
                        "reason" to "captcha_required",
                    ))
                    return@post
                }
            }

            if (body.fname.trim().length < 2) throw BadRequestException("first name is at least two characters")
            if (body.email.isBlank() || !body.email.contains("@")) throw BadRequestException("email is incorrect")
            if (body.password.length < 8) throw BadRequestException("password cannot be smaller than 8")
            if (!body.password.any { it.isUpperCase() }) throw BadRequestException("password must have at least one uppercase letter")
            if (!body.password.any { !it.isLetterOrDigit() }) throw BadRequestException("password must have at least one special character")

            if (UserService.emailExists(body.email.trim().lowercase())) {
                throw BadRequestException("this email is taken")
            }

            val result = UserService.register(body.fname, body.lname, body.email.trim().lowercase(), body.password)

            call.respond(HttpStatusCode.OK, mapOf(
                "message" to if (result.requiresApproval) "Account registered. Waiting for admin approval." else "account created",
                "requiresApproval" to result.requiresApproval,
                "isBootstrapAdmin" to result.isBootstrapAdmin,
            ))
        }
    }
}
