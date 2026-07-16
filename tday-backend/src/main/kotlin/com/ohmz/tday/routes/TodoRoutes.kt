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
import com.ohmz.tday.models.response.CompletedTodoResponse
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.services.CompletedTodoService
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import io.ktor.server.request.*
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject
import com.ohmz.tday.shared.model.BrainDumpCandidate
import com.ohmz.tday.shared.model.BrainDumpRequest
import com.ohmz.tday.shared.model.BrainDumpResponse
import com.ohmz.tday.shared.nlp.BrainDumpSplitter
import com.ohmz.tday.shared.nlp.RecurrencePriorityGrammar
import com.ohmz.tday.shared.model.CreateTodoResponse
import com.ohmz.tday.shared.model.DemoteTodoResponse
import com.ohmz.tday.shared.model.Priority
import com.ohmz.tday.shared.model.TodoSummaryResponse
import com.ohmz.tday.shared.summary.SummaryEngine
import com.ohmz.tday.shared.summary.SummaryTaskInput
import com.ohmz.tday.shared.summary.SummaryScope as SharedSummaryScope
import java.time.*
import java.util.Locale

private const val MSG = "message"
private const val TODOS = "todos"
private const val SOURCE_AI = "ai"
private const val SOURCE_LOGIC = "logic"
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
    val todoSummaryService by inject<TodoSummaryService>()
    val completedTodoService by inject<CompletedTodoService>()

    route("/todo") {
        todoCreateRoute(todoService)
        todoGetRoute(todoService)
        todoPatchRoute(todoService)
        todoDeleteRoute(todoService)
        todoCompleteRoutes(todoService)
        todoInstanceRoutes(todoService)
        todoDemoteRoute(todoService)
        todoUtilityRoutes(todoService, floaterService, todoNlpService, todoSummaryService, completedTodoService)
    }
}

/** Lets a stale todo float: the todo row is consumed and a floater takes its place. */
private fun Route.todoDemoteRoute(todoService: TodoService) {
    route("/{id}/demote") {
        post {
            call.withAuth { user ->
                either {
                    val todoId = call.parameters["id"]?.trim().orEmpty()
                    if (todoId.isBlank()) {
                        raise(AppError.BadRequest("todo id is required"))
                    }
                    val floater = todoService.demoteToFloater(user.id, todoId).bind()
                    DemoteTodoResponse(message = "todo demoted", floater = floater)
                }
            }
        }
    }
}

private fun Route.todoCreateRoute(todoService: TodoService) {
    post {
        call.withAuth { user ->
            either {
                val body = call.receive<TodoCreateRequest>()
                validateCreateTodo.validateOrFail(body).bind()
                val due = parseDueMinute(body.due)
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
                        val parsed = parseDueMinute(body.due)
                            ?: raise(AppError.BadRequest(ERR_INVALID_DUE))
                        fields["due"] = parsed
                    }
                } else if (!body.due.isNullOrBlank()) {
                    val parsed = parseDueMinute(body.due)
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
                    parseDueMinute(it)
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
                    parseDueMinute(it)
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
                val instanceDate = parseDueMinute(body.instanceDate)
                    ?: return@withAuth arrow.core.Either.Left(AppError.BadRequest(ERR_INVALID_INSTANCE_DATE))
                val fields = mutableMapOf<String, Any?>()
                body.title?.let { fields["title"] = it }
                body.description?.let { fields["description"] = it }
                when (val priority = validateOptionalEnumValue<Priority>(body.priority, "priority")) {
                    is arrow.core.Either.Left -> return@withAuth arrow.core.Either.Left(priority.value)
                    is arrow.core.Either.Right -> priority.value?.let { fields["priority"] = it }
                }
                body.due?.let {
                    val parsed = parseDueMinute(it)
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
                val instanceDate = parseDueMinute(body.instanceDate)
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
    todoSummaryService: TodoSummaryService,
    completedTodoService: CompletedTodoService,
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

                val locale = body.locale
                val nowMs = Instant.now().toEpochMilli()

                // Week in Review: a retrospective over the completed history, rendered by
                // the same shared engine (deterministic — no AI path for the recap).
                if (scope == SummaryScope.WEEK) {
                    val cleared = completedTodoService.getAll(user.id).getOrNull().orEmpty()
                        .mapNotNull { it.toWeekSummaryInput() }
                    return@withAuth TodoSummaryResponse(
                        summary = SummaryEngine.summarize(cleared, SharedSummaryScope.WEEK, nowMs, timeZone, locale),
                        source = SOURCE_LOGIC,
                        mode = scope.responseMode,
                        taskCount = cleared.size,
                        generatedAt = Instant.now().toString(),
                        fallbackReason = null,
                        reason = null,
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
                        // Empty list -> the shared engine returns the localized "clear for now" line.
                        summary = SummaryEngine.summarize(emptyList(), SharedSummaryScope.ALL, nowMs, timeZone, locale),
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
                // Deterministic fallback comes from the single shared engine. `tasks` is already
                // scoped, so pass SummaryScope.ALL to let the engine rank+render without re-filtering.
                val logicSummary = {
                    SummaryEngine.summarize(
                        tasks.map { it.toSummaryInput(zoneId) },
                        SharedSummaryScope.ALL,
                        nowMs,
                        timeZone,
                        locale,
                    )
                }
                TodoSummaryResponse(
                    summary = summaryText ?: logicSummary(),
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

    // Brain Dump: split a free-text blob into candidate tasks (dates via Natty, recurrence
    // and priority via the shared grammar). The model never invents timestamps — the
    // deterministic split + on-device grammar are the source of truth.
    route("/brain-dump") {
        post {
            call.withAuth { user ->
                val body = call.receive<BrainDumpRequest>()
                val timeZone = body.timeZone ?: user.timeZone ?: "UTC"
                val offsetMinutes = runCatching {
                    ZoneId.of(timeZone).rules.getOffset(Instant.now()).totalSeconds / 60
                }.getOrDefault(0)
                val candidates = BrainDumpSplitter.split(body.text).map { fragment ->
                    val dateParse = todoNlpService.parse(
                        text = fragment,
                        locale = body.locale,
                        referenceEpochMs = Instant.now().toEpochMilli(),
                        timezoneOffsetMinutes = offsetMinutes,
                    )
                    val grammar = RecurrencePriorityGrammar.parse(dateParse.cleanTitle)
                    BrainDumpCandidate(
                        title = grammar.cleanTitle.ifBlank { fragment },
                        dueEpochMs = dateParse.dueEpochMs,
                        rrule = grammar.rrule,
                        priority = grammar.priority,
                    )
                }
                BrainDumpResponse(candidates = candidates).right()
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
    FLOATER("floater", usesFloaters = true),
    WEEK("week");

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
                "week" -> WEEK
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
                // `due` is stored as a UTC wall-clock; convert it to the user's
                // zone so the TODAY / OVERDUE / SCHEDULED buckets (which use the
                // user-local now/today above) compare in the same frame.
                val dueUtc = parseTodoDateTime(todo.due) ?: return@mapNotNull null
                val due = dueUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId).toLocalDateTime()
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
                    // WEEK is handled from the completed history before buildSummaryTasks runs.
                    SummaryScope.WEEK -> false
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

/**
 * Maps an already-scoped backend [SummaryTask] into the shared engine's input. The
 * backend stores `due` as a zoned LocalDateTime; convert it back to an absolute
 * instant so the shared engine re-zones it identically to the native client.
 */
private fun SummaryTask.toSummaryInput(zoneId: ZoneId): SummaryTaskInput = SummaryTaskInput(
    title = title,
    priority = priority,
    dueEpochMs = due?.atZone(zoneId)?.toInstant()?.toEpochMilli(),
    pinned = pinned,
    recurring = recurring,
    listId = listId,
    completed = false,
    kind = kind,
)

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

/** Maps a completed todo into the shared engine's WEEK input (timestamps are UTC). */
private fun CompletedTodoResponse.toWeekSummaryInput(): SummaryTaskInput? {
    val completedMs = parseTodoDateTime(completedAt)
        ?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: return null
    return SummaryTaskInput(
        title = title,
        priority = priority,
        dueEpochMs = parseTodoDateTime(due)?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        completed = true,
        completedAtEpochMs = completedMs,
    )
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

/**
 * Parse a due / instance-date / occurrence timestamp and FLOOR it to the minute (drop
 * seconds & fractional seconds). Deadlines are minute-precision app-wide, so every due,
 * instanceDate and overriddenDue write goes through this instead of [parseTodoDateTime].
 * The plain parser is kept for audit timestamps (createdAt/updatedAt/completedAt on import)
 * that must retain sub-minute precision for last-writer-wins sync.
 */
internal fun parseDueMinute(value: String?): LocalDateTime? =
    parseTodoDateTime(value)?.withSecond(0)?.withNano(0)

/** Floor an already-parsed timestamp to the minute (equivalent of SQL date_trunc('minute')). */
internal fun LocalDateTime.flooredToMinute(): LocalDateTime = withSecond(0).withNano(0)
