package com.ohmz.tday.services

import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.security.FieldEncryption
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object TodoService {
    fun create(
        userId: String,
        title: String,
        description: String?,
        priority: String,
        dtstart: LocalDateTime,
        due: LocalDateTime,
        rrule: String?,
        listID: String?,
    ): Map<String, Any?> {
        val durationMinutes = Duration.between(dtstart, due).toMinutes().toInt()
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now()

        transaction {
            Todos.insert {
                it[Todos.id] = id
                it[Todos.title] = title
                it[Todos.description] = FieldEncryption.encryptIfSensitive("description", description)
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
        MemoryCache.invalidateTodoCaches(userId)
        return mapOf("id" to id, "title" to title)
    }

    @Suppress("UNUSED_PARAMETER")
    fun getByDateRange(userId: String, start: Long, end: Long, timeZone: String): List<Map<String, Any?>> {
        val dateRangeStart = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), ZoneOffset.UTC)
        val dateRangeEnd = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), ZoneOffset.UTC)

        return transaction {
            val oneOff = Todos.selectAll().where {
                (Todos.userID eq userId) and Todos.rrule.isNull() and
                    (Todos.completed eq false) and (Todos.due greaterEq dateRangeStart) and
                    (Todos.dtstart lessEq dateRangeEnd)
            }.orderBy(Todos.createdAt, SortOrder.DESC).map { it.toTodoMap() }

            val recurring = Todos.join(TodoInstances, JoinType.LEFT, Todos.id, TodoInstances.todoId)
                .selectAll().where {
                    (Todos.userID eq userId) and Todos.rrule.isNotNull() and
                        (Todos.dtstart lessEq dateRangeEnd) and (Todos.completed eq false)
                }.map { it.toTodoMap() }

            oneOff + recurring
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getTimeline(userId: String, timeZone: String, recurringFutureDays: Int): List<Map<String, Any?>> {
        return transaction {
            val oneOff = Todos.selectAll().where {
                (Todos.userID eq userId) and Todos.rrule.isNull() and (Todos.completed eq false)
            }.orderBy(Todos.due to SortOrder.ASC, Todos.order to SortOrder.ASC).map { it.toTodoMap() }

            val recurring = Todos.join(TodoInstances, JoinType.LEFT, Todos.id, TodoInstances.todoId)
                .selectAll().where {
                    (Todos.userID eq userId) and Todos.rrule.isNotNull() and (Todos.completed eq false)
                }.map { it.toTodoMap() }

            oneOff + recurring
        }
    }

    fun update(userId: String, id: String, fields: Map<String, Any?>) {
        transaction {
            Todos.update({ (Todos.id eq id) and (Todos.userID eq userId) }) { stmt ->
                fields["title"]?.let { stmt[Todos.title] = it as String }
                fields["description"]?.let {
                    stmt[Todos.description] = FieldEncryption.encryptIfSensitive("description", it as? String)
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
        MemoryCache.invalidateTodoCaches(userId)
    }

    fun delete(userId: String, id: String): Int {
        val count = transaction {
            Todos.deleteWhere { (Todos.id eq id) and (Todos.userID eq userId) }
        }
        MemoryCache.invalidateTodoCaches(userId)
        return count
    }

    fun completeTodo(userId: String, todoId: String, instanceDate: LocalDateTime?) {
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
        MemoryCache.invalidateTodoCaches(userId)
    }

    fun uncompleteTodo(userId: String, todoId: String, instanceDate: LocalDateTime?) {
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
        MemoryCache.invalidateTodoCaches(userId)
    }

    fun prioritize(userId: String, todoId: String, priority: String) {
        transaction {
            Todos.update({ (Todos.id eq todoId) and (Todos.userID eq userId) }) {
                it[Todos.priority] = Priority.valueOf(priority)
                it[Todos.updatedAt] = LocalDateTime.now()
            }
        }
        MemoryCache.invalidateTodoCaches(userId)
    }

    fun reorder(userId: String, todoId: String, newOrder: Int) {
        transaction {
            Todos.update({ (Todos.id eq todoId) and (Todos.userID eq userId) }) {
                it[Todos.order] = newOrder
                it[Todos.updatedAt] = LocalDateTime.now()
            }
        }
        MemoryCache.invalidateTodoCaches(userId)
    }

    fun getOverdue(userId: String, timeZone: String): List<Map<String, Any?>> {
        val now = LocalDateTime.now(ZoneId.of(timeZone))
        return transaction {
            Todos.selectAll().where {
                (Todos.userID eq userId) and (Todos.completed eq false) and
                    Todos.rrule.isNull() and (Todos.due less now)
            }.orderBy(Todos.due, SortOrder.ASC).map { it.toTodoMap() }
        }
    }

    fun patchInstance(
        userId: String,
        todoId: String,
        instanceDate: LocalDateTime,
        fields: Map<String, Any?>,
    ) {
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
                            FieldEncryption.encryptIfSensitive("overriddenDescription", it as? String)
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
                            FieldEncryption.encryptIfSensitive("overriddenDescription", it as? String)
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
        MemoryCache.invalidateTodoCaches(userId)
    }

    fun deleteInstance(userId: String, todoId: String, instanceDate: LocalDateTime) {
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
        MemoryCache.invalidateTodoCaches(userId)
    }

    private fun ResultRow.toTodoMap(): Map<String, Any?> = mapOf(
        "id" to this[Todos.id],
        "title" to this[Todos.title],
        "description" to FieldEncryption.decryptIfEncrypted(this[Todos.description]),
        "createdAt" to this[Todos.createdAt].toString(),
        "updatedAt" to this[Todos.updatedAt].toString(),
        "userID" to this[Todos.userID],
        "pinned" to this[Todos.pinned],
        "order" to this[Todos.order],
        "priority" to this[Todos.priority].name,
        "dtstart" to this[Todos.dtstart].toString(),
        "due" to this[Todos.due].toString(),
        "durationMinutes" to this[Todos.durationMinutes],
        "rrule" to this[Todos.rrule],
        "timeZone" to this[Todos.timeZone],
        "completed" to this[Todos.completed],
        "listID" to this[Todos.listID],
    )
}
