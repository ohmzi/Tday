package com.ohmz.tday.routes.auth

import com.ohmz.tday.security.CredentialEnvelope
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.ThrottleAction
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.credentialsKeyRoutes() {
    val credentialEnvelope by inject<CredentialEnvelope>()
    val authThrottle by inject<AuthThrottle>()

    route("/credentials-key") {
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

            call.respond(HttpStatusCode.OK, credentialEnvelope.getPublicKeyDescriptor())
        }
    }
}
