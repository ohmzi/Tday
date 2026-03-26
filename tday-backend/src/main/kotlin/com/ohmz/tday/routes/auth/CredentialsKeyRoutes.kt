package com.ohmz.tday.routes.auth

import com.ohmz.tday.security.CredentialEnvelope
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.credentialsKeyRoutes() {
    val credentialEnvelope by inject<CredentialEnvelope>()

    route("/credentials-key") {
        get {
            call.respond(HttpStatusCode.OK, credentialEnvelope.getPublicKeyDescriptor())
        }
    }
}
