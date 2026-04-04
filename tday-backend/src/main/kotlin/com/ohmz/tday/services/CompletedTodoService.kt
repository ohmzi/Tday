package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.CompletedTodoResponse
import com.ohmz.tday.security.FieldEncryption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface CompletedTodoService {
    suspend fun getAll(userId: String): Either<AppError, List<CompletedTodoResponse>>
    suspend fun deleteAll(userId: String): Either<AppError, Int>
    suspend fun deleteById(userId: String, id: String): Either<AppError, Int>
}

class CompletedTodoServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
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
        cache.invalidateCompletedCaches(userId)
        return count.right()
    }

    override suspend fun deleteById(userId: String, id: String): Either<AppError, Int> {
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedTodos.deleteWhere { (CompletedTodos.id eq id) and (CompletedTodos.userID eq userId) }
        }
        cache.invalidateCompletedCaches(userId)
        return count.right()
    }

    private fun ResultRow.toCompletedResponse(): CompletedTodoResponse = CompletedTodoResponse(
        id = this[CompletedTodos.id],
        originalTodoID = this[CompletedTodos.originalTodoID],
        title = this[CompletedTodos.title],
        description = fieldEncryption.decryptIfEncrypted(this[CompletedTodos.description]),
        priority = this[CompletedTodos.priority].name,
        completedAt = this[CompletedTodos.completedAt].toString(),
        due = this[CompletedTodos.due].toString(),
        completedOnTime = this[CompletedTodos.completedOnTime],
        daysToComplete = this[CompletedTodos.daysToComplete].toDouble(),
        rrule = this[CompletedTodos.rrule],
        userID = this[CompletedTodos.userID],
        instanceDate = this[CompletedTodos.instanceDate]?.toString(),
        listName = this[CompletedTodos.listName],
        listColor = this[CompletedTodos.listColor],
    )
}
