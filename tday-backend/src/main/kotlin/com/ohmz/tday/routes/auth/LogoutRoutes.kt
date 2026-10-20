package com.ohmz.tday.routes.auth

import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.security.SessionControl
import com.ohmz.tday.security.clearSessionCookie
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import com.ohmz.tday.config.AppConfig

fun Route.logoutRoutes() {
    val config by inject<AppConfig>()
    val sessionControl by inject<SessionControl>()

    route("/logout") {
        post {
            val user = call.authUser()
            if (user != null) {
                sessionControl.revokeUserSessions(user.id)
            }

            call.clearSessionCookie(config)

            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }
    }
}
