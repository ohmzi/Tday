package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterListShares
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.models.response.FloaterListResponse
import com.ohmz.tday.models.response.FloaterListTodoResponse
import com.ohmz.tday.shared.model.ShareRole
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.time.ZoneOffset

interface FloaterListService {
    suspend fun getAll(userId: String): Either<AppError, List<FloaterListResponse>>
    suspend fun getById(userId: String, listId: String): Either<AppError, FloaterListResponse>
    suspend fun getFloatersForList(userId: String, listId: String): Either<AppError, List<FloaterListTodoResponse>>
    suspend fun create(userId: String, name: String, color: String?, iconKey: String?, reusable: Boolean = false): Either<AppError, FloaterListResponse>
    suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?, reusable: Boolean? = null): Either<AppError, Unit>
    suspend fun resetFloaters(userId: String, listId: String): Either<AppError, Int>
    suspend fun delete(userId: String, id: String): Either<AppError, Int>
    suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> = either {
        ids.distinct().filter { it.isNotBlank() }.mapNotNull { id ->
            val deletedCount = delete(userId, id).bind()
            id.takeIf { deletedCount > 0 }
        }
    }
}

class FloaterListServiceImpl(
    private val cache: CacheService,
    private val shareService: ListShareService,
    private val publisher: RealtimePublisher,
) : FloaterListService {
    override suspend fun getAll(userId: String): Either<AppError, List<FloaterListResponse>> {
        val lists = newSuspendedTransaction(Dispatchers.IO) {
            // Lists shared with me, keyed by list id → my role.
            val myShareRoles = FloaterListShares.selectAll().where { FloaterListShares.userID eq userId }
                .associate { it[FloaterListShares.listID] to it[FloaterListShares.role] }

            val rows = FloaterLists.selectAll().where {
                if (myShareRoles.isEmpty()) {
                    FloaterLists.userID eq userId
                } else {
                    (FloaterLists.userID eq userId) or (FloaterLists.id inList myShareRoles.keys.toList())
                }
            }.orderBy(FloaterLists.createdAt, SortOrder.DESC).toList()

            val listIds = rows.map { it[FloaterLists.id] }
            // Counts cover every member's floaters in shared lists, so no userID
            // filter — the visible-list filter is the access boundary.
            val counts: Map<String, Int> = if (listIds.isEmpty()) {
                emptyMap()
            } else {
                Floaters
                    .select(Floaters.listID)
                    .where { (Floaters.listID inList listIds) and (Floaters.completed eq false) }
                    .mapNotNull { it[Floaters.listID] }
                    .groupingBy { it }
                    .eachCount()
            }
            val memberCounts: Map<String, Int> = if (listIds.isEmpty()) {
                emptyMap()
            } else {
                FloaterListShares.selectAll().where { FloaterListShares.listID inList listIds }
                    .groupingBy { it[FloaterListShares.listID] }
                    .eachCount()
            }
            val ownerIds = rows.map { it[FloaterLists.userID] }.filter { it != userId }.distinct()
            val ownerUsernames: Map<String, String> = if (ownerIds.isEmpty()) {
                emptyMap()
            } else {
                Users.selectAll().where { Users.id inList ownerIds }
                    .associate { it[Users.id] to it[Users.username] }
            }

            rows.map { row ->
                val listId = row[FloaterLists.id]
                val ownerId = row[FloaterLists.userID]
                val isOwner = ownerId == userId
                val memberCount = memberCounts[listId] ?: 0
                row.toFloaterListResponse(counts[listId] ?: 0).copy(
                    myRole = if (isOwner) ShareRole.OWNER.name else (myShareRoles[listId] ?: ShareRole.VIEWER.name),
                    isShared = memberCount > 0,
                    memberCount = memberCount,
                    ownerUsername = if (isOwner) null else ownerUsernames[ownerId],
                )
            }
        }
        return lists.right()
    }

    override suspend fun getById(userId: String, listId: String): Either<AppError, FloaterListResponse> {
        val role = shareService.accessFor(userId, listId, ListType.FLOATER)
            ?: return AppError.NotFound("floater list not found").left()
        val list = newSuspendedTransaction(Dispatchers.IO) {
            val row = FloaterLists.selectAll().where { FloaterLists.id eq listId }.firstOrNull()
                ?: return@newSuspendedTransaction null
            val count = Floaters.selectAll().where {
                (Floaters.listID eq listId) and (Floaters.completed eq false)
            }.count().toInt()
            val memberCount = FloaterListShares.selectAll().where { FloaterListShares.listID eq listId }.count().toInt()
            val ownerId = row[FloaterLists.userID]
            val ownerUsername = if (ownerId == userId) {
                null
            } else {
                Users.selectAll().where { Users.id eq ownerId }.firstOrNull()?.get(Users.username)
            }
            row.toFloaterListResponse(count).copy(
                myRole = role.name,
                isShared = memberCount > 0,
                memberCount = memberCount,
                ownerUsername = ownerUsername,
            )
        }
        return list?.right() ?: Either.Left(AppError.NotFound("floater list not found"))
    }

    override suspend fun getFloatersForList(userId: String, listId: String): Either<AppError, List<FloaterListTodoResponse>> {
        shareService.accessFor(userId, listId, ListType.FLOATER)
            ?: return AppError.NotFound("floater list not found").left()
        // Access verified above; members see every collaborator's floaters in
        // the list, so this query is deliberately not filtered by userID.
        val floaters = newSuspendedTransaction(Dispatchers.IO) {
            Floaters.selectAll().where {
                (Floaters.listID eq listId) and (Floaters.completed eq false)
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

    override suspend fun create(userId: String, name: String, color: String?, iconKey: String?, reusable: Boolean): Either<AppError, FloaterListResponse> {
        val id = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        newSuspendedTransaction(Dispatchers.IO) {
            FloaterLists.insert {
                it[FloaterLists.id] = id
                it[FloaterLists.name] = name
                it[FloaterLists.color] = color?.let { c -> ListColor.valueOf(c) }
                it[FloaterLists.iconKey] = iconKey
                it[FloaterLists.userID] = userId
                it[FloaterLists.reusable] = reusable
                it[FloaterLists.createdAt] = now
                it[FloaterLists.updatedAt] = now
            }
        }
        cache.invalidateFloaterListCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterListChanged(id))
        return FloaterListResponse(
            id = id,
            name = name,
            color = color,
            iconKey = iconKey,
            userID = userId,
            reusable = reusable,
            createdAt = now.toString(),
            updatedAt = now.toString(),
            myRole = ShareRole.OWNER.name,
        ).right()
    }

    override suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?, reusable: Boolean?): Either<AppError, Unit> {
        when (shareService.accessFor(userId, id, ListType.FLOATER)) {
            null -> return AppError.NotFound("floater list not found").left()
            ShareRole.OWNER -> Unit
            else -> return AppError.Forbidden("only the list owner can update the list").left()
        }
        newSuspendedTransaction(Dispatchers.IO) {
            FloaterLists.update({ (FloaterLists.id eq id) and (FloaterLists.userID eq userId) }) {
                name?.let { n -> it[FloaterLists.name] = n }
                color?.let { c -> it[FloaterLists.color] = ListColor.valueOf(c) }
                iconKey?.let { k -> it[FloaterLists.iconKey] = k }
                reusable?.let { r -> it[FloaterLists.reusable] = r }
                it[FloaterLists.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateFloaterListCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterListChanged(id))
        return Unit.right()
    }

    /** Reset a reusable list: un-complete every floater in it, in one transaction. */
    override suspend fun resetFloaters(userId: String, listId: String): Either<AppError, Int> {
        when (shareService.accessFor(userId, listId, ListType.FLOATER)) {
            null -> return AppError.NotFound("floater list not found").left()
            ShareRole.VIEWER -> return AppError.Forbidden("viewers cannot reset a list").left()
            else -> Unit
        }
        val count = newSuspendedTransaction(Dispatchers.IO) {
            val floaterIds = Floaters.selectAll()
                .where { (Floaters.listID eq listId) and (Floaters.completed eq true) }
                .map { it[Floaters.id] }
            if (floaterIds.isEmpty()) return@newSuspendedTransaction 0
            Floaters.update({ Floaters.listID eq listId }) {
                it[Floaters.completed] = false
                it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
            CompletedFloaters.deleteWhere {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.listID eq listId)
            }
            floaterIds.size
        }
        cache.invalidateFloaterCaches(userId)
        cache.invalidateFloaterListCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged(listId))
        publisher.publishToCollaborators(userId, DomainEvent.CompletedChanged())
        return count.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
        deleteMany(userId, listOf(id)).map { it.size }

    override suspend fun deleteMany(userId: String, ids: List<String>): Either<AppError, List<String>> {
        val normalizedIds = ids.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedIds.isEmpty()) return emptyList<String>().right()

        // Snapshot the fanout set before the share rows cascade away with the
        // lists, so members still hear about the deletion.
        val recipients = buildSet {
            add(userId)
            addAll(shareService.collaboratorIdsFor(userId))
        }

        val deletedIds = newSuspendedTransaction(Dispatchers.IO) {
            // Owner-only: deletion (and its cascades below) covers every
            // member's floaters, so a non-owner must never get this far.
            val existingIds = FloaterLists
                .select(FloaterLists.id)
                .where { (FloaterLists.userID eq userId) and (FloaterLists.id inList normalizedIds) }
                .map { it[FloaterLists.id] }

            if (existingIds.isEmpty()) return@newSuspendedTransaction emptyList()

            // List-scoped cascades are intentionally NOT filtered by userID:
            // shared lists hold floaters and completion history from every
            // member, and deleting the list removes all of it.
            val floaterIds = Floaters
                .select(Floaters.id)
                .where { Floaters.listID inList existingIds }
                .map { it[Floaters.id] }

            if (floaterIds.isNotEmpty()) {
                CompletedFloaters.deleteWhere {
                    SqlExpressionBuilder.run {
                        (CompletedFloaters.listID inList existingIds) or (CompletedFloaters.originalFloaterID inList floaterIds)
                    }
                }
                Floaters.deleteWhere {
                    SqlExpressionBuilder.run {
                        Floaters.id inList floaterIds
                    }
                }
            } else {
                CompletedFloaters.deleteWhere {
                    SqlExpressionBuilder.run {
                        CompletedFloaters.listID inList existingIds
                    }
                }
            }

            FloaterListShares.deleteWhere {
                SqlExpressionBuilder.run {
                    FloaterListShares.listID inList existingIds
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
            publisher.publishTo(userId, recipients, DomainEvent.FloaterListChanged())
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
        reusable = this[FloaterLists.reusable],
        createdAt = this[FloaterLists.createdAt].toString(),
        updatedAt = this[FloaterLists.updatedAt].toString(),
    )
}
