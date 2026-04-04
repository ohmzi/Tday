package com.ohmz.tday.routes

import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.validateCreateTodo
import com.ohmz.tday.domain.validateOrFail
import com.ohmz.tday.domain.validatePatchTodo
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import com.ohmz.tday.shared.model.CreateTodoResponse
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val MSG = "message"

fun Route.todoRoutes() {
    val todoService by inject<TodoService>()
    val todoNlpService by inject<TodoNlpService>()
    val appConfigService by inject<AppConfigService>()
    val todoSummaryService by inject<TodoSummaryService>()

    route("/todo") {
        todoCrudRoutes(todoService)
        todoCompleteRoutes(todoService)
        todoInstanceRoutes(todoService)
        todoUtilityRoutes(todoService, todoNlpService, appConfigService, todoSummaryService)
    }
}

private fun Route.todoCrudRoutes(todoService: TodoService) {
    post {
        call.withAuth { user ->
            either {
                val body = call.receive<TodoCreateRequest>()
                validateCreateTodo.validateOrFail(body).bind()
                val due = parseTodoDateTime(body.due)
                    ?: raise(AppError.BadRequest("due must be a valid ISO-8601 datetime"))
                val todo = todoService.create(user.id, body.title, body.description, body.priority, due, body.rrule, body.listID).bind()
                CreateTodoResponse(message = "todo created", todo = todo)
            }
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
            either {
                val body = call.receive<TodoPatchRequest>()
                validatePatchTodo.validateOrFail(body).bind()
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                body.priority?.let { fields["priority"] = it }
                body.pinned?.let { fields["pinned"] = it }
                body.completed?.let { fields["completed"] = it }
                body.due?.let {
                    val parsed = parseTodoDateTime(it)
                        ?: raise(AppError.BadRequest("due must be a valid ISO-8601 datetime"))
                    fields["due"] = parsed
                }
                body.rrule?.let { fields["rrule"] = it }
                body.listID?.let { fields["listID"] = it }
                todoService.update(user.id, body.id, fields).bind()
                mapOf(MSG to "Todo updated")
            }
        }
    }

    delete {
        call.withAuth { user ->
            val body = call.receive<TodoDeleteRequest>()
            if (body.id.isBlank()) return@withAuth arrow.core.Either.Left(AppError.BadRequest("todo id is required"))
            todoService.delete(user.id, body.id)
                .map { count -> mapOf(MSG to if (count > 0) "todo deleted" else "todo already deleted") }
        }
    }
}

private fun Route.todoCompleteRoutes(todoService: TodoService) {
    route("/complete") {
        patch {
            call.withAuth { user ->
                val body = call.receive<TodoCompleteRequest>()
                val instanceDate = body.instanceDate?.let {
                    parseTodoDateTime(it)
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("instanceDate must be a valid ISO-8601 datetime"))
                }
                todoService.completeTodo(user.id, body.id, instanceDate)
                    .map { mapOf(MSG to "todo completed") }
            }
        }
    }

    route("/uncomplete") {
        patch {
            call.withAuth { user ->
                val body = call.receive<TodoCompleteRequest>()
                val instanceDate = body.instanceDate?.let {
                    parseTodoDateTime(it)
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("instanceDate must be a valid ISO-8601 datetime"))
                }
                todoService.uncompleteTodo(user.id, body.id, instanceDate)
                    .map { mapOf(MSG to "todo uncompleted") }
            }
        }
    }

    route("/prioritize") {
        patch {
            call.withAuth { user ->
                val body = call.receive<TodoPrioritizeRequest>()
                todoService.prioritize(user.id, body.id, body.priority)
                    .map { mapOf(MSG to "priority updated") }
            }
        }
    }

    route("/reorder") {
        patch {
            call.withAuth { user ->
                val body = call.receive<TodoReorderRequest>()
                todoService.reorder(user.id, body.id, body.order)
                    .map { mapOf(MSG to "order updated") }
            }
        }
    }
}

private fun Route.todoInstanceRoutes(todoService: TodoService) {
    route("/instance") {
        patch {
            call.withAuth { user ->
                val body = call.receive<TodoInstancePatchRequest>()
                val instanceDate = parseTodoDateTime(body.instanceDate)
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("instanceDate must be a valid ISO-8601 datetime"))
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                body.priority?.let { fields["priority"] = it }
                body.due?.let {
                    val parsed = parseTodoDateTime(it)
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("due must be a valid ISO-8601 datetime"))
                    fields["due"] = parsed
                }
                todoService.patchInstance(user.id, body.todoId, instanceDate, fields)
                    .map { mapOf(MSG to "instance updated") }
            }
        }

        delete {
            call.withAuth { user ->
                val body = call.receive<TodoInstanceDeleteRequest>()
                val instanceDate = parseTodoDateTime(body.instanceDate)
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("instanceDate must be a valid ISO-8601 datetime"))
                todoService.deleteInstance(user.id, body.todoId, instanceDate)
                    .map { mapOf(MSG to "instance deleted") }
            }
        }
    }
}

private fun Route.todoUtilityRoutes(
    todoService: TodoService,
    todoNlpService: TodoNlpService,
    appConfigService: AppConfigService,
    todoSummaryService: TodoSummaryService,
) {
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

internal fun parseTodoDateTime(value: String): LocalDateTime? {
    return runCatching { LocalDateTime.parse(value) }.getOrNull()
        ?: runCatching {
            OffsetDateTime.parse(value)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
        }.getOrNull()
}
