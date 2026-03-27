package com.ohmz.tday.routes

import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.CompletedTodoService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import com.ohmz.tday.di.inject

@Serializable
private data class CompletedTodoPatchBody(val id: String)

fun Route.completedTodoRoutes() {
    val completedTodoService by inject<CompletedTodoService>()

    route("/completedTodo") {
        get {
            call.withAuth { user ->
                completedTodoService.getAll(user.id)
                    .map { mapOf("completedTodos" to it) }
            }
        }

        delete {
            call.withAuth { user ->
                completedTodoService.deleteAll(user.id)
                    .map { mapOf("message" to "completed todos cleared") }
            }
        }

        patch {
            call.withAuth { user ->
                val body = call.receive<CompletedTodoPatchBody>()
                completedTodoService.deleteById(user.id, body.id)
                    .map { mapOf("message" to "completed todo removed") }
            }
        }
    }
}
