package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.security.FieldEncryption
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

interface FloaterService {
    suspend fun create(userId: String, title: String, description: String?, priority: String, listID: String?): Either<AppError, FloaterResponse>
    suspend fun getAll(userId: String): Either<AppError, List<FloaterResponse>>
    suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit>
    suspend fun delete(userId: String, id: String): Either<AppError, Int>
    suspend fun completeFloater(userId: String, floaterId: String): Either<AppError, Unit>
    suspend fun uncompleteFloater(userId: String, floaterId: String): Either<AppError, Unit>
    suspend fun prioritize(userId: String, floaterId: String, priority: String): Either<AppError, Unit>
    suspend fun reorder(userId: String, floaterId: String, newOrder: Int): Either<AppError, Unit>
}

class FloaterServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
) : FloaterService {
    override suspend fun create(
        userId: String,
        title: String,
        description: String?,
        priority: String,
        listID: String?,
    ): Either<AppError, FloaterResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val normalizedListID = listID?.takeIf { it.isNotBlank() }
        val validList = newSuspendedTransaction(Dispatchers.IO) {
            if (normalizedListID != null && !floaterListExists(userId, normalizedListID)) {
                return@newSuspendedTransaction false
            }
            Floaters.insert {
                it[Floaters.id] = id
                it[Floaters.title] = title
                it[Floaters.description] = fieldEncryption.encryptIfSensitive("description", description)
                it[Floaters.priority] = Priority.valueOf(priority)
                it[Floaters.listID] = normalizedListID
                it[Floaters.userID] = userId
                it[Floaters.createdAt] = now
                it[Floaters.updatedAt] = now
            }
            true
        }
        if (!validList) return Either.Left(AppError.BadRequest("floater list not found"))
        cache.invalidateFloaterCaches(userId)
        return FloaterResponse(
            id = id,
            title = title,
            description = description,
            priority = priority,
            completed = false,
            pinned = false,
            order = 0,
            listID = normalizedListID,
            userID = userId,
            createdAt = now.toString(),
            updatedAt = now.toString(),
        ).right()
    }

    override suspend fun getAll(userId: String): Either<AppError, List<FloaterResponse>> {
        val floaters = newSuspendedTransaction(Dispatchers.IO) {
            Floaters.selectAll().where {
                (Floaters.userID eq userId) and (Floaters.completed eq false)
            }
                .orderBy(Floaters.priority to SortOrder.DESC, Floaters.pinned to SortOrder.DESC, Floaters.order to SortOrder.ASC)
                .map { it.toFloaterResponse() }
        }
        return floaters.right()
    }

    override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
        val validList = newSuspendedTransaction(Dispatchers.IO) {
            val listId = fields["listID"] as? String
            if (fields.containsKey("listID") && listId != null && !floaterListExists(userId, listId)) {
                return@newSuspendedTransaction false
            }
            Floaters.update({ (Floaters.id eq id) and (Floaters.userID eq userId) }) { stmt ->
                fields["title"]?.let { stmt[Floaters.title] = it as String }
                fields["description"]?.let {
                    stmt[Floaters.description] = fieldEncryption.encryptIfSensitive("description", it as? String)
                }
                fields["priority"]?.let { stmt[Floaters.priority] = Priority.valueOf(it as String) }
                fields["pinned"]?.let { stmt[Floaters.pinned] = it as Boolean }
                fields["completed"]?.let { stmt[Floaters.completed] = it as Boolean }
                if (fields.containsKey("listID")) stmt[Floaters.listID] = fields["listID"] as? String
                stmt[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
            true
        }
        if (!validList) return Either.Left(AppError.BadRequest("floater list not found"))
        cache.invalidateFloaterCaches(userId)
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> {
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedFloaters.deleteWhere {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq id)
            }
            Floaters.deleteWhere { (Floaters.id eq id) and (Floaters.userID eq userId) }
        }
        cache.invalidateFloaterCaches(userId)
        return count.right()
    }

    override suspend fun completeFloater(userId: String, floaterId: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            val floater = Floaters.selectAll().where {
                (Floaters.id eq floaterId) and (Floaters.userID eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction

            val now = LocalDateTime.now(ZoneOffset.UTC)
            val daysToComplete = Duration.between(floater[Floaters.createdAt], now).toDays().toDouble()
            val list = floater[Floaters.listID]?.let { listId ->
                FloaterLists.selectAll().where {
                    (FloaterLists.id eq listId) and (FloaterLists.userID eq userId)
                }.firstOrNull()
            }
            val existingCompleted = CompletedFloaters.selectAll().where {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
            }.firstOrNull()

            if (existingCompleted == null) {
                CompletedFloaters.insert {
                    it[CompletedFloaters.id] = CuidGenerator.newCuid()
                    it[CompletedFloaters.originalFloaterID] = floaterId
                    it[CompletedFloaters.title] = floater[Floaters.title]
                    it[CompletedFloaters.description] = floater[Floaters.description]
                    it[CompletedFloaters.priority] = floater[Floaters.priority]
                    it[CompletedFloaters.completedAt] = now
                    it[CompletedFloaters.daysToComplete] = BigDecimal.valueOf(daysToComplete).setScale(2, RoundingMode.HALF_UP)
                    it[CompletedFloaters.userID] = userId
                    it[CompletedFloaters.listID] = floater[Floaters.listID]
                    it[CompletedFloaters.listName] = list?.get(FloaterLists.name)
                    it[CompletedFloaters.listColor] = list?.get(FloaterLists.color)?.name
                }
            }

            Floaters.update({ (Floaters.id eq floaterId) and (Floaters.userID eq userId) }) {
                it[Floaters.completed] = true
                it[Floaters.updatedAt] = now
            }
        }
        cache.invalidateFloaterCaches(userId)
        return Unit.right()
    }

    override suspend fun uncompleteFloater(userId: String, floaterId: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.update({ (Floaters.id eq floaterId) and (Floaters.userID eq userId) }) {
                it[Floaters.completed] = false
                it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
            CompletedFloaters.deleteWhere {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
            }
        }
        cache.invalidateFloaterCaches(userId)
        return Unit.right()
    }

    override suspend fun prioritize(userId: String, floaterId: String, priority: String): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.update({ (Floaters.id eq floaterId) and (Floaters.userID eq userId) }) {
                it[Floaters.priority] = Priority.valueOf(priority)
                it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateFloaterCaches(userId)
        return Unit.right()
    }

    override suspend fun reorder(userId: String, floaterId: String, newOrder: Int): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.update({ (Floaters.id eq floaterId) and (Floaters.userID eq userId) }) {
                it[Floaters.order] = newOrder
                it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateFloaterCaches(userId)
        return Unit.right()
    }

    private fun ResultRow.toFloaterResponse(): FloaterResponse = FloaterResponse(
        id = this[Floaters.id],
        title = this[Floaters.title],
        description = fieldEncryption.decryptIfEncrypted(this[Floaters.description]),
        createdAt = this[Floaters.createdAt].toString(),
        updatedAt = this[Floaters.updatedAt].toString(),
        userID = this[Floaters.userID],
        pinned = this[Floaters.pinned],
        order = this[Floaters.order],
        priority = this[Floaters.priority].name,
        completed = this[Floaters.completed],
        listID = this[Floaters.listID],
    )

    private fun floaterListExists(userId: String, listId: String): Boolean {
        return FloaterLists.selectAll().where {
            (FloaterLists.id eq listId) and (FloaterLists.userID eq userId)
        }.limit(1).any()
    }
}
