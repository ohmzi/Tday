package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.CompletedTodoResponse
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.security.decryptRequired
import com.ohmz.tday.security.encryptRequired
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import com.ohmz.tday.shared.model.Priority

interface CompletedTodoService {
    suspend fun getAll(userId: String): Either<AppError, List<CompletedTodoResponse>>
    suspend fun deleteAll(userId: String): Either<AppError, Int>
    suspend fun deleteById(userId: String, id: String): Either<AppError, Int>
    suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Int>
}

class CompletedTodoServiceImpl(
    private val fieldEncryption: FieldEncryption,
) : CompletedTodoService {
    override suspend fun getAll(userId: String): Either<AppError, List<CompletedTodoResponse>> {
        val todos = newSuspendedTransaction(Dispatchers.IO) {
            CompletedTodos.selectAll().where { CompletedTodos.userID eq userId }
                .orderBy(CompletedTodos.completedAt, SortOrder.DESC)
                .map { it.toCompletedResponse() }
        }
        return todos.right()
    }

    override suspend fun deleteAll(userId: String): Either<AppError, Int> {
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedTodos.deleteWhere { CompletedTodos.userID eq userId }
        }
        return count.right()
    }

    override suspend fun deleteById(userId: String, id: String): Either<AppError, Int> {
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedTodos.deleteWhere { (CompletedTodos.id eq id) and (CompletedTodos.userID eq userId) }
        }
        return count.right()
    }

    override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Int> {
        val result = newSuspendedTransaction(Dispatchers.IO) {
            val requestedListId = (fields["listID"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val list = requestedListId?.let { listId ->
                Lists.selectAll().where {
                    (Lists.id eq listId) and (Lists.userID eq userId)
                }.firstOrNull()
            }
            if (requestedListId != null && list == null) {
                return@newSuspendedTransaction null
            }
            CompletedTodos.update({ (CompletedTodos.id eq id) and (CompletedTodos.userID eq userId) }) { stmt ->
                fields["title"]?.let {
                    stmt[CompletedTodos.title] = fieldEncryption.encryptRequired("title", it as String)
                }
                fields["description"]?.let {
                    stmt[CompletedTodos.description] = fieldEncryption.encryptIfSensitive("description", it as? String)
                }
                fields["priority"]?.let { stmt[CompletedTodos.priority] = Priority.valueOf(it as String) }
                (fields["due"] as? LocalDateTime)?.let { stmt[CompletedTodos.due] = it }
                if (fields.containsKey("rrule")) stmt[CompletedTodos.rrule] = fields["rrule"] as? String
                if (fields.containsKey("listID")) {
                    stmt[CompletedTodos.listID] = requestedListId
                    stmt[CompletedTodos.listName] = list?.get(Lists.name)
                    stmt[CompletedTodos.listColor] = list?.get(Lists.color)?.name
                }
            }
        }
        val count = result ?: return Either.Left(AppError.BadRequest("list not found", "listID"))
        return count.right()
    }

    private fun ResultRow.toCompletedResponse(): CompletedTodoResponse = CompletedTodoResponse(
        id = this[CompletedTodos.id],
        originalTodoID = this[CompletedTodos.originalTodoID],
        title = fieldEncryption.decryptRequired(this[CompletedTodos.title]),
        description = fieldEncryption.decryptIfEncrypted(this[CompletedTodos.description]),
        priority = this[CompletedTodos.priority].name,
        completedAt = this[CompletedTodos.completedAt].toString(),
        due = this[CompletedTodos.due].toString(),
        completedOnTime = this[CompletedTodos.completedOnTime],
        daysToComplete = this[CompletedTodos.daysToComplete].toDouble(),
        rrule = this[CompletedTodos.rrule],
        userID = this[CompletedTodos.userID],
        instanceDate = this[CompletedTodos.instanceDate]?.toString(),
        listID = this[CompletedTodos.listID],
        listName = this[CompletedTodos.listName],
        listColor = this[CompletedTodos.listColor],
    )
}
