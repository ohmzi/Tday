package com.ohmz.tday.routes

import com.ohmz.tday.models.request.PreferencesPatchRequest
import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.PreferencesService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.preferencesRoutes() {
    route("/preferences") {
        get {
            val user = call.requireUser()
            val prefs = PreferencesService.get(user.id)
            call.respond(HttpStatusCode.OK, prefs)
        }

        patch {
            val user = call.requireUser()
            val body = call.receive<PreferencesPatchRequest>()
            PreferencesService.update(user.id, body.sortBy, body.groupBy, body.direction)
            call.respond(HttpStatusCode.OK, mapOf("message" to "preferences updated"))
        }
    }
}
