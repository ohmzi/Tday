package com.ohmz.tday.routes.auth

import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.respondError
import com.ohmz.tday.domain.respondRateLimit
import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.ThrottleAction
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Route.sessionRoutes() {
    val authThrottle by inject<AuthThrottle>()

    route("/session") {
        get {
            val throttle = authThrottle.enforceRateLimit(ThrottleAction.sessionGet, call.request)
            if (!throttle.allowed) {
                call.respondRateLimit(
                    message = "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    reason = throttle.reasonCode ?: "auth_limit",
                    retryAfterSeconds = throttle.retryAfterSeconds,
                )
                return@get
            }

            val user = call.authUser()
            if (user == null) {
                call.respondError(AppError.Unauthorized("Not authenticated"))
                return@get
            }
            call.respond(
                io.ktor.http.HttpStatusCode.OK,
                buildJsonObject {
                    putJsonObject("user") {
                        put("id", user.id)
                        put("name", user.name)
                        put("username", user.username)
                        put("role", user.role)
                        put("approvalStatus", user.approvalStatus)
                        put("timeZone", user.timeZone)
                        put("requirePasswordChange", user.requirePasswordChange)
                    }
                },
            )
        }
    }
}
