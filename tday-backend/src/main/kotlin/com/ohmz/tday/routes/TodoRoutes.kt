package com.ohmz.tday.routes

import arrow.core.raise.either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.validateCreateTodo
import com.ohmz.tday.domain.validateOptionalEnumValue
import com.ohmz.tday.domain.validateOrFail
import com.ohmz.tday.domain.validatePatchTodo
import com.ohmz.tday.domain.validateRequiredEnumValue
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.models.request.*
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import com.ohmz.tday.shared.model.CreateTodoResponse
import com.ohmz.tday.shared.model.Priority
import com.ohmz.tday.shared.model.TodoSummaryResponse
import java.time.*
import java.util.Locale

private const val MSG = "message"
private const val TODOS = "todos"
private const val SOURCE_AI = "ai"
private const val SOURCE_LOGIC = "logic"
private const val REASON_DISABLED = "disabled"
private const val REASON_EMPTY = "empty"
private const val REASON_AI_UNAVAILABLE = "ai_unavailable"
private const val ERR_INVALID_DUE = "due must be a valid ISO-8601 datetime"
private const val ERR_DUE_REQUIRED = "due is required"
private const val ERR_INVALID_INSTANCE_DATE = "instanceDate must be a valid ISO-8601 datetime"
private const val MAX_SUMMARY_TASKS = 40
private const val MAX_SUMMARY_TITLE_LENGTH = 96

fun Route.todoRoutes() {
    val todoService by inject<TodoService>()
    val floaterService by inject<FloaterService>()
    val todoNlpService by inject<TodoNlpService>()
    val appConfigService by inject<AppConfigService>()
    val todoSummaryService by inject<TodoSummaryService>()

    route("/todo") {
        todoCreateRoute(todoService)
        todoGetRoute(todoService)
        todoPatchRoute(todoService)
        todoDeleteRoute(todoService)
        todoCompleteRoutes(todoService)
        todoInstanceRoutes(todoService)
        todoUtilityRoutes(todoService, floaterService, todoNlpService, appConfigService, todoSummaryService)
    }
}

private fun Route.todoCreateRoute(todoService: TodoService) {
    post {
        call.withAuth { user ->
            either {
                val body = call.receive<TodoCreateRequest>()
                validateCreateTodo.validateOrFail(body).bind()
                val due = parseTodoDateTime(body.due)
                if (body.due.isBlank()) {
                    raise(AppError.BadRequest(ERR_DUE_REQUIRED))
                }
                if (due == null) {
                    raise(AppError.BadRequest(ERR_INVALID_DUE))
                }
                val priority = validateRequiredEnumValue<Priority>(body.priority, "priority").bind()
                val rrule = body.rrule?.takeIf { it.isNotBlank() }
                val todo = todoService.create(user.id, body.title, body.description, priority, due, rrule, body.listID).bind()
                CreateTodoResponse(message = "todo created", todo = todo)
            }
        }
    }
}

private fun Route.todoGetRoute(todoService: TodoService) {
    get {
        call.withAuth { user ->
            val timeZone = user.timeZone ?: "UTC"
            val timeline = call.request.queryParameters["timeline"] == "true"

            if (timeline) {
                val days = call.request.queryParameters["recurringFutureDays"]?.toIntOrNull() ?: 365
                todoService.getTimeline(user.id, timeZone, days.coerceIn(1, 3650))
                    .map { mapOf(TODOS to it) }
            } else {
                val start = call.request.queryParameters["start"]?.toLongOrNull()
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("date range start not specified"))
                val end = call.request.queryParameters["end"]?.toLongOrNull()
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("date range end not specified"))
                todoService.getByDateRange(user.id, start, end, timeZone)
                    .map { mapOf(TODOS to it) }
            }
        }
    }
}

private fun Route.todoPatchRoute(todoService: TodoService) {
    patch {
        call.withAuth { user ->
            either {
                val body = call.receive<TodoPatchRequest>()
                validatePatchTodo.validateOrFail(body).bind()
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                validateOptionalEnumValue<Priority>(body.priority, "priority").bind()?.let { fields["priority"] = it }
                body.pinned?.let { fields["pinned"] = it }
                body.completed?.let { fields["completed"] = it }
                val requestedRrule = body.rrule?.takeIf { it.isNotBlank() }
                if (body.dateChanged == true) {
                    if (body.due.isNullOrBlank()) {
                        raise(AppError.BadRequest(ERR_DUE_REQUIRED))
                    } else {
                        val parsed = parseTodoDateTime(body.due)
                            ?: raise(AppError.BadRequest(ERR_INVALID_DUE))
                        fields["due"] = parsed
                    }
                } else if (!body.due.isNullOrBlank()) {
                    val parsed = parseTodoDateTime(body.due)
                        ?: raise(AppError.BadRequest(ERR_INVALID_DUE))
                    fields["due"] = parsed
                }
                if (body.rruleChanged == true || body.rrule != null) {
                    fields["rrule"] = requestedRrule
                }
                body.listID?.let { fields["listID"] = it.takeIf { value -> value.isNotBlank() } }
                todoService.update(user.id, body.id, fields).bind()
                mapOf(MSG to "Todo updated")
            }
        }
    }
}

private fun Route.todoDeleteRoute(todoService: TodoService) {
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
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest(ERR_INVALID_INSTANCE_DATE))
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
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest(ERR_INVALID_INSTANCE_DATE))
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
                    either {
                        val priority = validateRequiredEnumValue<Priority>(body.priority, "priority").bind()
                        todoService.prioritize(user.id, body.id, priority).bind()
                        mapOf(MSG to "priority updated")
                    }
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
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest(ERR_INVALID_INSTANCE_DATE))
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                when (val priority = validateOptionalEnumValue<Priority>(body.priority, "priority")) {
                    is arrow.core.Either.Left -> return@withAuth arrow.core.Either.Left(priority.value)
                    is arrow.core.Either.Right -> priority.value?.let { fields["priority"] = it }
                }
                body.due?.let {
                    val parsed = parseTodoDateTime(it)
                        ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest(ERR_INVALID_DUE))
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
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest(ERR_INVALID_INSTANCE_DATE))
                todoService.deleteInstance(user.id, body.todoId, instanceDate)
                    .map { mapOf(MSG to "instance deleted") }
            }
        }
    }
}

private fun Route.todoUtilityRoutes(
    todoService: TodoService,
    floaterService: FloaterService,
    todoNlpService: TodoNlpService,
    appConfigService: AppConfigService,
    todoSummaryService: TodoSummaryService,
) {
    route("/overdue") {
        get {
            call.withAuth { user ->
                val timeZone = user.timeZone ?: "UTC"
                todoService.getOverdue(user.id, timeZone)
                    .map { mapOf(TODOS to it) }
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
                val zoneId = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneOffset.UTC)
                val scope = SummaryScope.from(body.mode)
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest("summary mode is invalid", "mode"))

                val config = appConfigService.getGlobalConfig().getOrNull()
                if (config != null && !config.aiSummaryEnabled) {
                    return@withAuth TodoSummaryResponse(
                        summary = null,
                        source = SOURCE_LOGIC,
                        mode = scope.responseMode,
                        taskCount = 0,
                        fallbackReason = REASON_DISABLED,
                        reason = REASON_DISABLED,
                    ).right()
                }

                val todos = todoService.getTimeline(user.id, timeZone, 365).getOrNull() ?: emptyList()
                val floaters = if (scope.usesFloaters) {
                    floaterService.getAll(user.id).getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }
                val tasks = buildSummaryTasks(
                    scope = scope,
                    listId = body.listId,
                    todos = todos,
                    floaters = floaters,
                    zoneId = zoneId,
                )
                if (tasks.isEmpty()) {
                    return@withAuth TodoSummaryResponse(
                        summary = "You're clear for now. No tasks need attention in this view.",
                        source = SOURCE_LOGIC,
                        mode = scope.responseMode,
                        taskCount = 0,
                        generatedAt = Instant.now().toString(),
                        fallbackReason = REASON_EMPTY,
                        reason = REASON_EMPTY,
                    ).right()
                }

                val prompt = buildSummaryPrompt(scope, tasks, zoneId)
                val summaryText = todoSummaryService.generateSummary(prompt)
                val usedAi = !summaryText.isNullOrBlank()
                TodoSummaryResponse(
                    summary = summaryText ?: buildLogicSummary(scope, tasks, zoneId),
                    source = if (usedAi) SOURCE_AI else SOURCE_LOGIC,
                    mode = scope.responseMode,
                    taskCount = tasks.size,
                    generatedAt = Instant.now().toString(),
                    fallbackReason = if (usedAi) null else REASON_AI_UNAVAILABLE,
                    reason = if (usedAi) null else REASON_AI_UNAVAILABLE,
                ).right()
            }
        }
    }
}

private enum class SummaryScope(
    val responseMode: String,
    val usesFloaters: Boolean = false,
) {
    TODAY("today"),
    OVERDUE("overdue"),
    SCHEDULED("scheduled"),
    ALL("all"),
    PRIORITY("priority"),
    LIST("list"),
    FLOATER("floater", usesFloaters = true);

    companion object {
        fun from(value: String?): SummaryScope? {
            return when (value?.trim()?.lowercase(Locale.ROOT)) {
                null, "", "today" -> TODAY
                "overdue" -> OVERDUE
                "scheduled" -> SCHEDULED
                "all" -> ALL
                "priority" -> PRIORITY
                "list" -> LIST
                "floater", "anytime" -> FLOATER
                else -> null
            }
        }
    }
}

private data class SummaryTask(
    val title: String,
    val priority: String,
    val due: LocalDateTime?,
    val pinned: Boolean,
    val recurring: Boolean,
    val listId: String?,
    val kind: String,
)

private fun buildSummaryTasks(
    scope: SummaryScope,
    listId: String?,
    todos: List<TodoResponse>,
    floaters: List<FloaterResponse>,
    zoneId: ZoneId,
): List<SummaryTask> {
    val normalizedListId = listId?.trim()?.takeIf { it.isNotEmpty() }
    val now = LocalDateTime.now(zoneId)
    val today = LocalDate.now(zoneId)

    return when (scope) {
        SummaryScope.FLOATER -> floaters
            .asSequence()
            .filterNot { it.completed }
            .filter { normalizedListId == null || it.listID == normalizedListId }
            .map {
                SummaryTask(
                    title = it.title,
                    priority = it.priority,
                    due = null,
                    pinned = it.pinned,
                    recurring = false,
                    listId = it.listID,
                    kind = "anytime",
                )
            }
            .toList()

        else -> todos
            .asSequence()
            .filterNot { it.completed }
            .mapNotNull { todo ->
                val due = parseTodoDateTime(todo.due) ?: return@mapNotNull null
                SummaryTask(
                    title = todo.title,
                    priority = todo.priority,
                    due = due,
                    pinned = todo.pinned,
                    recurring = !todo.rrule.isNullOrBlank(),
                    listId = todo.listID,
                    kind = "scheduled",
                )
            }
            .filter { task ->
                when (scope) {
                    SummaryScope.TODAY -> task.due?.toLocalDate() == today
                    SummaryScope.OVERDUE -> task.due?.isBefore(now) == true
                    SummaryScope.SCHEDULED -> task.due?.isBefore(now) == false
                    SummaryScope.ALL -> true
                    SummaryScope.PRIORITY -> isPrioritySummaryTask(task.priority)
                    SummaryScope.LIST -> normalizedListId != null && task.listId == normalizedListId
                    SummaryScope.FLOATER -> false
                }
            }
            .sortedWith(
                compareByDescending<SummaryTask> { it.pinned }
                    .thenByDescending { priorityWeight(it.priority) }
                    .thenBy { it.due ?: LocalDateTime.MAX },
            )
            .toList()
    }
}

private fun buildSummaryPrompt(
    scope: SummaryScope,
    tasks: List<SummaryTask>,
    zoneId: ZoneId,
): String {
    val now = LocalDateTime.now(zoneId)
    val taskLines = tasks.take(MAX_SUMMARY_TASKS).joinToString("\n") { task ->
        val dueText = task.due?.let { due ->
            when {
                due.isBefore(now) -> "overdue"
                due.toLocalDate() == now.toLocalDate() -> "due today"
                else -> "due ${due.toLocalDate()}"
            }
        } ?: "anytime"
        val markers = listOfNotNull(
            task.priority.takeIf { it.isNotBlank() },
            dueText,
            "pinned".takeIf { task.pinned },
            "recurring".takeIf { task.recurring },
        ).joinToString(", ")
        "- ${task.kind}; $markers; ${boundedSummaryTitle(task.title)}"
    }

    return """
        Summarize this ${scope.responseMode} task view for a personal task planner.
        Return 1-2 short, useful sentences. Mention urgency, priority, and where to start when helpful.
        Do not use markdown. Do not invent tasks.
        Task count: ${tasks.size}
        Tasks:
        $taskLines
    """.trimIndent()
}

private fun buildLogicSummary(
    scope: SummaryScope,
    tasks: List<SummaryTask>,
    zoneId: ZoneId,
): String {
    val now = LocalDateTime.now(zoneId)
    val highPriority = tasks.count { priorityWeight(it.priority) >= priorityWeight("High") }
    val overdue = tasks.count { it.due?.isBefore(now) == true }
    val dueToday = tasks.count { it.due?.toLocalDate() == now.toLocalDate() }
    val recurring = tasks.count { it.recurring }
    val pinned = tasks.count { it.pinned }
    val firstTask = tasks.firstOrNull()?.title?.let(::boundedSummaryTitle)

    val opening = when (scope) {
        SummaryScope.FLOATER -> "You have ${tasks.size} anytime ${pluralize("task", tasks.size)}."
        SummaryScope.LIST -> "This list has ${tasks.size} active ${pluralize("task", tasks.size)}."
        else -> "This view has ${tasks.size} active ${pluralize("task", tasks.size)}."
    }

    val details = mutableListOf<String>()
    if (overdue > 0) details += "$overdue overdue"
    if (dueToday > 0) details += "$dueToday due today"
    if (highPriority > 0) details += "$highPriority high priority"
    if (pinned > 0) details += "$pinned pinned"
    if (recurring > 0) details += "$recurring recurring"

    val focus = firstTask?.let { " Start with \"$it\"." }.orEmpty()
    return if (details.isEmpty()) {
        "$opening Nothing looks urgent.$focus"
    } else {
        "$opening Key focus: ${details.joinToString(", ")}.$focus"
    }
}

private fun isPrioritySummaryTask(priority: String?): Boolean {
    return priorityWeight(priority) >= priorityWeight("Medium")
}

private fun priorityWeight(priority: String?): Int {
    return when (priority?.trim()?.lowercase(Locale.ROOT)) {
        "urgent", "important", "high" -> 3
        "medium" -> 2
        "low" -> 1
        else -> 0
    }
}

private fun boundedSummaryTitle(title: String): String {
    val normalized = title.trim().replace(Regex("\\s+"), " ")
    return if (normalized.length <= MAX_SUMMARY_TITLE_LENGTH) {
        normalized
    } else {
        normalized.take(MAX_SUMMARY_TITLE_LENGTH - 3).trimEnd() + "..."
    }
}

private fun pluralize(word: String, count: Int): String {
    return if (count == 1) word else "${word}s"
}

internal fun parseTodoDateTime(value: String?): LocalDateTime? {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDateTime.parse(normalized) }.getOrNull()
        ?: runCatching {
            OffsetDateTime.parse(normalized)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .toLocalDateTime()
        }.getOrNull()
}
