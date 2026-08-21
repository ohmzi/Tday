package com.ohmz.tday.mcp

import arrow.core.Either
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.mcp.McpDates.toZone
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.services.CompletedFloaterService
import com.ohmz.tday.services.CompletedTodoService
import com.ohmz.tday.services.FloaterListService
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.services.IntegrationContextService
import com.ohmz.tday.services.ListService
import com.ohmz.tday.services.RecurrenceExpander
import com.ohmz.tday.services.RecurrenceState
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.shared.model.IntegrationApiKeyDto
import com.ohmz.tday.shared.model.ListColor
import java.time.LocalDateTime
import java.time.ZoneId

/** Who the current MCP call acts as, and what it is allowed to do. */
data class McpCallContext(
    val userId: String,
    val timeZone: String?,
    val apiKey: IntegrationApiKeyDto?,
    val canWrite: Boolean,
) {
    val zone: ZoneId get() = McpDates.zoneOf(timeZone)
}

/**
 * The T'Day side of the MCP tools.
 *
 * Calls the domain services directly rather than looping back through HTTP, so tenant
 * isolation, share visibility and validation stay owned by the same services the REST
 * routes use — there is no second copy of those rules here. What *is* here is the
 * translation the model needs: date-or-no-date entity routing, list-name resolution,
 * and prose that states what actually happened.
 */
class TdayMcpService(
    private val integrationContextService: IntegrationContextService,
    private val todoService: TodoService,
    private val floaterService: FloaterService,
    private val listService: ListService,
    private val floaterListService: FloaterListService,
    private val completedTodoService: CompletedTodoService,
    private val completedFloaterService: CompletedFloaterService,
    private val recurrenceExpander: RecurrenceExpander,
) {

    // ---------------------------------------------------------------- read tools

    suspend fun getContext(ctx: McpCallContext): McpToolResult {
        val context = integrationContextService
            .contextFor(ctx.userId, ctx.timeZone, ctx.apiKey)
            .orFailure { return it }

        val zone = ctx.zone
        val text = buildString {
            appendLine("Account: ${context.user.name ?: context.user.username ?: context.user.id}" +
                (context.user.username?.let { " (@$it)" } ?: ""))
            appendLine("Timezone: ${context.user.timeZone ?: "UTC"}")
            appendLine("Local time now: ${McpDates.display(context.serverTime, zone)}")
            appendLine(
                if (context.capabilities.canWrite) {
                    "Access: full — this connection can create and change tasks."
                } else {
                    "Access: read-only — this connection can read tasks but not change them."
                },
            )
            appendLine()
            appendLine("Scheduled lists (for tasks with a date):")
            appendLine(describeLists(context.lists.map { it.name }))
            appendLine()
            appendLine("Anytime lists (for tasks without a date):")
            append(describeLists(context.anytimeLists.map { it.name }))
        }
        return McpToolResult.ok(text)
    }

    suspend fun findList(ctx: McpCallContext, name: String, kind: String?): McpToolResult {
        val namespace = when {
            kind == null || kind.equals("any", ignoreCase = true) -> null
            else -> parseNamespace(kind)
                ?: return McpToolResult.failed("kind must be 'scheduled', 'anytime' or 'any'.")
        }
        val candidates = allLists(ctx.userId).orFailure { return it }
        val lookup = McpListResolver.lookup(name, candidates, namespace)

        if (lookup.found) {
            val match = lookup.match!!
            return McpToolResult.ok(
                "Found \"${match.name}\" — ${match.namespace.indefinite} list (holds ${match.namespace.holds}). " +
                    "listId: ${match.id}",
            )
        }
        return McpToolResult.ok(describeMiss(lookup, candidates))
    }

    suspend fun listTasks(
        ctx: McpCallContext,
        view: String,
        from: String?,
        to: String?,
        listName: String?,
        includeCompleted: Boolean,
        limit: Int,
    ): McpToolResult {
        val zone = ctx.zone
        val resolvedView = TaskView.parse(view) ?: return McpToolResult.failed(
            "view must be one of: ${TaskView.entries.joinToString(", ") { it.value }}.",
        )

        val listFilter = listName?.let { requested ->
            val candidates = allLists(ctx.userId).orFailure { return it }
            val lookup = McpListResolver.lookup(requested, candidates, namespace = null)
            lookup.match ?: return McpToolResult.ok(describeMiss(lookup, candidates))
        }

        val tasks = mutableListOf<ResolvedTask>()

        if (resolvedView.includesScheduled) {
            val window = resolvedView.windowFor(zone, from, to)
                ?: return McpToolResult.failed("Could not read the from/to window. Use YYYY-MM-DD or YYYY-MM-DDTHH:mm.")
            tasks += scheduledTasks(ctx, window, includeCompleted).orFailure { return it }
        }
        if (resolvedView.includesAnytime) {
            tasks += anytimeTasks(ctx, includeCompleted).orFailure { return it }
        }

        val listNames = listNamesById(ctx.userId).orFailure { return it }
        val filtered = tasks
            .filter { listFilter == null || it.listId == listFilter.id }
            .sortedWith(compareBy({ it.due ?: LocalDateTime.MAX }, { it.title }))
            .take(limit.coerceIn(1, MAX_LIMIT))

        if (filtered.isEmpty()) {
            return McpToolResult.ok("No ${resolvedView.describe(listFilter?.name)} found.")
        }
        return McpToolResult.ok(
            buildString {
                appendLine("${filtered.size} ${resolvedView.describe(listFilter?.name)}:")
                filtered.forEach { appendLine(it.render(zone, listNames)) }
            }.trimEnd(),
        )
    }

    suspend fun searchTasks(
        ctx: McpCallContext,
        query: String,
        includeCompleted: Boolean,
        limit: Int,
    ): McpToolResult {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return McpToolResult.failed("query must contain something to search for.")

        val zone = ctx.zone
        val matches = mutableListOf<ResolvedTask>()
        matches += todoService.getTimeline(ctx.userId, zone.id, DEFAULT_UPCOMING_DAYS)
            .orFailure { return it }
            .map { it.toResolvedTask() }
        matches += floaterService.getAll(ctx.userId).orFailure { return it }.map { it.toResolvedTask() }

        if (includeCompleted) {
            matches += completedTodoService.getAll(ctx.userId).orFailure { return it }.map {
                ResolvedTask(
                    handle = TaskHandle.of(TaskKind.TODO, it.originalTodoID ?: it.id),
                    title = it.title,
                    notes = it.description,
                    due = McpDates.parseWireFormat(it.due),
                    priority = it.priority,
                    completed = true,
                    listId = it.listID,
                )
            }
            matches += completedFloaterService.getAll(ctx.userId).orFailure { return it }.map {
                ResolvedTask(
                    handle = TaskHandle.of(TaskKind.FLOATER, it.originalFloaterID ?: it.id),
                    title = it.title,
                    notes = it.description,
                    due = null,
                    priority = it.priority,
                    completed = true,
                    listId = it.listID,
                )
            }
        } else {
            matches.removeAll { it.completed }
        }

        val listNames = listNamesById(ctx.userId).orFailure { return it }
        val hits = matches
            .filter { task ->
                val haystack = "${task.title} ${task.notes.orEmpty()}".lowercase()
                terms.all { haystack.contains(it) }
            }
            .distinctBy { it.handle.toString() }
            .take(limit.coerceIn(1, MAX_LIMIT))

        if (hits.isEmpty()) return McpToolResult.ok("No tasks match \"$query\".")
        return McpToolResult.ok(
            buildString {
                appendLine("${hits.size} task(s) matching \"$query\":")
                hits.forEach { appendLine(it.render(zone, listNames)) }
            }.trimEnd(),
        )
    }

    // --------------------------------------------------------------- write tools

    suspend fun createTask(ctx: McpCallContext, args: CreateTaskArgs): McpToolResult {
        val zone = ctx.zone
        val due = args.due?.let {
            McpDates.parseDue(it, zone) ?: return McpToolResult.failed(
                "Could not read due \"$it\". Use YYYY-MM-DDTHH:mm, YYYY-MM-DD, or a full ISO-8601 instant.",
            )
        }
        if (args.recurrence != null && due == null) {
            return McpToolResult.failed(
                "A repeating task needs a due date — recurrence describes when the repeats fall. " +
                    "Ask the user when the first one is due, or create it as an Anytime task without recurrence.",
            )
        }

        // The presence of a date is what picks the entity, and with it the list namespace.
        val namespace = if (due == null) ListNamespace.ANYTIME else ListNamespace.SCHEDULED
        val list = resolveTargetList(ctx, args.listId, args.listName, namespace, args.createListIfMissing)
            .orReturn { return it }

        return if (due == null) {
            val floater = floaterService
                .create(ctx.userId, args.title, args.notes, args.priority, list?.id)
                .orFailure { return it }
            McpToolResult.ok(
                "Created Anytime task \"${floater.title}\"${inList(list)}. " +
                    "It has no date, so it lives in the Anytime feed rather than Today. " +
                    "Handle: ${TaskHandle.of(TaskKind.FLOATER, floater.id)}",
            )
        } else {
            val todo = todoService
                .create(ctx.userId, args.title, args.notes, args.priority, due, args.recurrence, list?.id)
                .orFailure { return it }
            McpToolResult.ok(
                buildString {
                    append("Created scheduled task \"${todo.title}\", due ${McpDates.display(todo.due, zone)}")
                    append(inList(list))
                    append(".")
                    if (args.dueWasDateOnly) {
                        append(" No time of day was given, so it is due by end of that day.")
                    }
                    args.recurrence?.let { append(" Repeats: $it.") }
                    append(" Handle: ${TaskHandle.of(TaskKind.TODO, todo.id)}")
                },
            )
        }
    }

    suspend fun updateTask(ctx: McpCallContext, args: UpdateTaskArgs): McpToolResult {
        val zone = ctx.zone
        val handle = TaskHandle.parse(args.taskId) ?: return badHandle(args.taskId)
        val due = args.due?.let {
            McpDates.parseDue(it, zone) ?: return McpToolResult.failed(
                "Could not read due \"$it\". Use YYYY-MM-DDTHH:mm, YYYY-MM-DD, or a full ISO-8601 instant.",
            )
        }
        if (args.clearDue && due != null) {
            return McpToolResult.failed("Pass either due or clearDue, not both.")
        }

        // Editing one occurrence of a series never touches the series itself.
        if (args.occurrenceDate != null) {
            return updateOccurrence(ctx, handle, args, due)
        }

        return when (handle.kind) {
            TaskKind.FLOATER -> updateFloater(ctx, handle, args, due)
            TaskKind.TODO -> updateTodo(ctx, handle, args, due)
        }
    }

    suspend fun completeTask(
        ctx: McpCallContext,
        taskId: String,
        completed: Boolean,
        occurrenceDate: String?,
    ): McpToolResult {
        val handle = TaskHandle.parse(taskId) ?: return badHandle(taskId)
        val instant = occurrenceDate?.let {
            McpDates.parseDue(it, ctx.zone) ?: return badOccurrence(it)
        }

        return when (handle.kind) {
            TaskKind.TODO -> {
                if (completed) {
                    todoService.completeTodo(ctx.userId, handle.id, instant).orFailure { return it }
                } else {
                    todoService.uncompleteTodo(ctx.userId, handle.id, instant).orFailure { return it }
                }
                val what = if (instant != null) "the ${McpDates.display(instant.toString(), ctx.zone)} occurrence" else "the task"
                McpToolResult.ok(if (completed) "Completed $what." else "Reopened $what.")
            }

            TaskKind.FLOATER -> {
                if (instant != null) {
                    return McpToolResult.failed(
                        "Anytime tasks don't repeat, so there are no occurrences to complete individually. " +
                            "Call again without occurrenceDate.",
                    )
                }
                if (completed) {
                    floaterService.completeFloater(ctx.userId, handle.id).orFailure { return it }
                } else {
                    floaterService.uncompleteFloater(ctx.userId, handle.id).orFailure { return it }
                }
                McpToolResult.ok(if (completed) "Completed the Anytime task." else "Reopened the Anytime task.")
            }
        }
    }

    suspend fun deleteTask(ctx: McpCallContext, taskId: String, occurrenceDate: String?): McpToolResult {
        val handle = TaskHandle.parse(taskId) ?: return badHandle(taskId)

        if (occurrenceDate != null) {
            if (handle.kind != TaskKind.TODO) {
                return McpToolResult.failed(
                    "Anytime tasks don't repeat, so there is no single occurrence to cancel. " +
                        "Call again without occurrenceDate to delete the task.",
                )
            }
            val instant = McpDates.parseDue(occurrenceDate, ctx.zone) ?: return badOccurrence(occurrenceDate)
            todoService.deleteInstance(ctx.userId, handle.id, instant).orFailure { return it }
            return McpToolResult.ok(
                "Cancelled the ${McpDates.display(instant.toString(), ctx.zone)} occurrence. The rest of the series is unchanged.",
            )
        }

        val deleted = when (handle.kind) {
            TaskKind.TODO -> todoService.delete(ctx.userId, handle.id).orFailure { return it }
            TaskKind.FLOATER -> floaterService.delete(ctx.userId, handle.id).orFailure { return it }
        }
        return if (deleted > 0) {
            McpToolResult.ok("Deleted the ${handle.kind.label}.")
        } else {
            McpToolResult.failed("No ${handle.kind.label} with id ${handle.id} — it may already be deleted.")
        }
    }

    suspend fun createList(
        ctx: McpCallContext,
        name: String,
        kind: String,
        color: String?,
        reusable: Boolean,
    ): McpToolResult {
        val namespace = parseNamespace(kind)
            ?: return McpToolResult.failed("kind must be 'scheduled' (dated tasks) or 'anytime' (undated tasks).")

        val listColor = color?.trim()?.uppercase()
        if (listColor != null && ListColor.entries.none { it.name == listColor }) {
            return McpToolResult.failed(
                "\"$color\" is not a T'Day list colour. Use one of: ${ListColor.entries.joinToString(", ") { it.name }}.",
            )
        }

        val existing = McpListResolver.lookup(name, allLists(ctx.userId).orFailure { return it }, namespace)
        if (existing.found) {
            val match = existing.match!!
            return McpToolResult.ok(
                "There is already ${namespace.indefinite} list called \"${match.name}\" — nothing was created. listId: ${match.id}",
            )
        }

        return when (namespace) {
            ListNamespace.SCHEDULED -> {
                val list = listService.create(ctx.userId, name, listColor, null).orFailure { return it }
                McpToolResult.ok("Created scheduled list \"${list.name}\" (holds dated tasks). listId: ${list.id}")
            }

            ListNamespace.ANYTIME -> {
                val list = floaterListService.create(ctx.userId, name, listColor, null, reusable).orFailure { return it }
                McpToolResult.ok("Created Anytime list \"${list.name}\" (holds undated tasks). listId: ${list.id}")
            }
        }
    }

    // -------------------------------------------------------------- update paths

    private suspend fun updateFloater(
        ctx: McpCallContext,
        handle: TaskHandle,
        args: UpdateTaskArgs,
        due: LocalDateTime?,
    ): McpToolResult {
        if (args.clearDue) {
            return McpToolResult.ok("That is already an Anytime task — it has no date to clear.")
        }

        // Giving an Anytime task a date makes it a scheduled task: the floater row is
        // consumed and a todo takes its place. List membership deliberately does not
        // carry across, because the two list namespaces hold different entities.
        if (due != null) {
            val leavingList = args.listId == null && args.listName == null &&
                floaterService.getAll(ctx.userId).orFailure { return it }
                    .firstOrNull { it.id == handle.id }?.listID != null

            val todo = floaterService
                .promoteToTodo(ctx.userId, handle.id, due, args.recurrence)
                .orFailure { return it }

            val list = resolveTargetList(
                ctx, args.listId, args.listName, ListNamespace.SCHEDULED, args.createListIfMissing,
            ).orReturn { return it }

            val fields = todoFields(args, list)
            if (fields.isNotEmpty()) {
                todoService.update(ctx.userId, todo.id, fields).orFailure { return it }
            }

            return McpToolResult.ok(
                buildString {
                    append("\"${todo.title}\" is now a scheduled task, due ${McpDates.display(todo.due, ctx.zone)}")
                    append(inList(list))
                    append(". Handle is now ${TaskHandle.of(TaskKind.TODO, todo.id)}.")
                    if (leavingList) {
                        append(
                            " It left its Anytime list behind — Anytime lists can't hold dated tasks — " +
                                "so tell the user and offer to file it in a scheduled list.",
                        )
                    }
                },
            )
        }

        if (args.recurrence != null || args.clearRecurrence) {
            return McpToolResult.failed(
                "Anytime tasks can't repeat. Give the task a due date first, then set its recurrence.",
            )
        }

        val list = resolveTargetList(ctx, args.listId, args.listName, ListNamespace.ANYTIME, args.createListIfMissing)
            .orReturn { return it }

        val fields = mutableMapOf<String, Any?>()
        args.title?.let { fields["title"] = it }
        args.notes?.let { fields["description"] = it }
        args.priority?.let { fields["priority"] = it }
        args.pinned?.let { fields["pinned"] = it }
        if (list != null) fields["listID"] = list.id

        if (fields.isEmpty()) return McpToolResult.failed("Nothing to change — supply at least one field to update.")

        floaterService.update(ctx.userId, handle.id, fields).orFailure { return it }
        return McpToolResult.ok("Updated the Anytime task${inList(list)}.")
    }

    private suspend fun updateTodo(
        ctx: McpCallContext,
        handle: TaskHandle,
        args: UpdateTaskArgs,
        due: LocalDateTime?,
    ): McpToolResult {
        // Clearing the date turns a scheduled task back into an Anytime one. The
        // service rejects this for a repeating task, whose series would be destroyed.
        if (args.clearDue) {
            val floater = todoService.demoteToFloater(ctx.userId, handle.id).orFailure {
                return McpToolResult.failed(
                    "${it.text} A repeating task cannot become an Anytime task — its series would be lost. " +
                        "Stop the repeat first with clearRecurrence, or delete it instead.",
                )
            }
            return McpToolResult.ok(
                "\"${floater.title}\" is now an Anytime task with no date. " +
                    "Handle is now ${TaskHandle.of(TaskKind.FLOATER, floater.id)}.",
            )
        }

        val list = resolveTargetList(ctx, args.listId, args.listName, ListNamespace.SCHEDULED, args.createListIfMissing)
            .orReturn { return it }

        val fields = todoFields(args, list)
        due?.let { fields["due"] = it }
        if (args.recurrence != null) fields["rrule"] = args.recurrence

        if (fields.isEmpty()) return McpToolResult.failed("Nothing to change — supply at least one field to update.")

        todoService.update(ctx.userId, handle.id, fields).orFailure { return it }
        return McpToolResult.ok(
            buildString {
                append("Updated the scheduled task")
                due?.let { append(" — now due ${McpDates.display(it.toString(), ctx.zone)}") }
                append(inList(list))
                if (args.clearRecurrence) append(". It no longer repeats")
                append(".")
            },
        )
    }

    private suspend fun updateOccurrence(
        ctx: McpCallContext,
        handle: TaskHandle,
        args: UpdateTaskArgs,
        due: LocalDateTime?,
    ): McpToolResult {
        if (handle.kind != TaskKind.TODO) {
            return McpToolResult.failed(
                "Anytime tasks don't repeat, so there are no occurrences to edit. Call again without occurrenceDate.",
            )
        }
        val instant = McpDates.parseDue(args.occurrenceDate!!, ctx.zone) ?: return badOccurrence(args.occurrenceDate)

        val fields = mutableMapOf<String, Any?>()
        args.title?.let { fields["title"] = it }
        args.notes?.let { fields["description"] = it }
        args.priority?.let { fields["priority"] = it }
        due?.let { fields["due"] = it }
        if (fields.isEmpty()) {
            return McpToolResult.failed(
                "Nothing to change for that occurrence — supply title, notes, priority or due.",
            )
        }

        todoService.patchInstance(ctx.userId, handle.id, instant, fields).orFailure { return it }
        return McpToolResult.ok(
            "Updated only the ${McpDates.display(instant.toString(), ctx.zone)} occurrence. " +
                "The rest of the series is unchanged.",
        )
    }

    /** The non-date `todos` columns an update touches, ready for TodoService.update. */
    private fun todoFields(args: UpdateTaskArgs, list: NamedList?): MutableMap<String, Any?> {
        val fields = mutableMapOf<String, Any?>()
        args.title?.let { fields["title"] = it }
        args.notes?.let { fields["description"] = it }
        args.priority?.let { fields["priority"] = it }
        args.pinned?.let { fields["pinned"] = it }
        if (args.clearRecurrence) fields["rrule"] = null
        if (list != null) fields["listID"] = list.id
        return fields
    }

    // ------------------------------------------------------------------ reading

    private suspend fun scheduledTasks(
        ctx: McpCallContext,
        window: Window,
        includeCompleted: Boolean,
    ): Either<AppError, List<ResolvedTask>> {
        val zone = ctx.zone
        val timeline = when (val result = todoService.getTimeline(ctx.userId, zone.id, DEFAULT_UPCOMING_DAYS)) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }

        val (recurring, oneOff) = timeline.partition { !it.rrule.isNullOrBlank() }
        val states = when (val result = todoService.getRecurrenceStates(ctx.userId, recurring.map { it.id })) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }

        // One-off todos can be filtered over an unbounded window cheaply, but expanding a
        // series over one would materialise decades of occurrences. Clamp expansion to a
        // window a person could plausibly be asking about.
        val now = McpDates.nowUtc()
        val expansionFrom = maxOf(window.from, now.minusDays(EXPANSION_LOOKBACK_DAYS))
        val expansionTo = minOf(window.to, now.plusDays(EXPANSION_LOOKAHEAD_DAYS))
        val expanded = if (expansionFrom.isAfter(expansionTo)) {
            emptyList()
        } else {
            recurring.flatMap { template ->
                recurrenceExpander.expand(
                    todo = template,
                    state = states[template.id] ?: RecurrenceState(),
                    zone = zone,
                    from = expansionFrom,
                    to = expansionTo,
                )
            }
        }

        val dated = oneOff.filter { todo ->
            val due = McpDates.parseWireFormat(todo.due) ?: return@filter false
            !due.isBefore(window.from) && !due.isAfter(window.to)
        }

        return Either.Right(
            (dated + expanded)
                .filter { includeCompleted || !it.completed }
                .map { it.toResolvedTask() },
        )
    }

    private suspend fun anytimeTasks(
        ctx: McpCallContext,
        includeCompleted: Boolean,
    ): Either<AppError, List<ResolvedTask>> =
        when (val result = floaterService.getAll(ctx.userId)) {
            is Either.Left -> result
            is Either.Right -> Either.Right(
                result.value.filter { includeCompleted || !it.completed }.map { it.toResolvedTask() },
            )
        }

    // ------------------------------------------------------------------ helpers

    private suspend fun allLists(userId: String): Either<AppError, List<NamedList>> {
        val scheduled = when (val result = listService.getAll(userId)) {
            is Either.Left -> return result
            is Either.Right -> result.value.map { NamedList(it.id, it.name, ListNamespace.SCHEDULED) }
        }
        val anytime = when (val result = floaterListService.getAll(userId)) {
            is Either.Left -> return result
            is Either.Right -> result.value.map { NamedList(it.id, it.name, ListNamespace.ANYTIME) }
        }
        return Either.Right(scheduled + anytime)
    }

    private suspend fun listNamesById(userId: String): Either<AppError, Map<String, String>> =
        when (val result = allLists(userId)) {
            is Either.Left -> result
            is Either.Right -> Either.Right(result.value.associate { it.id to it.name })
        }

    /**
     * Turns a requested list into an id, or explains why it can't.
     *
     * A miss is deliberately not a silent create: the model gets the existing names and
     * has to come back with `createListIfMissing` once the user has agreed.
     */
    private suspend fun resolveTargetList(
        ctx: McpCallContext,
        listId: String?,
        listName: String?,
        namespace: ListNamespace,
        createIfMissing: Boolean,
    ): ListOutcome {
        if (listId == null && listName == null) return ListOutcome.None

        val candidates = when (val result = allLists(ctx.userId)) {
            is Either.Left -> return ListOutcome.Stop(McpToolResult.failed(result.value.toText()))
            is Either.Right -> result.value
        }

        if (listId != null) {
            val byId = candidates.firstOrNull { it.id == listId }
                ?: return ListOutcome.Stop(
                    McpToolResult.failed("No list with id $listId. Call ${McpToolCatalog.GET_CONTEXT} for the current lists."),
                )
            if (byId.namespace != namespace) return ListOutcome.Stop(crossNamespaceResult(byId, namespace))
            return ListOutcome.Found(byId)
        }

        val lookup = McpListResolver.lookup(listName!!, candidates, namespace)
        lookup.match?.let { return ListOutcome.Found(it) }
        lookup.crossNamespace?.let { return ListOutcome.Stop(crossNamespaceResult(it, namespace)) }

        if (!createIfMissing) {
            return ListOutcome.Stop(McpToolResult.failed(describeMiss(lookup, candidates)))
        }

        val created = when (namespace) {
            ListNamespace.SCHEDULED -> listService.create(ctx.userId, listName, null, null)
                .map { NamedList(it.id, it.name, ListNamespace.SCHEDULED) }

            ListNamespace.ANYTIME -> floaterListService.create(ctx.userId, listName, null, null, false)
                .map { NamedList(it.id, it.name, ListNamespace.ANYTIME) }
        }
        return when (created) {
            is Either.Left -> ListOutcome.Stop(McpToolResult.failed(created.value.toText()))
            is Either.Right -> ListOutcome.Found(created.value)
        }
    }

    private fun crossNamespaceResult(found: NamedList, wanted: ListNamespace): McpToolResult =
        McpToolResult.failed(
            "\"${found.name}\" is ${found.namespace.indefinite} list, which holds ${found.namespace.holds}, " +
                "but this task needs ${wanted.indefinite} list (${wanted.holds}). " +
                "T'Day keeps the two kinds of list separate. Either pick ${wanted.indefinite} list, or " +
                "create one with this name via ${McpToolCatalog.CREATE_LIST}.",
        )

    private fun describeMiss(lookup: ListLookup, allCandidates: List<NamedList>): String = buildString {
        val where = lookup.namespace?.let { "${it.label} list" } ?: "list"
        append("No $where named \"${lookup.query}\" exists.")

        lookup.crossNamespace?.let {
            append(" There is ${it.namespace.indefinite} list with that name (it holds ${it.namespace.holds}) — listId: ${it.id}.")
        }
        if (lookup.suggestions.isNotEmpty()) {
            append(" Closest ${if (lookup.suggestions.size == 1) "match" else "matches"}: ")
            append(lookup.suggestions.joinToString(", ") { "\"${it.name}\"" })
            append(".")
        }

        // Only ever offer names from the namespace that was asked for — a scheduled list
        // is not an option for an undated task, and listing one here would imply it is.
        val available = lookup.available.map { it.name }
        if (available.isEmpty()) {
            val scope = lookup.namespace?.let { "${it.label} lists" } ?: "lists"
            append(" You have no $scope yet.")
            if (lookup.namespace != null) {
                val others = allCandidates.filter { it.namespace != lookup.namespace }
                if (others.isNotEmpty()) {
                    append(
                        " (Your ${others.first().namespace.label} lists — ${others.joinToString(", ") { it.name }} — " +
                            "hold ${others.first().namespace.holds}, so they can't take this task.)",
                    )
                }
            }
        } else {
            append(" Available: ${available.joinToString(", ")}.")
        }
        append(" Don't create a list unless the user asks — say it doesn't exist first, then use createListIfMissing.")
    }

    private fun describeLists(names: List<String>): String =
        if (names.isEmpty()) "(none yet)" else names.joinToString(", ")

    private fun inList(list: NamedList?): String = list?.let { " in list \"${it.name}\"" }.orEmpty()

    private fun badHandle(raw: String?): McpToolResult = McpToolResult.failed(
        "\"${raw.orEmpty()}\" is not a task handle. Handles look like todo:<id> (a scheduled task) or " +
            "floater:<id> (an Anytime task), and come from ${McpToolCatalog.LIST_TASKS} or ${McpToolCatalog.SEARCH_TASKS}.",
    )

    private fun badOccurrence(raw: String): McpToolResult = McpToolResult.failed(
        "Could not read occurrenceDate \"$raw\". Pass the occurrence's date-time exactly as a read tool reported it.",
    )

    private fun TodoResponse.toResolvedTask() = ResolvedTask(
        handle = TaskHandle.of(TaskKind.TODO, id),
        title = title,
        notes = description,
        due = McpDates.parseWireFormat(due),
        occurrenceDate = instanceDate,
        recurrence = rrule,
        priority = priority,
        completed = completed,
        pinned = pinned,
        listId = listID,
    )

    private fun FloaterResponse.toResolvedTask() = ResolvedTask(
        handle = TaskHandle.of(TaskKind.FLOATER, id),
        title = title,
        notes = description,
        due = null,
        priority = priority,
        completed = completed,
        pinned = pinned,
        listId = listID,
    )

    private companion object {
        const val MAX_LIMIT = 500
        const val DEFAULT_UPCOMING_DAYS = 365
        const val EXPANSION_LOOKBACK_DAYS = 90L
        const val EXPANSION_LOOKAHEAD_DAYS = 400L
    }
}

/** A task flattened into what the model needs to read and act on it. */
data class ResolvedTask(
    val handle: TaskHandle,
    val title: String,
    val notes: String? = null,
    val due: LocalDateTime? = null,
    val occurrenceDate: String? = null,
    val recurrence: String? = null,
    val priority: String = "Low",
    val completed: Boolean = false,
    val pinned: Boolean = false,
    val listId: String? = null,
) {
    fun render(zone: ZoneId, listNames: Map<String, String>): String = buildString {
        append("- ")
        if (completed) append("[done] ")
        append(title)
        due?.let { append(" — due ${McpDates.display(it.toString(), zone)}") }
        if (due == null) append(" — Anytime (no date)")
        if (priority != "Low") append(" — $priority priority")
        if (pinned) append(" — pinned")
        recurrence?.let { append(" — repeats ($it)") }
        listId?.let { id -> listNames[id]?.let { append(" — list \"$it\"") } }
        append(" — ")
        append(handle)
        occurrenceDate?.let {
            val local = McpDates.parseWireFormat(it)?.toZone(zone)
            append(", occurrenceDate: ${local ?: it}")
        }
        notes?.takeIf { it.isNotBlank() }?.let { append("\n    notes: ${it.replace('\n', ' ').take(200)}") }
    }
}

/** Parsed `tday_create_task` arguments. */
data class CreateTaskArgs(
    val title: String,
    val notes: String? = null,
    val due: String? = null,
    val dueWasDateOnly: Boolean = false,
    val recurrence: String? = null,
    val priority: String = "Low",
    val listName: String? = null,
    val listId: String? = null,
    val createListIfMissing: Boolean = false,
)

/** Parsed `tday_update_task` arguments. */
data class UpdateTaskArgs(
    val taskId: String,
    val title: String? = null,
    val notes: String? = null,
    val priority: String? = null,
    val due: String? = null,
    val clearDue: Boolean = false,
    val recurrence: String? = null,
    val clearRecurrence: Boolean = false,
    val listName: String? = null,
    val listId: String? = null,
    val createListIfMissing: Boolean = false,
    val pinned: Boolean? = null,
    val occurrenceDate: String? = null,
)

/** Result of resolving a requested list: use it, stop and explain, or no list wanted. */
sealed interface ListOutcome {
    data object None : ListOutcome
    data class Found(val list: NamedList) : ListOutcome
    data class Stop(val result: McpToolResult) : ListOutcome
}

private inline fun ListOutcome.orReturn(onStop: (McpToolResult) -> Nothing): NamedList? = when (this) {
    is ListOutcome.None -> null
    is ListOutcome.Found -> list
    is ListOutcome.Stop -> onStop(result)
}

private inline fun <T> Either<AppError, T>.orFailure(onError: (McpToolResult) -> Nothing): T = when (this) {
    is Either.Left -> onError(McpToolResult.failed(value.toText()))
    is Either.Right -> value
}

/** Reads the `kind` argument shared by the list-aware tools. */
internal fun parseNamespace(kind: String?): ListNamespace? = when (kind?.trim()?.lowercase()) {
    "scheduled", "todo", "todos", "dated" -> ListNamespace.SCHEDULED
    "anytime", "floater", "floaters", "undated" -> ListNamespace.ANYTIME
    else -> null
}

internal fun AppError.toText(): String = when (this) {
    is AppError.NotFound -> "Not found: $message."
    is AppError.BadRequest -> "Rejected: $message."
    is AppError.Forbidden -> "Not allowed: $message."
    is AppError.Conflict -> "Conflict: $message."
    is AppError.Unauthorized -> "Not authenticated: $message."
    is AppError.Internal -> "T'Day hit an internal error handling that."
}

/** The `view` argument of `tday_list_tasks`. */
enum class TaskView(
    val value: String,
    val includesScheduled: Boolean,
    val includesAnytime: Boolean,
) {
    TODAY("today", true, false),
    OVERDUE("overdue", true, false),
    UPCOMING("upcoming", true, false),
    ANYTIME("anytime", false, true),
    ALL("all", true, true);

    /** Null when a supplied from/to could not be parsed. Never called for [ANYTIME]. */
    fun windowFor(zone: ZoneId, from: String?, to: String?): Window? {
        val explicitFrom = from?.let { McpDates.parseWindowStart(it, zone) ?: return null }
        val explicitTo = to?.let { McpDates.parseWindowEnd(it, zone) ?: return null }
        val defaultEnd = McpDates.endOfToday(zone).plusDays(UPCOMING_DAYS)
        return when (this) {
            TODAY -> Window(McpDates.startOfToday(zone), McpDates.endOfToday(zone))
            OVERDUE -> Window(McpDates.FLOOR, McpDates.nowUtc())
            UPCOMING -> Window(explicitFrom ?: McpDates.startOfToday(zone), explicitTo ?: defaultEnd)
            ANYTIME -> Window(McpDates.FLOOR, McpDates.FLOOR)
            ALL -> Window(explicitFrom ?: McpDates.FLOOR, explicitTo ?: defaultEnd)
        }
    }

    fun describe(listName: String?): String {
        val scope = when (this) {
            TODAY -> "task(s) due today"
            OVERDUE -> "overdue task(s)"
            UPCOMING -> "upcoming task(s)"
            ANYTIME -> "Anytime task(s)"
            ALL -> "task(s)"
        }
        return listName?.let { "$scope in list \"$it\"" } ?: scope
    }

    companion object {
        private const val UPCOMING_DAYS = 14L

        fun parse(value: String?): TaskView? =
            entries.firstOrNull { it.value.equals(value?.trim(), ignoreCase = true) }
    }
}

data class Window(val from: LocalDateTime, val to: LocalDateTime)
