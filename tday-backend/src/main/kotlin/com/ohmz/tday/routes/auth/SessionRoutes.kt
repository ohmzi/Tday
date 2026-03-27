package com.ohmz.tday.routes.auth

import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.respondError
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.ThrottleAction
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes() {
    val authThrottle by inject<AuthThrottle>()

    route("/session") {
        get {
            val throttle = authThrottle.enforceRateLimit(ThrottleAction.session, call.request)
            if (!throttle.allowed) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "message" to "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    "reason" to (throttle.reasonCode ?: "auth_limit"),
                    "retryAfterSeconds" to throttle.retryAfterSeconds,
                ))
                return@get
            }

            val user = call.authUser()
            if (user == null) {
                call.respondError(AppError.Unauthorized("Not authenticated"))
                return@get
            }
            call.respond(HttpStatusCode.OK, mapOf(
                "user" to mapOf(
                    "id" to user.id,
                    "name" to user.name,
                    "email" to user.email,
                    "role" to user.role,
                    "approvalStatus" to user.approvalStatus,
                    "timeZone" to user.timeZone,
                ),
            ))
        }
    }
}
