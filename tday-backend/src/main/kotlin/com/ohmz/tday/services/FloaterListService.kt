package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import arrow.core.raise.either
import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.FloaterListResponse
import com.ohmz.tday.models.response.FloaterListTodoResponse
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

interface FloaterListService {
    suspend fun getAll(userId: String): Either<AppError, List<FloaterListResponse>>
    suspend fun getById(userId: String, listId: String): Either<AppError, FloaterListResponse>
    suspend fun getFloatersForList(userId: String, listId: String): Either<AppError, List<FloaterListTodoResponse>>
    suspend fun create(userId: String, name: String, color: String?, iconKey: String?): Either<AppError, FloaterListResponse>
    suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?): Either<AppError, Unit>
    suspend fun delete(userId: String, id: String): Either<AppError, Int>
    suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> = either {
        ids.distinct().filter { it.isNotBlank() }.mapNotNull { id ->
            val deletedCount = delete(userId, id).bind()
            id.takeIf { deletedCount > 0 }
        }
    }
}

class FloaterListServiceImpl(private val cache: CacheService) : FloaterListService {
    override suspend fun getAll(userId: String): Either<AppError, List<FloaterListResponse>> {
        val lists = newSuspendedTransaction(Dispatchers.IO) {
            val counts = Floaters
                .select(Floaters.listID)
                .where { (Floaters.userID eq userId) and (Floaters.completed eq false) }
                .mapNotNull { it[Floaters.listID] }
                .groupingBy { it }
                .eachCount()

            FloaterLists.selectAll().where { FloaterLists.userID eq userId }
                .orderBy(FloaterLists.createdAt, SortOrder.DESC)
                .map { it.toFloaterListResponse(counts[it[FloaterLists.id]] ?: 0) }
        }
        return lists.right()
    }

    override suspend fun getById(userId: String, listId: String): Either<AppError, FloaterListResponse> {
        val list = newSuspendedTransaction(Dispatchers.IO) {
            val count = Floaters.selectAll().where {
                (Floaters.userID eq userId) and
                    (Floaters.listID eq listId) and
                    (Floaters.completed eq false)
            }.count().toInt()

            FloaterLists.selectAll().where { (FloaterLists.id eq listId) and (FloaterLists.userID eq userId) }
                .firstOrNull()?.toFloaterListResponse(count)
        }
        return list?.right() ?: Either.Left(AppError.NotFound("floater list not found"))
    }

    override suspend fun getFloatersForList(userId: String, listId: String): Either<AppError, List<FloaterListTodoResponse>> {
        val floaters = newSuspendedTransaction(Dispatchers.IO) {
            Floaters.selectAll().where {
                (Floaters.userID eq userId) and (Floaters.listID eq listId) and (Floaters.completed eq false)
            }.orderBy(Floaters.priority to SortOrder.DESC, Floaters.pinned to SortOrder.DESC, Floaters.order to SortOrder.ASC)
                .map { row ->
                    FloaterListTodoResponse(
                        id = row[Floaters.id],
                        title = row[Floaters.title],
                        priority = row[Floaters.priority].name,
                        completed = row[Floaters.completed],
                        order = row[Floaters.order],
                    )
                }
        }
        return floaters.right()
    }

    override suspend fun create(userId: String, name: String, color: String?, iconKey: String?): Either<AppError, FloaterListResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now()
        newSuspendedTransaction(Dispatchers.IO) {
            FloaterLists.insert {
                it[FloaterLists.id] = id
                it[FloaterLists.name] = name
                it[FloaterLists.color] = color?.let { c -> ListColor.valueOf(c) }
                it[FloaterLists.iconKey] = iconKey
                it[FloaterLists.userID] = userId
                it[FloaterLists.createdAt] = now
                it[FloaterLists.updatedAt] = now
            }
        }
        cache.invalidateFloaterListCaches(userId)
        return FloaterListResponse(
            id = id,
            name = name,
            color = color,
            iconKey = iconKey,
            userID = userId,
            createdAt = now.toString(),
            updatedAt = now.toString(),
        ).right()
    }

    override suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            FloaterLists.update({ (FloaterLists.id eq id) and (FloaterLists.userID eq userId) }) {
                name?.let { n -> it[FloaterLists.name] = n }
                color?.let { c -> it[FloaterLists.color] = ListColor.valueOf(c) }
                iconKey?.let { k -> it[FloaterLists.iconKey] = k }
                it[FloaterLists.updatedAt] = LocalDateTime.now()
            }
        }
        cache.invalidateFloaterListCaches(userId)
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
        deleteMany(userId, listOf(id)).map { it.size }

    override suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> {
        val normalizedIds = ids.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedIds.isEmpty()) return emptyList<String>().right()

        val deletedIds = newSuspendedTransaction(Dispatchers.IO) {
            val existingIds = FloaterLists
                .select(FloaterLists.id)
                .where { (FloaterLists.userID eq userId) and (FloaterLists.id inList normalizedIds) }
                .map { it[FloaterLists.id] }

            if (existingIds.isEmpty()) return@newSuspendedTransaction emptyList()

            val floaterIds = Floaters
                .select(Floaters.id)
                .where { (Floaters.userID eq userId) and (Floaters.listID inList existingIds) }
                .map { it[Floaters.id] }

            if (floaterIds.isNotEmpty()) {
                CompletedFloaters.deleteWhere {
                    SqlExpressionBuilder.run {
                        (CompletedFloaters.userID eq userId) and
                            ((CompletedFloaters.listID inList existingIds) or (CompletedFloaters.originalFloaterID inList floaterIds))
                    }
                }
                Floaters.deleteWhere {
                    SqlExpressionBuilder.run {
                        (Floaters.userID eq userId) and (Floaters.id inList floaterIds)
                    }
                }
            } else {
                CompletedFloaters.deleteWhere {
                    SqlExpressionBuilder.run {
                        (CompletedFloaters.userID eq userId) and (CompletedFloaters.listID inList existingIds)
                    }
                }
            }

            FloaterLists.deleteWhere {
                SqlExpressionBuilder.run {
                    (FloaterLists.userID eq userId) and (FloaterLists.id inList existingIds)
                }
            }
            existingIds
        }

        if (deletedIds.isNotEmpty()) {
            cache.invalidateFloaterListCaches(userId)
        }

        return deletedIds.right()
    }

    private fun ResultRow.toFloaterListResponse(todoCountOverride: Int = 0): FloaterListResponse = FloaterListResponse(
        id = this[FloaterLists.id],
        name = this[FloaterLists.name],
        color = this[FloaterLists.color]?.name,
        iconKey = this[FloaterLists.iconKey],
        userID = this[FloaterLists.userID],
        todoCount = todoCountOverride,
        createdAt = this[FloaterLists.createdAt].toString(),
        updatedAt = this[FloaterLists.updatedAt].toString(),
    )
}
