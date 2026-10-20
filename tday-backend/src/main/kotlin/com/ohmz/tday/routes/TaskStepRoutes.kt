package com.ohmz.tday.routes

import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.TaskStepService
import com.ohmz.tday.shared.model.CreateTaskStepRequest
import com.ohmz.tday.shared.model.DeleteTaskStepRequest
import com.ohmz.tday.shared.model.ReorderTaskStepsRequest
import com.ohmz.tday.shared.model.TaskStepMutationResponse
import com.ohmz.tday.shared.model.TaskStepsResponse
import com.ohmz.tday.shared.model.ToggleTaskStepRequest
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Flat checklist steps inside a todo (R6-2). All step ownership is enforced by
 * [TaskStepService] through the parent todo, so handlers only pass [user.id]
 * through. Routes mirror the [com.ohmz.tday.shared.routes.ApiRoutes.Todo.Steps]
 * paths used by every client.
 */
fun Route.taskStepRoutes() {
    val taskStepService by inject<TaskStepService>()

    // GET /api/todo/{todoId}/steps — list a todo's steps in order.
    route("/todo/{todoId}/steps") {
        get {
            call.withAuth { user ->
                val todoId = call.parameters["todoId"].orEmpty()
                if (todoId.isBlank()) {
                    AppError.BadRequest("todo id is required").let { arrow.core.Either.Left(it) }
                } else {
                    taskStepService.listForTodo(user.id, todoId)
                        .map { TaskStepsResponse(steps = it) }
                }
            }
        }
    }

    route("/todo/steps") {
        // POST /api/todo/steps — append a step to a todo.
        post {
            call.withAuth { user ->
                val body = call.receive<CreateTaskStepRequest>()
                taskStepService.create(user.id, body.todoId, body.title)
                    .map { TaskStepMutationResponse(message = "step created", step = it) }
            }
        }

        // POST /api/todo/steps/toggle — check/uncheck a step.
        post("/toggle") {
            call.withAuth { user ->
                val body = call.receive<ToggleTaskStepRequest>()
                taskStepService.toggle(user.id, body.id, body.completed)
                    .map { TaskStepMutationResponse(message = "step toggled", step = it) }
            }
        }

        // POST /api/todo/steps/delete — remove a step.
        post("/delete") {
            call.withAuth { user ->
                val body = call.receive<DeleteTaskStepRequest>()
                taskStepService.delete(user.id, body.id)
                    .map { mapOf("message" to "step deleted") }
            }
        }

        // POST /api/todo/steps/reorder — persist a new step order for a todo.
        post("/reorder") {
            call.withAuth { user ->
                val body = call.receive<ReorderTaskStepsRequest>()
                taskStepService.reorder(user.id, body.todoId, body.orderedIds)
                    .map { mapOf("message" to "steps reordered") }
            }
        }
    }
}
