package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.tables.ListShares
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.models.response.ListResponse
import com.ohmz.tday.models.response.ListTodoResponse
import com.ohmz.tday.shared.model.ShareRole
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.time.ZoneOffset

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

class ListServiceImpl(
    private val cache: CacheService,
    private val shareService: ListShareService,
    private val publisher: RealtimePublisher,
) : ListService {
    override suspend fun getAll(userId: String): Either<AppError, List<ListResponse>> {
        val lists = newSuspendedTransaction(Dispatchers.IO) {
            // Lists shared with me, keyed by list id → my role.
            val myShareRoles = ListShares.selectAll().where { ListShares.userID eq userId }
                .associate { it[ListShares.listID] to it[ListShares.role] }

            val rows = Lists.selectAll().where {
                if (myShareRoles.isEmpty()) {
                    Lists.userID eq userId
                } else {
                    (Lists.userID eq userId) or (Lists.id inList myShareRoles.keys.toList())
                }
            }.orderBy(Lists.createdAt, SortOrder.DESC).toList()

            val listIds = rows.map { it[Lists.id] }
            val memberCounts: Map<String, Int> = if (listIds.isEmpty()) {
                emptyMap()
            } else {
                ListShares.selectAll().where { ListShares.listID inList listIds }
                    .groupingBy { it[ListShares.listID] }
                    .eachCount()
            }
            val ownerIds = rows.map { it[Lists.userID] }.filter { it != userId }.distinct()
            val ownerUsernames: Map<String, String> = if (ownerIds.isEmpty()) {
                emptyMap()
            } else {
                Users.selectAll().where { Users.id inList ownerIds }
                    .associate { it[Users.id] to it[Users.username] }
            }

            rows.map { row ->
                val listId = row[Lists.id]
                val ownerId = row[Lists.userID]
                val isOwner = ownerId == userId
                val memberCount = memberCounts[listId] ?: 0
                row.toListResponse().copy(
                    myRole = if (isOwner) ShareRole.OWNER.name else (myShareRoles[listId] ?: ShareRole.VIEWER.name),
                    isShared = memberCount > 0,
                    memberCount = memberCount,
                    ownerUsername = if (isOwner) null else ownerUsernames[ownerId],
                )
            }
        }
        return lists.right()
    }

    override suspend fun getById(userId: String, listId: String): Either<AppError, ListResponse> {
        val role = shareService.accessFor(userId, listId, ListType.SCHEDULED)
            ?: return AppError.NotFound("list not found").left()
        val list = newSuspendedTransaction(Dispatchers.IO) {
            val row = Lists.selectAll().where { Lists.id eq listId }.firstOrNull()
                ?: return@newSuspendedTransaction null
            val memberCount = ListShares.selectAll().where { ListShares.listID eq listId }.count().toInt()
            val ownerId = row[Lists.userID]
            val ownerUsername = if (ownerId == userId) {
                null
            } else {
                Users.selectAll().where { Users.id eq ownerId }.firstOrNull()?.get(Users.username)
            }
            row.toListResponse().copy(
                myRole = role.name,
                isShared = memberCount > 0,
                memberCount = memberCount,
                ownerUsername = ownerUsername,
            )
        }
        return list?.right() ?: Either.Left(AppError.NotFound("list not found"))
    }

    override suspend fun getTodosForList(userId: String, listId: String): Either<AppError, List<ListTodoResponse>> {
        shareService.accessFor(userId, listId, ListType.SCHEDULED)
            ?: return AppError.NotFound("list not found").left()
        // Access verified above; members see every collaborator's todos in the
        // list, so this query is deliberately not filtered by userID.
        val todos = newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().where {
                (Todos.listID eq listId) and (Todos.completed eq false)
            }.orderBy(Todos.order, SortOrder.ASC).map { row ->
                ListTodoResponse(
                    id = row[Todos.id],
                    title = row[Todos.title],
                    priority = row[Todos.priority].name,
                    due = row[Todos.due]?.toString(),
                    completed = row[Todos.completed],
                    order = row[Todos.order],
                )
            }
        }
        return todos.right()
    }

    override suspend fun create(userId: String, name: String, color: String?, iconKey: String?): Either<AppError, ListResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
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
        publisher.publishToCollaborators(userId, DomainEvent.ListChanged(id))
        return ListResponse(
            id = id, name = name, color = color, iconKey = iconKey,
            userID = userId, createdAt = now.toString(), updatedAt = now.toString(),
            myRole = ShareRole.OWNER.name,
        ).right()
    }

    override suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?): Either<AppError, Unit> {
        when (shareService.accessFor(userId, id, ListType.SCHEDULED)) {
            null -> return AppError.NotFound("list not found").left()
            ShareRole.OWNER -> Unit
            else -> return AppError.Forbidden("only the list owner can update the list").left()
        }
        newSuspendedTransaction(Dispatchers.IO) {
            Lists.update({ (Lists.id eq id) and (Lists.userID eq userId) }) {
                name?.let { n -> it[Lists.name] = n }
                color?.let { c -> it[Lists.color] = ListColor.valueOf(c) }
                iconKey?.let { k -> it[Lists.iconKey] = k }
                it[Lists.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateListCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.ListChanged(id))
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
        deleteMany(userId, listOf(id)).map { it.size }

    override suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> {
        val normalizedIds = ids.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedIds.isEmpty()) {
            return emptyList<String>().right()
        }

        // Snapshot the fanout set before the share rows cascade away with the
        // lists, so members still hear about the deletion.
        val recipients = buildSet {
            add(userId)
            addAll(shareService.collaboratorIdsFor(userId))
        }

        val deletedIds = newSuspendedTransaction(Dispatchers.IO) {
            // Owner-only: deletion (and its cascades below) covers every
            // member's todos, so a non-owner must never get this far.
            val existingIds = Lists
                .select(Lists.id)
                .where { (Lists.userID eq userId) and (Lists.id inList normalizedIds) }
                .map { it[Lists.id] }

            if (existingIds.isEmpty()) {
                return@newSuspendedTransaction emptyList()
            }

            // List-scoped cascades are intentionally NOT filtered by userID:
            // shared lists hold todos and completion history from every member,
            // and deleting the list removes all of it.
            val todoIds = Todos
                .select(Todos.id)
                .where { Todos.listID inList existingIds }
                .map { it[Todos.id] }
            if (todoIds.isNotEmpty()) {
                CompletedTodos.deleteWhere {
                    SqlExpressionBuilder.run {
                        (CompletedTodos.listID inList existingIds) or (CompletedTodos.originalTodoID inList todoIds)
                    }
                }
                TodoInstances.deleteWhere {
                    SqlExpressionBuilder.run {
                        TodoInstances.todoId inList todoIds
                    }
                }
                Todos.deleteWhere {
                    SqlExpressionBuilder.run {
                        Todos.id inList todoIds
                    }
                }
            } else {
                CompletedTodos.deleteWhere {
                    SqlExpressionBuilder.run {
                        CompletedTodos.listID inList existingIds
                    }
                }
            }

            ListShares.deleteWhere {
                SqlExpressionBuilder.run {
                    ListShares.listID inList existingIds
                }
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
            cache.invalidateTodoCaches(userId)
            cache.invalidateCompletedCaches(userId)
            publisher.publishTo(userId, recipients, DomainEvent.ListChanged())
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
