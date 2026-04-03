package com.ohmz.tday.routes.auth

import com.ohmz.tday.security.CredentialEnvelope
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.ThrottleAction
import com.ohmz.tday.domain.respondRateLimit
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.credentialsKeyRoutes() {
    val credentialEnvelope by inject<CredentialEnvelope>()
    val authThrottle by inject<AuthThrottle>()

    route("/credentials-key") {
        get {
            val throttle = authThrottle.enforceRateLimit(ThrottleAction.credentialsKey, call.request)
            if (!throttle.allowed) {
                call.respondRateLimit(
                    message = "Too many requests. Try again in ${authThrottle.formatRetryWait(throttle.retryAfterSeconds)}.",
                    reason = throttle.reasonCode ?: "auth_limit",
                    retryAfterSeconds = throttle.retryAfterSeconds,
                )
                return@get
            }

            call.respond(io.ktor.http.HttpStatusCode.OK, credentialEnvelope.getPublicKeyDescriptor())
        }
    }
}
