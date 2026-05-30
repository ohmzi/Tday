package com.ohmz.tday.routes

import arrow.core.Either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.DeleteCompletedFloaterRequest
import com.ohmz.tday.models.request.UpdateCompletedFloaterRequest
import com.ohmz.tday.services.CompletedFloaterService
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

fun Route.completedFloaterRoutes() {
    val completedFloaterService by inject<CompletedFloaterService>()

    route("/completedFloater") {
        get {
            call.withAuth { user ->
                completedFloaterService.getAll(user.id)
                    .map { mapOf("completedFloaters" to it) }
            }
        }

        delete {
            call.withAuth { user ->
                val body = runCatching { call.receiveNullable<DeleteCompletedFloaterRequest>() }.getOrNull()
                if (body?.id?.isNotBlank() == true) {
                    completedFloaterService.deleteById(user.id, body.id)
                        .map { count ->
                            mapOf("message" to if (count > 0) "completed floater removed" else "completed floater already removed")
                        }
                } else {
                    completedFloaterService.deleteAll(user.id)
                        .map { mapOf("message" to "completed floaters cleared") }
                }
            }
        }

        patch {
            call.withAuth { user ->
                val body = call.receive<UpdateCompletedFloaterRequest>()
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                body.priority?.let { fields["priority"] = it }
                body.listID?.let { fields["listID"] = it.takeIf { value -> value.isNotBlank() } }
                if (fields.isEmpty()) {
                    return@withAuth completedFloaterService.deleteById(user.id, body.id)
                        .map { count ->
                            mapOf("message" to if (count > 0) "completed floater removed" else "completed floater already removed")
                        }
                }
                completedFloaterService.update(user.id, body.id, fields)
                    .map { count ->
                        mapOf("message" to if (count > 0) "completed floater updated" else "completed floater already removed")
                    }
            }
        }
    }
}
