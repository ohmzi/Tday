package com.ohmz.tday.routes

import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.services.NoteService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.noteRoutes() {
    val noteService by inject<NoteService>()

    route("/note") {
        get {
            call.withAuth { user ->
                noteService.getAll(user.id).map { mapOf("notes" to it) }
            }
        }

        post {
            call.withAuth { user ->
                val body = call.receive<NoteCreateRequest>()
                if (body.name.isBlank()) return@withAuth arrow.core.Either.Left(AppError.BadRequest("title cannot be left empty"))
                noteService.create(user.id, body.name, body.content)
                    .map { mapOf("message" to "note created", "note" to it) }
            }
        }

        route("/{id}") {
            patch {
                call.withAuth { user ->
                    val noteId = call.parameters["id"]
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("note id required"))
                    val body = call.receive<NotePatchRequest>()
                    noteService.update(user.id, noteId, body.name, body.content)
                        .map { mapOf("message" to "note updated") }
                }
            }

            delete {
                call.withAuth { user ->
                    val noteId = call.parameters["id"]
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("note id required"))
                    noteService.delete(user.id, noteId)
                        .map { mapOf("message" to "note deleted") }
                }
            }
        }
    }
}
