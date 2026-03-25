package com.ohmz.tday.routes.auth

import com.ohmz.tday.security.CredentialEnvelope
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.credentialsKeyRoutes() {
    route("/credentials-key") {
        get {
            call.respond(HttpStatusCode.OK, CredentialEnvelope.getPublicKeyDescriptor())
        }
    }
}
