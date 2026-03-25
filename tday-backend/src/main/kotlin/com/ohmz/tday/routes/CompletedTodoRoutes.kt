package com.ohmz.tday.routes

import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.CompletedTodoService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class CompletedTodoPatchBody(val id: String)

fun Route.completedTodoRoutes() {
    route("/completedTodo") {
        get {
            val user = call.requireUser()
            val todos = CompletedTodoService.getAll(user.id)
            call.respond(HttpStatusCode.OK, mapOf("completedTodos" to todos))
        }

        delete {
            val user = call.requireUser()
            CompletedTodoService.deleteAll(user.id)
            call.respond(HttpStatusCode.OK, mapOf("message" to "completed todos cleared"))
        }

        patch {
            val user = call.requireUser()
            val body = call.receive<CompletedTodoPatchBody>()
            CompletedTodoService.deleteById(user.id, body.id)
            call.respond(HttpStatusCode.OK, mapOf("message" to "completed todo removed"))
        }
    }
}
