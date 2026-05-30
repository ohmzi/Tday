package com.ohmz.tday.routes

import arrow.core.raise.either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.validateCreateFloater
import com.ohmz.tday.domain.validateOrFail
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.models.response.CreateFloaterResponse
import com.ohmz.tday.services.FloaterService
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.floaterRoutes() {
    val floaterService by inject<FloaterService>()

    route("/floater") {
        get {
            call.withAuth { user ->
                floaterService.getAll(user.id).map { mapOf("floaters" to it) }
            }
        }

        post {
            call.withAuth { user ->
                either {
                    val body = call.receive<FloaterCreateRequest>()
                    validateCreateFloater.validateOrFail(body).bind()
                    val floater = floaterService.create(
                        userId = user.id,
                        title = body.title,
                        description = body.description,
                        priority = body.priority,
                        listID = body.listID,
                    ).bind()
                    CreateFloaterResponse(message = "floater created", floater = floater)
                }
            }
        }

        patch {
            call.withAuth { user ->
                either {
                    val body = call.receive<FloaterPatchRequest>()
                    val fields = mutableMapOf<String, Any?>()
                    body.title?.let { fields["title"] = it }
                    body.description?.let { fields["description"] = it }
                    body.priority?.let { fields["priority"] = it }
                    body.pinned?.let { fields["pinned"] = it }
                    body.completed?.let { fields["completed"] = it }
                    body.listID?.let { fields["listID"] = it.takeIf { value -> value.isNotBlank() } }
                    floaterService.update(user.id, body.id, fields).bind()
                    mapOf("message" to "floater updated")
                }
            }
        }

        delete {
            call.withAuth { user ->
                val body = call.receive<FloaterDeleteRequest>()
                if (body.id.isBlank()) {
                    return@withAuth arrow.core.Either.Left(AppError.BadRequest("floater id is required"))
                }
                floaterService.delete(user.id, body.id)
                    .map { count -> mapOf("message" to if (count > 0) "floater deleted" else "floater already deleted") }
            }
        }

        route("/complete") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<FloaterCompleteRequest>()
                    floaterService.completeFloater(user.id, body.id)
                        .map { mapOf("message" to "floater completed") }
                }
            }
        }

        route("/uncomplete") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<FloaterUncompleteRequest>()
                    floaterService.uncompleteFloater(user.id, body.id)
                        .map { mapOf("message" to "floater uncompleted") }
                }
            }
        }

        route("/prioritize") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<FloaterPrioritizeRequest>()
                    floaterService.prioritize(user.id, body.id, body.priority)
                        .map { mapOf("message" to "priority updated") }
                }
            }
        }

        route("/reorder") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<FloaterReorderRequest>()
                    floaterService.reorder(user.id, body.id, body.order)
                        .map { mapOf("message" to "order updated") }
                }
            }
        }
    }
}
