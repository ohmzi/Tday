package com.ohmz.tday.routes.auth

import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.ThrottleAction
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import java.security.SecureRandom

fun Route.csrfRoutes() {
    val authThrottle by inject<AuthThrottle>()

    route("/csrf") {
        get {
            val throttle = authThrottle.enforceRateLimit(ThrottleAction.csrf, call.request)
            if (!throttle.allowed) {
                call.respond(HttpStatusCode.TooManyRequests, mapOf(
                    "message" to "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    "reason" to (throttle.reasonCode ?: "auth_limit"),
                ))
                return@get
            }

            val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            call.respond(HttpStatusCode.OK, mapOf("csrfToken" to token))
        }
    }
}
