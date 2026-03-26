package com.ohmz.tday.routes

import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDateTime

fun Route.todoRoutes() {
    val todoService by inject<TodoService>()
    val todoNlpService by inject<TodoNlpService>()
    val appConfigService by inject<AppConfigService>()
    val todoSummaryService by inject<TodoSummaryService>()

    route("/todo") {
        post {
            call.withAuth { user ->
                val body = call.receive<TodoCreateRequest>()
                if (body.title.isBlank()) return@withAuth arrow.core.Either.Left(AppError.BadRequest("title cannot be left empty"))
                val dtstart = LocalDateTime.parse(body.dtstart)
                val due = LocalDateTime.parse(body.due)
                todoService.create(user.id, body.title, body.description, body.priority, dtstart, due, body.rrule, body.listID)
                    .map { mapOf("message" to "todo created", "todo" to it) }
            }
        }

        get {
            call.withAuth { user ->
                val timeZone = user.timeZone ?: "UTC"
                val timeline = call.request.queryParameters["timeline"] == "true"

                if (timeline) {
                    val days = call.request.queryParameters["recurringFutureDays"]?.toIntOrNull() ?: 365
                    todoService.getTimeline(user.id, timeZone, days.coerceIn(1, 3650))
                        .map { mapOf("todos" to it) }
                } else {
                    val start = call.request.queryParameters["start"]?.toLongOrNull()
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("date range start not specified"))
                    val end = call.request.queryParameters["end"]?.toLongOrNull()
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("date range end not specified"))
                    todoService.getByDateRange(user.id, start, end, timeZone)
                        .map { mapOf("todos" to it) }
                }
            }
        }

        patch {
            call.withAuth { user ->
                val body = call.receive<TodoPatchRequest>()
                if (body.id.isBlank()) return@withAuth arrow.core.Either.Left(AppError.BadRequest("todo id is required"))
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
                todoService.update(user.id, body.id, fields)
                    .map { mapOf("message" to "Todo updated") }
            }
        }

        delete {
            call.withAuth { user ->
                val body = call.receive<TodoDeleteRequest>()
                if (body.id.isBlank()) return@withAuth arrow.core.Either.Left(AppError.BadRequest("todo id is required"))
                todoService.delete(user.id, body.id)
                    .map { count -> mapOf("message" to if (count > 0) "todo deleted" else "todo already deleted") }
            }
        }

        route("/complete") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<TodoCompleteRequest>()
                    val instanceDate = body.instanceDate?.let { LocalDateTime.parse(it) }
                    todoService.completeTodo(user.id, body.id, instanceDate)
                        .map { mapOf("message" to "todo completed") }
                }
            }
        }

        route("/uncomplete") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<TodoCompleteRequest>()
                    val instanceDate = body.instanceDate?.let { LocalDateTime.parse(it) }
                    todoService.uncompleteTodo(user.id, body.id, instanceDate)
                        .map { mapOf("message" to "todo uncompleted") }
                }
            }
        }

        route("/prioritize") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<TodoPrioritizeRequest>()
                    todoService.prioritize(user.id, body.id, body.priority)
                        .map { mapOf("message" to "priority updated") }
                }
            }
        }

        route("/reorder") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<TodoReorderRequest>()
                    todoService.reorder(user.id, body.id, body.order)
                        .map { mapOf("message" to "order updated") }
                }
            }
        }

        route("/instance") {
            patch {
                call.withAuth { user ->
                    val body = call.receive<TodoInstancePatchRequest>()
                    val instanceDate = LocalDateTime.parse(body.instanceDate)
                    val fields = mutableMapOf<String, Any?>()
                    body.title?.let { fields["title"] = it }
                    body.description?.let { fields["description"] = it }
                    body.priority?.let { fields["priority"] = it }
                    body.dtstart?.let { fields["dtstart"] = LocalDateTime.parse(it) }
                    body.due?.let { fields["due"] = LocalDateTime.parse(it) }
                    body.durationMinutes?.let { fields["durationMinutes"] = it }
                    todoService.patchInstance(user.id, body.todoId, instanceDate, fields)
                        .map { mapOf("message" to "instance updated") }
                }
            }

            delete {
                call.withAuth { user ->
                    val body = call.receive<TodoInstanceDeleteRequest>()
                    val instanceDate = LocalDateTime.parse(body.instanceDate)
                    todoService.deleteInstance(user.id, body.todoId, instanceDate)
                        .map { mapOf("message" to "instance deleted") }
                }
            }
        }

        route("/overdue") {
            get {
                call.withAuth { user ->
                    val timeZone = user.timeZone ?: "UTC"
                    todoService.getOverdue(user.id, timeZone)
                        .map { mapOf("todos" to it) }
                }
            }
        }

        route("/nlp") {
            post {
                call.withAuth { _ ->
                    val body = call.receive<TodoNlpRequest>()
                    val result = todoNlpService.parse(
                        text = body.text,
                        locale = body.locale,
                        referenceEpochMs = body.referenceEpochMs,
                        timezoneOffsetMinutes = body.timezoneOffsetMinutes,
                        defaultDurationMinutes = body.defaultDurationMinutes,
                    )
                    mapOf(
                        "cleanTitle" to result.cleanTitle,
                        "matchedText" to result.matchedText,
                        "matchStart" to result.matchStart,
                        "startEpochMs" to result.startEpochMs,
                        "dueEpochMs" to result.dueEpochMs,
                    ).right()
                }
            }
        }

        route("/summary") {
            post {
                call.withAuth { user ->
                    val body = call.receive<TodoSummaryRequest>()
                    val timeZone = body.timeZone ?: user.timeZone ?: "UTC"

                    val config = appConfigService.getGlobalConfig().getOrNull()
                    if (config != null && !config.aiSummaryEnabled) {
                        return@withAuth mapOf("summary" to null, "reason" to "disabled").right()
                    }

                    val todos = todoService.getTimeline(user.id, timeZone, 365).getOrNull() ?: emptyList()
                    if (todos.isEmpty()) {
                        return@withAuth mapOf("summary" to "You're clear for now. No tasks need attention in this view.").right()
                    }

                    val prompt = "Summarize these ${todos.size} tasks briefly for the user."
                    val summary = todoSummaryService.generateSummary(prompt)
                    mapOf("summary" to summary).right()
                }
            }
        }
    }
}
