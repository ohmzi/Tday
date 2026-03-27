package com.ohmz.tday.routes.auth

import com.ohmz.tday.plugins.authUser
import com.ohmz.tday.security.SessionControl
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.logoutRoutes() {
    val sessionControl by inject<SessionControl>()

    route("/logout") {
        post {
            val user = call.authUser()
            if (user != null) {
                sessionControl.revokeUserSessions(user.id)
            }

            val secure = System.getenv("NODE_ENV") == "production"
            val cookieName = if (secure) "__Secure-authjs.session-token" else "authjs.session-token"

            call.response.cookies.append(Cookie(
                name = cookieName,
                value = "",
                maxAge = 0,
                path = "/",
                secure = secure,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Lax"),
            ))

            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out"))
        }
    }
}
