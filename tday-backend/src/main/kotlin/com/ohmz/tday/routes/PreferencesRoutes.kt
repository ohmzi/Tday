package com.ohmz.tday.routes

import arrow.core.right
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.PreferencesPatchRequest
import com.ohmz.tday.services.PreferencesService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.preferencesRoutes() {
    val preferencesService by inject<PreferencesService>()

    route("/preferences") {
        get {
            call.withAuth { user ->
                preferencesService.get(user.id)
            }
        }

        patch {
            call.withAuth { user ->
                val body = call.receive<PreferencesPatchRequest>()
                preferencesService.update(user.id, body.sortBy, body.groupBy, body.direction)
                    .map { mapOf("message" to "preferences updated") }
            }
        }
    }
}
