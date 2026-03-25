package com.ohmz.tday.routes

import com.ohmz.tday.models.request.*
import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.NoteService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.noteRoutes() {
    route("/note") {
        get {
            val user = call.requireUser()
            val notes = NoteService.getAll(user.id)
            call.respond(HttpStatusCode.OK, mapOf("notes" to notes))
        }

        post {
            val user = call.requireUser()
            val body = call.receive<NoteCreateRequest>()
            if (body.name.isBlank()) throw BadRequestException("title cannot be left empty")
            val result = NoteService.create(user.id, body.name, body.content)
            call.respond(HttpStatusCode.OK, mapOf("message" to "note created", "note" to result))
        }

        route("/{id}") {
            patch {
                val user = call.requireUser()
                val noteId = call.parameters["id"] ?: throw BadRequestException("note id required")
                val body = call.receive<NotePatchRequest>()
                NoteService.update(user.id, noteId, body.name, body.content)
                call.respond(HttpStatusCode.OK, mapOf("message" to "note updated"))
            }

            delete {
                val user = call.requireUser()
                val noteId = call.parameters["id"] ?: throw BadRequestException("note id required")
                NoteService.delete(user.id, noteId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "note deleted"))
            }
        }
    }
}
