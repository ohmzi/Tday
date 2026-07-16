package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.TaskSteps
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.shared.model.TaskStepDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

interface TodoService {
    suspend fun create(userId: String, title: String, description: String?, priority: String, due: LocalDateTime, rrule: String?, listID: String?): Either<AppError, TodoResponse>
    suspend fun getByDateRange(userId: String, start: Long, end: Long, timeZone: String): Either<AppError, List<TodoResponse>>
    suspend fun getTimeline(userId: String, timeZone: String, recurringFutureDays: Int): Either<AppError, List<TodoResponse>>
    suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit>
    suspend fun delete(userId: String, id: String): Either<AppError, Int>
    suspend fun completeTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit>
    suspend fun uncompleteTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit>
    suspend fun prioritize(userId: String, todoId: String, priority: String): Either<AppError, Unit>
    suspend fun reorder(userId: String, todoId: String, newOrder: Int): Either<AppError, Unit>
    suspend fun getOverdue(userId: String, timeZone: String): Either<AppError, List<TodoResponse>>
    suspend fun patchInstance(userId: String, todoId: String, instanceDate: LocalDateTime, fields: Map<String, Any?>): Either<AppError, Unit>
    suspend fun deleteInstance(userId: String, todoId: String, instanceDate: LocalDateTime): Either<AppError, Unit>
    suspend fun demoteToFloater(userId: String, todoId: String): Either<AppError, FloaterResponse>
}

class TodoServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
    private val shareService: ListShareService,
    private val publisher: RealtimePublisher,
) : TodoService {

    override suspend fun create(
        userId: String, title: String, description: String?,
        priority: String, due: LocalDateTime,
        rrule: String?, listID: String?,
    ): Either<AppError, TodoResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val normalizedListID = listID?.trim()?.takeIf { it.isNotEmpty() }

        if (normalizedListID != null && !shareService.canEditList(userId, normalizedListID, ListType.SCHEDULED)) {
            return Either.Left(AppError.BadRequest("list not found", "listID"))
        }
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.insert {
                it[Todos.id] = id
                it[Todos.title] = title
                it[Todos.description] = fieldEncryption.encryptIfSensitive("description", description)
                it[Todos.priority] = Priority.valueOf(priority)
                it[Todos.due] = due
                it[Todos.rrule] = rrule
                it[Todos.listID] = normalizedListID
                it[Todos.userID] = userId
                it[Todos.createdAt] = now
                it[Todos.updatedAt] = now
                it[Todos.exdates] = emptyList()
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged(normalizedListID))
        return TodoResponse(
            id = id, title = title, description = description,
            priority = priority, due = due.toString(),
            rrule = rrule, timeZone = "UTC",
            completed = false, pinned = false, order = 0, listID = normalizedListID,
            userID = userId, createdAt = now.toString(), updatedAt = now.toString(),
        ).right()
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getByDateRange(userId: String, start: Long, end: Long, timeZone: String): Either<AppError, List<TodoResponse>> {
        val dateRangeStart = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), ZoneOffset.UTC)
        val dateRangeEnd = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), ZoneOffset.UTC)
        val visibleListIds = shareService.sharedListIdsFor(userId, ListType.SCHEDULED)

        val todos = newSuspendedTransaction(Dispatchers.IO) {
            val oneOff = Todos.selectAll().where {
                visibleTodos(userId, visibleListIds) and Todos.rrule.isNull() and
                    (Todos.completed eq false) and Todos.due.isNotNull() and (Todos.due greaterEq dateRangeStart) and
                    (Todos.due lessEq dateRangeEnd)
            }.orderBy(Todos.createdAt, SortOrder.DESC).map { it.toTodoResponse() }

            // One row per recurring template. The client expands occurrences from
            // rrule/exdates/instances itself, and toTodoResponse() reads no
            // TodoInstances columns, so joining instances here only fanned the
            // same todo out once per instance row (duplicate timeline entries).
            val recurring = Todos.selectAll().where {
                visibleTodos(userId, visibleListIds) and Todos.rrule.isNotNull() and
                    (Todos.completed eq false)
            }.map { it.toTodoResponse() }

            oneOff + recurring
        }
        return todos.right()
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getTimeline(userId: String, timeZone: String, recurringFutureDays: Int): Either<AppError, List<TodoResponse>> {
        val visibleListIds = shareService.sharedListIdsFor(userId, ListType.SCHEDULED)
        val todos = newSuspendedTransaction(Dispatchers.IO) {
            val oneOff = Todos.selectAll().where {
                visibleTodos(userId, visibleListIds) and Todos.rrule.isNull() and (Todos.completed eq false)
            }.orderBy(Todos.due to SortOrder.ASC, Todos.order to SortOrder.ASC).map { it.toTodoResponse() }

            // See getByDateRange: emit one row per recurring template, not one per
            // persisted instance, to avoid duplicate entries in the timeline.
            val recurring = Todos.selectAll().where {
                visibleTodos(userId, visibleListIds) and Todos.rrule.isNotNull() and (Todos.completed eq false)
            }.map { it.toTodoResponse() }

            oneOff + recurring
        }
        return todos.right()
    }

    override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
        val targetListId = (fields["listID"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (fields.containsKey("listID") && targetListId != null &&
            !shareService.canEditList(userId, targetListId, ListType.SCHEDULED)
        ) {
            return Either.Left(AppError.BadRequest("list not found", "listID"))
        }
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.update({ (Todos.id eq id) and mutableTodos(userId, editableListIds) }) { stmt ->
                fields["title"]?.let { stmt[Todos.title] = it as String }
                fields["description"]?.let {
                    stmt[Todos.description] = fieldEncryption.encryptIfSensitive("description", it as? String)
                }
                fields["priority"]?.let { stmt[Todos.priority] = Priority.valueOf(it as String) }
                fields["pinned"]?.let { stmt[Todos.pinned] = it as Boolean }
                fields["completed"]?.let { stmt[Todos.completed] = it as Boolean }
                (fields["due"] as? LocalDateTime)?.let { stmt[Todos.due] = it }
                if (fields.containsKey("rrule")) stmt[Todos.rrule] = fields["rrule"] as? String
                if (fields.containsKey("listID")) stmt[Todos.listID] = targetListId
                stmt[Todos.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged(targetListId))
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> {
        val editableListIds = editableListIdsFor(userId)
        val count = newSuspendedTransaction(Dispatchers.IO) {
            Todos.deleteWhere { (Todos.id eq id) and mutableTodos(userId, editableListIds) }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        return count.right()
    }

    override suspend fun demoteToFloater(userId: String, todoId: String): Either<AppError, FloaterResponse> {
        val newFloaterId = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        // Owner-only: demoting moves the row out of any shared list (see
        // FloaterService.promoteToTodo for the symmetric rationale).
        val result: Either<AppError, ResultRow> = newSuspendedTransaction(Dispatchers.IO) {
            val todo = Todos.selectAll().where {
                (Todos.id eq todoId) and (Todos.userID eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction Either.Left(AppError.NotFound("todo not found"))

            if (todo[Todos.rrule] != null) {
                // Floaters carry no recurrence; silently destroying a series is
                // worse than asking the user to end it first.
                return@newSuspendedTransaction Either.Left(
                    AppError.BadRequest("recurring tasks cannot be demoted to floaters", "id"),
                )
            }

            Floaters.insert {
                it[Floaters.id] = newFloaterId
                it[Floaters.title] = todo[Todos.title]
                // Ciphertext copies straight across — both tables encrypt "description".
                it[Floaters.description] = todo[Todos.description]
                it[Floaters.priority] = todo[Todos.priority]
                it[Floaters.pinned] = todo[Todos.pinned]
                // Todo lists and floater lists are separate types; membership stays behind.
                it[Floaters.listID] = null
                it[Floaters.userID] = userId
                it[Floaters.createdAt] = todo[Todos.createdAt]
                it[Floaters.updatedAt] = now
            }
            Todos.deleteWhere { (Todos.id eq todoId) and (Todos.userID eq userId) }
            Either.Right(todo)
        }
        val demoted = when (result) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }

        cache.invalidateTodoCaches(userId)
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        return FloaterResponse(
            id = newFloaterId,
            title = demoted[Todos.title],
            description = fieldEncryption.decryptIfEncrypted(demoted[Todos.description]),
            pinned = demoted[Todos.pinned],
            priority = demoted[Todos.priority].name,
            completed = false,
            order = 0,
            listID = null,
            userID = userId,
            createdAt = demoted[Todos.createdAt].toString(),
            updatedAt = now.toString(),
        ).right()
    }

    override suspend fun completeTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            val todo = Todos.selectAll().where {
                (Todos.id eq todoId) and mutableTodos(userId, editableListIds)
            }.firstOrNull() ?: return@newSuspendedTransaction

            val now = LocalDateTime.now(ZoneOffset.UTC)
            // Deadlines are minute-precision; floor the parent due in case a legacy row still
            // carries seconds (pre-migration), so the completed snapshot + on-time check align.
            val todoDue = todo[Todos.due].withSecond(0).withNano(0)
            val daysToComplete = Duration.between(todo[Todos.createdAt], now).toDays().toDouble()
            // Access was already checked against the todo; the list row is only
            // denormalized metadata, so no owner filter here (shared lists belong
            // to another user).
            val list = todo[Todos.listID]?.let { listId ->
                Lists.selectAll().where { Lists.id eq listId }.firstOrNull()
            }
            val existingCompleted = CompletedTodos.selectAll().where {
                if (instanceDate != null) {
                    (CompletedTodos.userID eq userId) and
                        (CompletedTodos.originalTodoID eq todoId) and
                        (CompletedTodos.instanceDate eq instanceDate)
                } else {
                    (CompletedTodos.userID eq userId) and
                        (CompletedTodos.originalTodoID eq todoId) and
                        CompletedTodos.instanceDate.isNull()
                }
            }.firstOrNull()

            if (existingCompleted == null) {
                // Snapshot the todo's steps as JSON so history survives the parent's
                // deletion (R6-2). Null when the todo has no steps.
                val stepSnapshot = TaskSteps.selectAll()
                    .where { TaskSteps.todoID eq todoId }
                    .orderBy(TaskSteps.position to SortOrder.ASC, TaskSteps.createdAt to SortOrder.ASC)
                    .map { row ->
                        TaskStepDto(
                            id = row[TaskSteps.id],
                            todoID = todoId,
                            title = row[TaskSteps.title],
                            completed = row[TaskSteps.completed],
                            position = row[TaskSteps.position],
                            createdAt = row[TaskSteps.createdAt].toString(),
                        )
                    }
                val stepsJson = if (stepSnapshot.isEmpty()) null else Json.encodeToString(stepSnapshot)
                CompletedTodos.insert {
                    it[CompletedTodos.id] = CuidGenerator.newCuid()
                    it[CompletedTodos.originalTodoID] = todoId
                    it[CompletedTodos.title] = todo[Todos.title]
                    it[CompletedTodos.description] = todo[Todos.description]
                    it[CompletedTodos.priority] = todo[Todos.priority]
                    it[CompletedTodos.completedAt] = now
                    it[CompletedTodos.due] = todoDue
                    // Compare at minute granularity: completing within the due minute is on time.
                    it[CompletedTodos.completedOnTime] = !now.withSecond(0).withNano(0).isAfter(todoDue)
                    it[CompletedTodos.daysToComplete] = BigDecimal.valueOf(daysToComplete).setScale(2, RoundingMode.HALF_UP)
                    it[CompletedTodos.rrule] = todo[Todos.rrule]
                    it[CompletedTodos.userID] = userId
                    it[CompletedTodos.instanceDate] = instanceDate
                    it[CompletedTodos.listID] = todo[Todos.listID]
                    it[CompletedTodos.listName] = list?.get(Lists.name)
                    it[CompletedTodos.listColor] = list?.get(Lists.color)?.name
                    it[CompletedTodos.steps] = stepsJson
                }
            }

            if (todo[Todos.rrule] == null) {
                Todos.update({ Todos.id eq todoId }) {
                    it[Todos.completed] = true
                    it[Todos.updatedAt] = now
                }
            } else if (instanceDate != null) {
                val existing = TodoInstances.selectAll().where {
                    (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
                }.firstOrNull()

                if (existing != null) {
                    TodoInstances.update({
                        (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
                    }) { it[TodoInstances.completedAt] = now }
                } else {
                    TodoInstances.insert {
                        it[TodoInstances.id] = CuidGenerator.newCuid()
                        it[TodoInstances.todoId] = todoId
                        it[TodoInstances.recurId] = instanceDate.toString()
                        it[TodoInstances.instanceDate] = instanceDate
                        it[TodoInstances.completedAt] = now
                    }
                }
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        publisher.publishToCollaborators(userId, DomainEvent.CompletedChanged())
        return Unit.right()
    }

    override suspend fun uncompleteTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            // TodoInstances has no userID column, so verify access against the
            // parent Todos row before touching any instance state (mirrors
            // completeTodo/patchInstance/deleteInstance). Without this, another
            // user could clear an instance's completion by todoId+instanceDate.
            val canMutate = Todos.selectAll().where {
                (Todos.id eq todoId) and mutableTodos(userId, editableListIds)
            }.limit(1).any()
            if (!canMutate) return@newSuspendedTransaction

            if (instanceDate != null) {
                TodoInstances.update({
                    (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
                }) { it[TodoInstances.completedAt] = null }
            } else {
                Todos.update({ Todos.id eq todoId }) {
                    it[Todos.completed] = false
                    it[Todos.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
                }
            }

            CompletedTodos.deleteWhere {
                if (instanceDate != null) {
                    (CompletedTodos.userID eq userId) and
                        (CompletedTodos.originalTodoID eq todoId) and
                        (CompletedTodos.instanceDate eq instanceDate)
                } else {
                    (CompletedTodos.userID eq userId) and
                        (CompletedTodos.originalTodoID eq todoId) and
                        CompletedTodos.instanceDate.isNull()
                }
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        publisher.publishToCollaborators(userId, DomainEvent.CompletedChanged())
        return Unit.right()
    }

    override suspend fun prioritize(userId: String, todoId: String, priority: String): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.update({ (Todos.id eq todoId) and mutableTodos(userId, editableListIds) }) {
                it[Todos.priority] = Priority.valueOf(priority)
                it[Todos.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        return Unit.right()
    }

    override suspend fun reorder(userId: String, todoId: String, newOrder: Int): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.update({ (Todos.id eq todoId) and mutableTodos(userId, editableListIds) }) {
                it[Todos.order] = newOrder
                it[Todos.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        return Unit.right()
    }

    override suspend fun getOverdue(userId: String, timeZone: String): Either<AppError, List<TodoResponse>> {
        // `due` is stored as a UTC wall-clock instant, so "overdue" is a pure
        // instant comparison and must be evaluated in UTC — not the user's local
        // zone (mixing frames shifted results by the user's UTC offset). The
        // user's zone only matters for local-day *grouping*, done client-side.
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val visibleListIds = shareService.sharedListIdsFor(userId, ListType.SCHEDULED)
        val todos = newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().where {
                visibleTodos(userId, visibleListIds) and (Todos.completed eq false) and
                    Todos.rrule.isNull() and Todos.due.isNotNull() and (Todos.due less now)
            }.orderBy(Todos.due, SortOrder.ASC).map { it.toTodoResponse() }
        }
        return todos.right()
    }

    override suspend fun patchInstance(userId: String, todoId: String, instanceDate: LocalDateTime, fields: Map<String, Any?>): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().where {
                (Todos.id eq todoId) and mutableTodos(userId, editableListIds)
            }.firstOrNull() ?: return@newSuspendedTransaction

            val existing = TodoInstances.selectAll().where {
                (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
            }.firstOrNull()

            if (existing != null) {
                TodoInstances.update({
                    (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
                }) { stmt ->
                    fields["title"]?.let { stmt[TodoInstances.overriddenTitle] = it as? String }
                    fields["description"]?.let {
                        stmt[TodoInstances.overriddenDescription] =
                            fieldEncryption.encryptIfSensitive("overriddenDescription", it as? String)
                    }
                    fields["priority"]?.let { p ->
                        stmt[TodoInstances.overriddenPriority] = Priority.valueOf(p as String)
                    }
                    fields["due"]?.let { stmt[TodoInstances.overriddenDue] = it as? LocalDateTime }
                }
            } else {
                TodoInstances.insert { stmt ->
                    stmt[TodoInstances.id] = CuidGenerator.newCuid()
                    stmt[TodoInstances.todoId] = todoId
                    stmt[TodoInstances.recurId] = instanceDate.toString()
                    stmt[TodoInstances.instanceDate] = instanceDate
                    fields["title"]?.let { stmt[TodoInstances.overriddenTitle] = it as? String }
                    fields["description"]?.let {
                        stmt[TodoInstances.overriddenDescription] =
                            fieldEncryption.encryptIfSensitive("overriddenDescription", it as? String)
                    }
                    fields["priority"]?.let { p ->
                        stmt[TodoInstances.overriddenPriority] = Priority.valueOf(p as String)
                    }
                    fields["due"]?.let { stmt[TodoInstances.overriddenDue] = it as? LocalDateTime }
                }
            }
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        return Unit.right()
    }

    override suspend fun deleteInstance(userId: String, todoId: String, instanceDate: LocalDateTime): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().where {
                (Todos.id eq todoId) and mutableTodos(userId, editableListIds)
            }.firstOrNull() ?: return@newSuspendedTransaction

            TodoInstances.deleteWhere {
                (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
            }

            // Parameter-bound, not string-interpolated: array_append has no Exposed
            // DSL equivalent, but the values must still be bound, not escaped by hand.
            exec(
                "UPDATE todos SET exdates = array_append(exdates, ?::timestamp) WHERE id = ?",
                args = listOf(
                    TextColumnType() to Timestamp.valueOf(instanceDate).toString(),
                    TextColumnType() to todoId,
                ),
            )
        }
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        return Unit.right()
    }

    private fun ResultRow.toTodoResponse(): TodoResponse = TodoResponse(
        id = this[Todos.id],
        title = this[Todos.title],
        description = fieldEncryption.decryptIfEncrypted(this[Todos.description]),
        createdAt = this[Todos.createdAt].toString(),
        updatedAt = this[Todos.updatedAt].toString(),
        userID = this[Todos.userID],
        pinned = this[Todos.pinned],
        order = this[Todos.order],
        priority = this[Todos.priority].name,
        due = this[Todos.due].toString(),
        rrule = this[Todos.rrule],
        timeZone = this[Todos.timeZone],
        completed = this[Todos.completed],
        listID = this[Todos.listID],
    )

    private suspend fun editableListIdsFor(userId: String): List<String> =
        shareService.sharedListIdsFor(userId, ListType.SCHEDULED, editorOnly = true)

    /** Todos the user can see: their own, plus everything in lists shared with them. */
    private fun visibleTodos(userId: String, sharedListIds: List<String>): Op<Boolean> =
        Op.build {
            if (sharedListIds.isEmpty()) {
                Todos.userID eq userId
            } else {
                (Todos.userID eq userId) or (Todos.listID inList sharedListIds)
            }
        }

    /**
     * Todos the user can mutate: their own, plus everything in lists where they
     * are an EDITOR. Viewers fail closed here (0 rows matched, existing no-op
     * behavior).
     */
    private fun mutableTodos(userId: String, editableListIds: List<String>): Op<Boolean> =
        Op.build {
            if (editableListIds.isEmpty()) {
                Todos.userID eq userId
            } else {
                (Todos.userID eq userId) or (Todos.listID inList editableListIds)
            }
        }
}
