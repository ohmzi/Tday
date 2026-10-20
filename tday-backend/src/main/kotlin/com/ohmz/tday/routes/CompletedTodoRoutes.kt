package com.ohmz.tday.routes

import arrow.core.Either
import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.validateOptionalEnumValue
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.CompletedTodoService
import com.ohmz.tday.shared.model.Priority
import com.ohmz.tday.shared.model.DeleteCompletedTodoRequest
import com.ohmz.tday.shared.model.UpdateCompletedTodoRequest
import io.ktor.server.request.*
import io.ktor.server.routing.*

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
                val body = runCatching { call.receiveNullable<DeleteCompletedTodoRequest>() }.getOrNull()
                if (body?.id?.isNotBlank() == true) {
                    completedTodoService.deleteById(user.id, body.id)
                        .map { count ->
                            mapOf("message" to if (count > 0) "completed todo removed" else "completed todo already removed")
                        }
                } else {
                    completedTodoService.deleteAll(user.id)
                        .map { mapOf("message" to "completed todos cleared") }
                }
            }
        }

        patch {
            call.withAuth { user ->
                val body = call.receive<UpdateCompletedTodoRequest>()
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                when (val priority = validateOptionalEnumValue<Priority>(body.priority, "priority")) {
                    is Either.Left -> return@withAuth Either.Left(priority.value)
                    is Either.Right -> priority.value?.let { fields["priority"] = it }
                }
                val due = body.due
                if (due != null) {
                    if (due.isBlank()) {
                        return@withAuth Either.Left(AppError.BadRequest("due is required"))
                    } else {
                        val parsed = parseDueMinute(due)
                            ?: return@withAuth Either.Left(
                                AppError.BadRequest("due must be a valid ISO-8601 datetime"),
                            )
                        fields["due"] = parsed
                    }
                }
                body.rrule?.let { fields["rrule"] = it.takeIf { value -> value.isNotBlank() } }
                body.listID?.let { fields["listID"] = it.takeIf { value -> value.isNotBlank() } }
                if (fields.isEmpty()) {
                    return@withAuth completedTodoService.deleteById(user.id, body.id)
                        .map { count ->
                            mapOf("message" to if (count > 0) "completed todo removed" else "completed todo already removed")
                        }
                }
                completedTodoService.update(user.id, body.id, fields)
                    .map { count ->
                        mapOf("message" to if (count > 0) "completed todo updated" else "completed todo already removed")
                    }
            }
        }
    }
}
