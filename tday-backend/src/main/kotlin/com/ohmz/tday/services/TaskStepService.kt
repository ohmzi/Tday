package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.tables.TaskSteps
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.shared.model.TaskStepDto
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Flat checklist steps inside a todo (R6-2). Ownership is enforced through the
 * parent todo: a step is mutable only when its todo belongs to [userId]. Steps
 * are a personal breakdown, so shared-list collaborators don't edit each other's.
 */
interface TaskStepService {
    suspend fun listForTodo(userId: String, todoId: String): Either<AppError, List<TaskStepDto>>
    suspend fun create(userId: String, todoId: String, title: String): Either<AppError, TaskStepDto>
    suspend fun toggle(userId: String, stepId: String, completed: Boolean): Either<AppError, TaskStepDto>
    suspend fun delete(userId: String, stepId: String): Either<AppError, Unit>
    suspend fun reorder(userId: String, todoId: String, orderedIds: List<String>): Either<AppError, Unit>
}

class TaskStepServiceImpl(
    private val cache: CacheService,
    private val publisher: RealtimePublisher,
) : TaskStepService {

    override suspend fun listForTodo(userId: String, todoId: String): Either<AppError, List<TaskStepDto>> {
        val steps = newSuspendedTransaction(Dispatchers.IO) {
            if (!ownsTodo(userId, todoId)) return@newSuspendedTransaction null
            TaskSteps.selectAll()
                .where { TaskSteps.todoID eq todoId }
                .orderBy(TaskSteps.position to SortOrder.ASC, TaskSteps.createdAt to SortOrder.ASC)
                .map { it.toDto() }
        } ?: return AppError.NotFound("todo not found").left()
        return steps.right()
    }

    override suspend fun create(userId: String, todoId: String, title: String): Either<AppError, TaskStepDto> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return AppError.BadRequest("step title is required").left()
        val dto = newSuspendedTransaction(Dispatchers.IO) {
            if (!ownsTodo(userId, todoId)) return@newSuspendedTransaction null
            val nextPosition = (TaskSteps
                .select(TaskSteps.position.max())
                .where { TaskSteps.todoID eq todoId }
                .firstOrNull()?.get(TaskSteps.position.max()) ?: -1) + 1
            val id = CuidGenerator.newCuid()
            val now = LocalDateTime.now(ZoneOffset.UTC)
            TaskSteps.insert {
                it[TaskSteps.id] = id
                it[TaskSteps.todoID] = todoId
                it[TaskSteps.title] = trimmed
                it[TaskSteps.completed] = false
                it[TaskSteps.position] = nextPosition
                it[TaskSteps.createdAt] = now
            }
            TaskStepDto(
                id = id,
                todoID = todoId,
                title = trimmed,
                completed = false,
                position = nextPosition,
                createdAt = now.toString(),
            )
        } ?: return AppError.NotFound("todo not found").left()
        notifyChange(userId)
        return dto.right()
    }

    override suspend fun toggle(userId: String, stepId: String, completed: Boolean): Either<AppError, TaskStepDto> {
        val dto = newSuspendedTransaction(Dispatchers.IO) {
            val row = TaskSteps.selectAll().where { TaskSteps.id eq stepId }.firstOrNull()
                ?: return@newSuspendedTransaction null
            if (!ownsTodo(userId, row[TaskSteps.todoID])) return@newSuspendedTransaction null
            TaskSteps.update({ TaskSteps.id eq stepId }) { it[TaskSteps.completed] = completed }
            row.toDto().copy(completed = completed)
        } ?: return AppError.NotFound("step not found").left()
        notifyChange(userId)
        return dto.right()
    }

    override suspend fun delete(userId: String, stepId: String): Either<AppError, Unit> {
        val ok = newSuspendedTransaction(Dispatchers.IO) {
            val row = TaskSteps.selectAll().where { TaskSteps.id eq stepId }.firstOrNull()
                ?: return@newSuspendedTransaction false
            if (!ownsTodo(userId, row[TaskSteps.todoID])) return@newSuspendedTransaction false
            TaskSteps.deleteWhere { TaskSteps.id eq stepId }
            true
        }
        if (!ok) return AppError.NotFound("step not found").left()
        notifyChange(userId)
        return Unit.right()
    }

    override suspend fun reorder(userId: String, todoId: String, orderedIds: List<String>): Either<AppError, Unit> {
        val ok = newSuspendedTransaction(Dispatchers.IO) {
            if (!ownsTodo(userId, todoId)) return@newSuspendedTransaction false
            // Only reorder steps that actually belong to this todo; ignore stragglers.
            val owned = TaskSteps.selectAll().where { TaskSteps.todoID eq todoId }
                .map { it[TaskSteps.id] }.toSet()
            orderedIds.filter { it in owned }.forEachIndexed { index, stepId ->
                TaskSteps.update({ TaskSteps.id eq stepId }) { it[TaskSteps.position] = index }
            }
            true
        }
        if (!ok) return AppError.NotFound("todo not found").left()
        notifyChange(userId)
        return Unit.right()
    }

    /** A step is editable only when its parent todo is owned by [userId]. */
    private fun ownsTodo(userId: String, todoId: String): Boolean =
        Todos.selectAll()
            .where { (Todos.id eq todoId) and (Todos.userID eq userId) }
            .limit(1).any()

    private suspend fun notifyChange(userId: String) {
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
    }

    private fun ResultRow.toDto() = TaskStepDto(
        id = this[TaskSteps.id],
        todoID = this[TaskSteps.todoID],
        title = this[TaskSteps.title],
        completed = this[TaskSteps.completed],
        position = this[TaskSteps.position],
        createdAt = this[TaskSteps.createdAt].toString(),
    )
}
