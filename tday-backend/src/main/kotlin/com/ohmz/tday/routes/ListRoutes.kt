package com.ohmz.tday.routes

import com.ohmz.tday.models.request.*
import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.ListService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.listRoutes() {
    route("/list") {
        get {
            val user = call.requireUser()
            val lists = ListService.getAll(user.id)
            call.respond(HttpStatusCode.OK, mapOf("lists" to lists))
        }

        post {
            val user = call.requireUser()
            val body = call.receive<ListCreateRequest>()
            if (body.name.isBlank()) throw BadRequestException("title cannot be left empty")
            val result = ListService.create(user.id, body.name, body.color, body.iconKey)
            call.respond(HttpStatusCode.OK, mapOf("message" to "list created", "list" to result))
        }

        patch {
            val user = call.requireUser()
            val body = call.receive<ListPatchRequest>()
            if (body.id.isBlank()) throw BadRequestException("list id is required")
            ListService.update(user.id, body.id, body.name, body.color, body.iconKey)
            call.respond(HttpStatusCode.OK, mapOf("message" to "list updated"))
        }

        delete {
            val user = call.requireUser()
            val body = call.receive<ListDeleteRequest>()
            if (body.id.isBlank()) throw BadRequestException("list id is required")
            ListService.delete(user.id, body.id)
            call.respond(HttpStatusCode.OK, mapOf("message" to "list deleted"))
        }

        get("/{id}") {
            val user = call.requireUser()
            val listId = call.parameters["id"] ?: throw BadRequestException("list id is required")
            val list = ListService.getById(user.id, listId) ?: throw NotFoundException("list not found")
            val todos = ListService.getTodosForList(user.id, listId)
            call.respond(HttpStatusCode.OK, mapOf("list" to list, "todos" to todos))
        }
    }
}
