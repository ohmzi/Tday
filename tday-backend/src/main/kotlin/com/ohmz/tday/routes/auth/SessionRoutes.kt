package com.ohmz.tday.routes.auth

import com.ohmz.tday.plugins.authUser
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes() {
    route("/session") {
        get {
            val user = call.authUser()
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Not authenticated"))
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
