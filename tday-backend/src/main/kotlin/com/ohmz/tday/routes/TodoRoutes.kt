package com.ohmz.tday.routes

import com.ohmz.tday.models.request.*
import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.TodoService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime

fun Route.todoRoutes() {
    route("/todo") {
        post {
            val user = call.requireUser()
            val body = call.receive<TodoCreateRequest>()
            if (body.title.isBlank()) throw BadRequestException("title cannot be left empty")

            val dtstart = LocalDateTime.parse(body.dtstart)
            val due = LocalDateTime.parse(body.due)

            val result = TodoService.create(
                userId = user.id, title = body.title, description = body.description,
                priority = body.priority, dtstart = dtstart, due = due,
                rrule = body.rrule, listID = body.listID,
            )
            call.respond(HttpStatusCode.OK, mapOf("message" to "todo created", "todo" to result))
        }

        get {
            val user = call.requireUser()
            val timeZone = user.timeZone ?: "UTC"
            val timeline = call.request.queryParameters["timeline"] == "true"

            if (timeline) {
                val days = call.request.queryParameters["recurringFutureDays"]?.toIntOrNull() ?: 365
                val todos = TodoService.getTimeline(user.id, timeZone, days.coerceIn(1, 3650))
                call.respond(HttpStatusCode.OK, mapOf("todos" to todos))
                return@get
            }

            val start = call.request.queryParameters["start"]?.toLongOrNull()
                ?: throw BadRequestException("date range start not specified")
            val end = call.request.queryParameters["end"]?.toLongOrNull()
                ?: throw BadRequestException("date range end not specified")

            val todos = TodoService.getByDateRange(user.id, start, end, timeZone)
            call.respond(HttpStatusCode.OK, mapOf("todos" to todos))
        }

        patch {
            val user = call.requireUser()
            val body = call.receive<TodoPatchRequest>()
            if (body.id.isBlank()) throw BadRequestException("todo id is required")

            val fields = mutableMapOf<String, Any?>()
            body.title?.let { fields["title"] = it }
            body.description?.let { fields["description"] = it }
            body.priority?.let { fields["priority"] = it }
            body.pinned?.let { fields["pinned"] = it }
            body.completed?.let { fields["completed"] = it }
            body.dtstart?.let { fields["dtstart"] = LocalDateTime.parse(it) }
            body.due?.let { fields["due"] = LocalDateTime.parse(it) }
            body.rrule?.let { fields["rrule"] = it }
            body.listID?.let { fields["listID"] = it }

            TodoService.update(user.id, body.id, fields)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Todo updated"))
        }

        delete {
            val user = call.requireUser()
            val body = call.receive<TodoDeleteRequest>()
            if (body.id.isBlank()) throw BadRequestException("todo id is required")

            val count = TodoService.delete(user.id, body.id)
            val message = if (count > 0) "todo deleted" else "todo already deleted"
            call.respond(HttpStatusCode.OK, mapOf("message" to message))
        }

        route("/complete") {
            patch {
                val user = call.requireUser()
                val body = call.receive<TodoCompleteRequest>()
                val instanceDate = body.instanceDate?.let { LocalDateTime.parse(it) }
                TodoService.completeTodo(user.id, body.id, instanceDate)
                call.respond(HttpStatusCode.OK, mapOf("message" to "todo completed"))
            }
        }

        route("/uncomplete") {
            patch {
                val user = call.requireUser()
                val body = call.receive<TodoCompleteRequest>()
                val instanceDate = body.instanceDate?.let { LocalDateTime.parse(it) }
                TodoService.uncompleteTodo(user.id, body.id, instanceDate)
                call.respond(HttpStatusCode.OK, mapOf("message" to "todo uncompleted"))
            }
        }

        route("/prioritize") {
            patch {
                val user = call.requireUser()
                val body = call.receive<TodoPrioritizeRequest>()
                TodoService.prioritize(user.id, body.id, body.priority)
                call.respond(HttpStatusCode.OK, mapOf("message" to "priority updated"))
            }
        }

        route("/reorder") {
            patch {
                val user = call.requireUser()
                val body = call.receive<TodoReorderRequest>()
                TodoService.reorder(user.id, body.id, body.order)
                call.respond(HttpStatusCode.OK, mapOf("message" to "order updated"))
            }
        }

        route("/instance") {
            patch {
                val user = call.requireUser()
                val body = call.receive<TodoInstancePatchRequest>()
                val instanceDate = LocalDateTime.parse(body.instanceDate)
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                body.priority?.let { fields["priority"] = it }
                body.dtstart?.let { fields["dtstart"] = LocalDateTime.parse(it) }
                body.due?.let { fields["due"] = LocalDateTime.parse(it) }
                body.durationMinutes?.let { fields["durationMinutes"] = it }
                TodoService.patchInstance(user.id, body.todoId, instanceDate, fields)
                call.respond(HttpStatusCode.OK, mapOf("message" to "instance updated"))
            }

            delete {
                val user = call.requireUser()
                val body = call.receive<TodoInstanceDeleteRequest>()
                val instanceDate = LocalDateTime.parse(body.instanceDate)
                TodoService.deleteInstance(user.id, body.todoId, instanceDate)
                call.respond(HttpStatusCode.OK, mapOf("message" to "instance deleted"))
            }
        }

        route("/overdue") {
            get {
                val user = call.requireUser()
                val timeZone = user.timeZone ?: "UTC"
                val todos = TodoService.getOverdue(user.id, timeZone)
                call.respond(HttpStatusCode.OK, mapOf("todos" to todos))
            }
        }

        route("/nlp") {
            post {
                call.requireUser()
                val body = call.receive<TodoNlpRequest>()
                val result = com.ohmz.tday.services.TodoNlpService.parse(
                    text = body.text,
                    locale = body.locale,
                    referenceEpochMs = body.referenceEpochMs,
                    timezoneOffsetMinutes = body.timezoneOffsetMinutes,
                    defaultDurationMinutes = body.defaultDurationMinutes,
                )
                call.respond(HttpStatusCode.OK, mapOf(
                    "cleanTitle" to result.cleanTitle,
                    "matchedText" to result.matchedText,
                    "matchStart" to result.matchStart,
                    "startEpochMs" to result.startEpochMs,
                    "dueEpochMs" to result.dueEpochMs,
                ))
            }
        }

        route("/summary") {
            post {
                val user = call.requireUser()
                val body = call.receive<TodoSummaryRequest>()
                val timeZone = body.timeZone ?: user.timeZone ?: "UTC"

                val config = com.ohmz.tday.services.AppConfigService.getGlobalConfig()
                if (!config.aiSummaryEnabled) {
                    call.respond(HttpStatusCode.OK, mapOf("summary" to null, "reason" to "disabled"))
                    return@post
                }

                val todos = TodoService.getTimeline(user.id, timeZone, 365)
                if (todos.isEmpty()) {
                    call.respond(HttpStatusCode.OK, mapOf("summary" to "You're clear for now. No tasks need attention in this view."))
                    return@post
                }

                val prompt = "Summarize these ${todos.size} tasks briefly for the user."
                val summary = com.ohmz.tday.services.TodoSummaryService.generateSummary(prompt)
                call.respond(HttpStatusCode.OK, mapOf("summary" to summary))
            }
        }
    }
}
