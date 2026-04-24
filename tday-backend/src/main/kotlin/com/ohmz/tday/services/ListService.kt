package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import arrow.core.raise.either
import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.ListResponse
import com.ohmz.tday.models.response.ListTodoResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

interface ListService {
    suspend fun getAll(userId: String): Either<AppError, List<ListResponse>>
    suspend fun getById(userId: String, listId: String): Either<AppError, ListResponse>
    suspend fun getTodosForList(userId: String, listId: String): Either<AppError, List<ListTodoResponse>>
    suspend fun create(userId: String, name: String, color: String?, iconKey: String?): Either<AppError, ListResponse>
    suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?): Either<AppError, Unit>
    suspend fun delete(userId: String, id: String): Either<AppError, Int>
    suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> = either {
        ids.distinct().filter { it.isNotBlank() }.mapNotNull { id ->
            val deletedCount = delete(userId, id).bind()
            id.takeIf { deletedCount > 0 }
        }
    }
}

class ListServiceImpl(private val cache: CacheService) : ListService {
    override suspend fun getAll(userId: String): Either<AppError, List<ListResponse>> {
        val lists = newSuspendedTransaction(Dispatchers.IO) {
            Lists.selectAll().where { Lists.userID eq userId }
                .orderBy(Lists.createdAt, SortOrder.DESC)
                .map { it.toListResponse() }
        }
        return lists.right()
    }

    override suspend fun getById(userId: String, listId: String): Either<AppError, ListResponse> {
        val list = newSuspendedTransaction(Dispatchers.IO) {
            Lists.selectAll().where { (Lists.id eq listId) and (Lists.userID eq userId) }
                .firstOrNull()?.toListResponse()
        }
        return list?.right() ?: Either.Left(AppError.NotFound("list not found"))
    }

    override suspend fun getTodosForList(userId: String, listId: String): Either<AppError, List<ListTodoResponse>> {
        val todos = newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().where {
                (Todos.userID eq userId) and (Todos.listID eq listId) and (Todos.completed eq false)
            }.orderBy(Todos.order, SortOrder.ASC).map { row ->
                ListTodoResponse(
                    id = row[Todos.id],
                    title = row[Todos.title],
                    priority = row[Todos.priority].name,
                    due = row[Todos.due].toString(),
                    completed = row[Todos.completed],
                    order = row[Todos.order],
                )
            }
        }
        return todos.right()
    }

    override suspend fun create(userId: String, name: String, color: String?, iconKey: String?): Either<AppError, ListResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now()
        newSuspendedTransaction(Dispatchers.IO) {
            Lists.insert {
                it[Lists.id] = id
                it[Lists.name] = name
                it[Lists.color] = color?.let { c -> ListColor.valueOf(c) }
                it[Lists.iconKey] = iconKey
                it[Lists.userID] = userId
                it[Lists.createdAt] = now
                it[Lists.updatedAt] = now
            }
        }
        cache.invalidateListCaches(userId)
        return ListResponse(
            id = id, name = name, color = color, iconKey = iconKey,
            userID = userId, createdAt = now.toString(), updatedAt = now.toString(),
        ).right()
    }

    override suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?): Either<AppError, Unit> {
        newSuspendedTransaction(Dispatchers.IO) {
            Lists.update({ (Lists.id eq id) and (Lists.userID eq userId) }) {
                name?.let { n -> it[Lists.name] = n }
                color?.let { c -> it[Lists.color] = ListColor.valueOf(c) }
                iconKey?.let { k -> it[Lists.iconKey] = k }
                it[Lists.updatedAt] = LocalDateTime.now()
            }
        }
        cache.invalidateListCaches(userId)
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
        deleteMany(userId, listOf(id)).map { it.size }

    override suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> {
        val normalizedIds = ids.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedIds.isEmpty()) {
            return emptyList<String>().right()
        }

        val deletedIds = newSuspendedTransaction(Dispatchers.IO) {
            val existingIds = Lists
                .select(Lists.id)
                .where { (Lists.userID eq userId) and (Lists.id inList normalizedIds) }
                .map { it[Lists.id] }

            if (existingIds.isEmpty()) {
                return@newSuspendedTransaction emptyList()
            }

            Todos.update({ (Todos.userID eq userId) and (Todos.listID inList existingIds) }) {
                it[Todos.listID] = null
            }
            Lists.deleteWhere {
                SqlExpressionBuilder.run {
                    (Lists.userID eq userId) and (Lists.id inList existingIds)
                }
            }
            existingIds
        }

        if (deletedIds.isNotEmpty()) {
            cache.invalidateListCaches(userId)
        }

        return deletedIds.right()
    }

    private fun ResultRow.toListResponse(): ListResponse = ListResponse(
        id = this[Lists.id],
        name = this[Lists.name],
        color = this[Lists.color]?.name,
        iconKey = this[Lists.iconKey],
        userID = this[Lists.userID],
        createdAt = this[Lists.createdAt].toString(),
        updatedAt = this[Lists.updatedAt].toString(),
    )
}
