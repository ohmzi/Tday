package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.raise.either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.services.ListService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.listRoutes() {
    val listService by inject<ListService>()

    route("/list") {
        get {
            call.withAuth { user ->
                listService.getAll(user.id).map { mapOf("lists" to it) }
            }
        }

        post {
            call.withAuth { user ->
                val body = call.receive<ListCreateRequest>()
                if (body.name.isBlank()) return@withAuth Either.Left(AppError.BadRequest("title cannot be left empty"))
                listService.create(user.id, body.name, body.color, body.iconKey)
                    .map { mapOf("message" to "list created", "list" to it) }
            }
        }

        patch {
            call.withAuth { user ->
                val body = call.receive<ListPatchRequest>()
                if (body.id.isBlank()) return@withAuth Either.Left(AppError.BadRequest("list id is required"))
                listService.update(user.id, body.id, body.name, body.color, body.iconKey)
                    .map { mapOf("message" to "list updated") }
            }
        }

        delete {
            call.withAuth { user ->
                val body = call.receive<ListDeleteRequest>()
                if (body.id.isBlank()) return@withAuth Either.Left(AppError.BadRequest("list id is required"))
                listService.delete(user.id, body.id)
                    .map { mapOf("message" to "list deleted") }
            }
        }

        get("/{id}") {
            call.withAuth { user ->
                val listId = call.parameters["id"]
                    ?: return@withAuth Either.Left(AppError.BadRequest("list id is required"))
                either<AppError, Map<String, Any?>> {
                    val list = listService.getById(user.id, listId).bind()
                    val todos = listService.getTodosForList(user.id, listId).bind()
                    mapOf("list" to list, "todos" to todos)
                }
            }
        }
    }
}
