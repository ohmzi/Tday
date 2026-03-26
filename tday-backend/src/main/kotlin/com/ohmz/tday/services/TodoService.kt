package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.security.FieldEncryption
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

interface TodoService {
    suspend fun create(userId: String, title: String, description: String?, priority: String, dtstart: LocalDateTime, due: LocalDateTime, rrule: String?, listID: String?): Either<AppError, TodoResponse>
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
}

class TodoServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
) : TodoService {

    override suspend fun create(
        userId: String, title: String, description: String?,
        priority: String, dtstart: LocalDateTime, due: LocalDateTime,
        rrule: String?, listID: String?,
    ): Either<AppError, TodoResponse> {
        val durationMinutes = Duration.between(dtstart, due).toMinutes().toInt()
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now()

        transaction {
            Todos.insert {
                it[Todos.id] = id
                it[Todos.title] = title
                it[Todos.description] = fieldEncryption.encryptIfSensitive("description", description)
                it[Todos.priority] = Priority.valueOf(priority)
                it[Todos.dtstart] = dtstart
                it[Todos.due] = due
                it[Todos.durationMinutes] = durationMinutes
                it[Todos.rrule] = rrule
                it[Todos.listID] = listID
                it[Todos.userID] = userId
                it[Todos.createdAt] = now
                it[Todos.updatedAt] = now
                it[Todos.exdates] = emptyList()
            }
        }
        cache.invalidateTodoCaches(userId)
        return TodoResponse(
            id = id, title = title, description = description,
            priority = priority, dtstart = dtstart.toString(), due = due.toString(),
            durationMinutes = durationMinutes, rrule = rrule, timeZone = "UTC",
            completed = false, pinned = false, order = 0, listID = listID,
            userID = userId, createdAt = now.toString(), updatedAt = now.toString(),
        ).right()
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getByDateRange(userId: String, start: Long, end: Long, timeZone: String): Either<AppError, List<TodoResponse>> {
        val dateRangeStart = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), ZoneOffset.UTC)
        val dateRangeEnd = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), ZoneOffset.UTC)

        val todos = transaction {
            val oneOff = Todos.selectAll().where {
                (Todos.userID eq userId) and Todos.rrule.isNull() and
                    (Todos.completed eq false) and (Todos.due greaterEq dateRangeStart) and
                    (Todos.dtstart lessEq dateRangeEnd)
            }.orderBy(Todos.createdAt, SortOrder.DESC).map { it.toTodoResponse() }

            val recurring = Todos.join(TodoInstances, JoinType.LEFT, Todos.id, TodoInstances.todoId)
                .selectAll().where {
                    (Todos.userID eq userId) and Todos.rrule.isNotNull() and
                        (Todos.dtstart lessEq dateRangeEnd) and (Todos.completed eq false)
                }.map { it.toTodoResponse() }

            oneOff + recurring
        }
        return todos.right()
    }

    @Suppress("UNUSED_PARAMETER")
    override suspend fun getTimeline(userId: String, timeZone: String, recurringFutureDays: Int): Either<AppError, List<TodoResponse>> {
        val todos = transaction {
            val oneOff = Todos.selectAll().where {
                (Todos.userID eq userId) and Todos.rrule.isNull() and (Todos.completed eq false)
            }.orderBy(Todos.due to SortOrder.ASC, Todos.order to SortOrder.ASC).map { it.toTodoResponse() }

            val recurring = Todos.join(TodoInstances, JoinType.LEFT, Todos.id, TodoInstances.todoId)
                .selectAll().where {
                    (Todos.userID eq userId) and Todos.rrule.isNotNull() and (Todos.completed eq false)
                }.map { it.toTodoResponse() }

            oneOff + recurring
        }
        return todos.right()
    }

    override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
        transaction {
            Todos.update({ (Todos.id eq id) and (Todos.userID eq userId) }) { stmt ->
                fields["title"]?.let { stmt[Todos.title] = it as String }
                fields["description"]?.let {
                    stmt[Todos.description] = fieldEncryption.encryptIfSensitive("description", it as? String)
                }
                fields["priority"]?.let { stmt[Todos.priority] = Priority.valueOf(it as String) }
                fields["pinned"]?.let { stmt[Todos.pinned] = it as Boolean }
                fields["completed"]?.let { stmt[Todos.completed] = it as Boolean }
                fields["dtstart"]?.let { stmt[Todos.dtstart] = it as LocalDateTime }
                fields["due"]?.let { stmt[Todos.due] = it as LocalDateTime }
                fields["rrule"]?.let { stmt[Todos.rrule] = it as? String }
                fields["listID"]?.let { stmt[Todos.listID] = it as? String }
                stmt[Todos.updatedAt] = LocalDateTime.now()

                val ds = fields["dtstart"] as? LocalDateTime
                val d = fields["due"] as? LocalDateTime
                if (ds != null && d != null) {
                    stmt[Todos.durationMinutes] = Duration.between(ds, d).toMinutes().toInt()
                }
            }
        }
        cache.invalidateTodoCaches(userId)
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> {
        val count = transaction {
            Todos.deleteWhere { (Todos.id eq id) and (Todos.userID eq userId) }
        }
        cache.invalidateTodoCaches(userId)
        return count.right()
    }

    override suspend fun completeTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit> {
        transaction {
            val todo = Todos.selectAll().where {
                (Todos.id eq todoId) and (Todos.userID eq userId)
            }.firstOrNull() ?: return@transaction

            val now = LocalDateTime.now()
            val todoDtstart = todo[Todos.dtstart]
            val todoDue = todo[Todos.due]
            val daysToComplete = Duration.between(todoDtstart, now).toDays().toDouble()

            CompletedTodos.insert {
                it[CompletedTodos.id] = CuidGenerator.newCuid()
                it[CompletedTodos.originalTodoID] = todoId
                it[CompletedTodos.title] = todo[Todos.title]
                it[CompletedTodos.description] = todo[Todos.description]
                it[CompletedTodos.priority] = todo[Todos.priority]
                it[CompletedTodos.completedAt] = now
                it[CompletedTodos.dtstart] = todoDtstart
                it[CompletedTodos.due] = todoDue
                it[CompletedTodos.completedOnTime] = !now.isAfter(todoDue)
                it[CompletedTodos.daysToComplete] = BigDecimal.valueOf(daysToComplete).setScale(2, RoundingMode.HALF_UP)
                it[CompletedTodos.rrule] = todo[Todos.rrule]
                it[CompletedTodos.userID] = userId
                it[CompletedTodos.instanceDate] = instanceDate
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
        return Unit.right()
    }

    override suspend fun uncompleteTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit> {
        transaction {
            if (instanceDate != null) {
                TodoInstances.update({
                    (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
                }) { it[TodoInstances.completedAt] = null }
            } else {
                Todos.update({ (Todos.id eq todoId) and (Todos.userID eq userId) }) {
                    it[Todos.completed] = false
                    it[Todos.updatedAt] = LocalDateTime.now()
                }
            }
        }
        cache.invalidateTodoCaches(userId)
        return Unit.right()
    }

    override suspend fun prioritize(userId: String, todoId: String, priority: String): Either<AppError, Unit> {
        transaction {
            Todos.update({ (Todos.id eq todoId) and (Todos.userID eq userId) }) {
                it[Todos.priority] = Priority.valueOf(priority)
                it[Todos.updatedAt] = LocalDateTime.now()
            }
        }
        cache.invalidateTodoCaches(userId)
        return Unit.right()
    }

    override suspend fun reorder(userId: String, todoId: String, newOrder: Int): Either<AppError, Unit> {
        transaction {
            Todos.update({ (Todos.id eq todoId) and (Todos.userID eq userId) }) {
                it[Todos.order] = newOrder
                it[Todos.updatedAt] = LocalDateTime.now()
            }
        }
        cache.invalidateTodoCaches(userId)
        return Unit.right()
    }

    override suspend fun getOverdue(userId: String, timeZone: String): Either<AppError, List<TodoResponse>> {
        val now = LocalDateTime.now(ZoneId.of(timeZone))
        val todos = transaction {
            Todos.selectAll().where {
                (Todos.userID eq userId) and (Todos.completed eq false) and
                    Todos.rrule.isNull() and (Todos.due less now)
            }.orderBy(Todos.due, SortOrder.ASC).map { it.toTodoResponse() }
        }
        return todos.right()
    }

    override suspend fun patchInstance(userId: String, todoId: String, instanceDate: LocalDateTime, fields: Map<String, Any?>): Either<AppError, Unit> {
        transaction {
            Todos.selectAll().where {
                (Todos.id eq todoId) and (Todos.userID eq userId)
            }.firstOrNull() ?: return@transaction

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
                    fields["dtstart"]?.let { stmt[TodoInstances.overriddenDtstart] = it as? LocalDateTime }
                    fields["due"]?.let { stmt[TodoInstances.overriddenDue] = it as? LocalDateTime }
                    fields["durationMinutes"]?.let { stmt[TodoInstances.overriddenDurationMinutes] = it as? Int }
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
                    fields["dtstart"]?.let { stmt[TodoInstances.overriddenDtstart] = it as? LocalDateTime }
                    fields["due"]?.let { stmt[TodoInstances.overriddenDue] = it as? LocalDateTime }
                    fields["durationMinutes"]?.let { stmt[TodoInstances.overriddenDurationMinutes] = it as? Int }
                }
            }
        }
        cache.invalidateTodoCaches(userId)
        return Unit.right()
    }

    override suspend fun deleteInstance(userId: String, todoId: String, instanceDate: LocalDateTime): Either<AppError, Unit> {
        transaction {
            Todos.selectAll().where {
                (Todos.id eq todoId) and (Todos.userID eq userId)
            }.firstOrNull() ?: return@transaction

            TodoInstances.deleteWhere {
                (TodoInstances.todoId eq todoId) and (TodoInstances.instanceDate eq instanceDate)
            }

            val tsLiteral = Timestamp.valueOf(instanceDate).toString().replace("'", "''")
            val idLiteral = todoId.replace("'", "''")
            exec(
                "UPDATE todos SET exdates = array_append(exdates, '$tsLiteral'::timestamp) WHERE id = '$idLiteral'",
            )
        }
        cache.invalidateTodoCaches(userId)
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
        dtstart = this[Todos.dtstart].toString(),
        due = this[Todos.due].toString(),
        durationMinutes = this[Todos.durationMinutes],
        rrule = this[Todos.rrule],
        timeZone = this[Todos.timeZone],
        completed = this[Todos.completed],
        listID = this[Todos.listID],
    )
}
