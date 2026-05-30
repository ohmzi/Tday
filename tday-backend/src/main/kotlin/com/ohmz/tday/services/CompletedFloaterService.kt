package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.CompletedFloaterResponse
import com.ohmz.tday.security.FieldEncryption
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface CompletedFloaterService {
    suspend fun getAll(userId: String): Either<AppError, List<CompletedFloaterResponse>>
    suspend fun deleteAll(userId: String): Either<AppError, Int>
    suspend fun deleteById(userId: String, id: String): Either<AppError, Int>
    suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Int>
}

class CompletedFloaterServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
) : CompletedFloaterService {
    override suspend fun getAll(userId: String): Either<AppError, List<CompletedFloaterResponse>> {
        val floaters = newSuspendedTransaction(Dispatchers.IO) {
            CompletedFloaters.selectAll().where { CompletedFloaters.userID eq userId }
                .orderBy(CompletedFloaters.completedAt, SortOrder.DESC)
                .map { it.toCompletedFloaterResponse() }
        }
        return floaters.right()
    }

    override suspend fun deleteAll(userId: String): Either<AppError, Int> {
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedFloaters.deleteWhere { CompletedFloaters.userID eq userId }
        }
        cache.invalidateFloaterCaches(userId)
        return count.right()
    }

    override suspend fun deleteById(userId: String, id: String): Either<AppError, Int> {
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedFloaters.deleteWhere { (CompletedFloaters.id eq id) and (CompletedFloaters.userID eq userId) }
        }
        cache.invalidateFloaterCaches(userId)
        return count.right()
    }

    override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Int> {
        val result = newSuspendedTransaction(Dispatchers.IO) {
            val requestedListId = fields["listID"] as? String
            val list = requestedListId?.let { listId ->
                FloaterLists.selectAll()
                    .where { (FloaterLists.id eq listId) and (FloaterLists.userID eq userId) }
                    .firstOrNull()
            }
            if (requestedListId != null && list == null) {
                return@newSuspendedTransaction null
            }
            CompletedFloaters.update({ (CompletedFloaters.id eq id) and (CompletedFloaters.userID eq userId) }) { stmt ->
                fields["title"]?.let { stmt[CompletedFloaters.title] = it as String }
                fields["description"]?.let {
                    stmt[CompletedFloaters.description] = fieldEncryption.encryptIfSensitive("description", it as? String)
                }
                fields["priority"]?.let { stmt[CompletedFloaters.priority] = Priority.valueOf(it as String) }
                if (fields.containsKey("listID")) {
                    stmt[CompletedFloaters.listID] = requestedListId
                    stmt[CompletedFloaters.listName] = list?.get(FloaterLists.name)
                    stmt[CompletedFloaters.listColor] = list?.get(FloaterLists.color)?.name
                }
            }
        }
        val count = result ?: return Either.Left(AppError.BadRequest("floater list not found"))
        if (count > 0) cache.invalidateFloaterCaches(userId)
        return count.right()
    }

    private fun ResultRow.toCompletedFloaterResponse(): CompletedFloaterResponse = CompletedFloaterResponse(
        id = this[CompletedFloaters.id],
        originalFloaterID = this[CompletedFloaters.originalFloaterID],
        title = this[CompletedFloaters.title],
        description = fieldEncryption.decryptIfEncrypted(this[CompletedFloaters.description]),
        priority = this[CompletedFloaters.priority].name,
        completedAt = this[CompletedFloaters.completedAt].toString(),
        daysToComplete = this[CompletedFloaters.daysToComplete].toDouble(),
        userID = this[CompletedFloaters.userID],
        listID = this[CompletedFloaters.listID],
        listName = this[CompletedFloaters.listName],
        listColor = this[CompletedFloaters.listColor],
    )
}
