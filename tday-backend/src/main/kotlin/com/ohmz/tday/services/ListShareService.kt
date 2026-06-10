package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.db.tables.FloaterListShares
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.ListShares
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.db.enums.ApprovalStatus
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.shared.model.ListMemberDto
import com.ohmz.tday.shared.model.ListMembersResponse
import com.ohmz.tday.shared.model.ShareRole
import com.ohmz.tday.shared.model.UserSearchResultDto
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class ListType { SCHEDULED, FLOATER }

/**
 * Single source of truth for list-sharing access decisions. The owner of a
 * list is its `userID` column; share rows grant other users EDITOR or VIEWER.
 */
interface ListShareService {
    /** OWNER if the list belongs to the user, the share-row role if shared with them, else null. */
    suspend fun accessFor(userId: String, listId: String, type: ListType): ShareRole?

    /** Ids of lists shared WITH the user (not owned). [editorOnly] keeps only EDITOR rows. */
    suspend fun sharedListIdsFor(userId: String, type: ListType, editorOnly: Boolean = false): List<String>

    /** True when the user is the owner or an EDITOR member of the list. */
    suspend fun canEditList(userId: String, listId: String, type: ListType): Boolean

    /**
     * Every user connected to this user through a share (members of lists they
     * own, plus owners and co-members of lists shared with them). Used as the
     * realtime fanout set; cached briefly.
     */
    suspend fun collaboratorIdsFor(userId: String): Set<String>

    suspend fun members(requesterId: String, listId: String, type: ListType): Either<AppError, ListMembersResponse>
    suspend fun addMember(requesterId: String, listId: String, type: ListType, username: String, role: String): Either<AppError, ListMemberDto>
    suspend fun updateRole(requesterId: String, listId: String, type: ListType, memberUserId: String, role: String): Either<AppError, Unit>
    suspend fun removeMember(requesterId: String, listId: String, type: ListType, memberUserId: String): Either<AppError, Unit>
    suspend fun leave(userId: String, listId: String, type: ListType): Either<AppError, Unit>
    suspend fun searchUsers(requesterId: String, query: String): Either<AppError, List<UserSearchResultDto>>
}

class ListShareServiceImpl(
    private val cache: CacheService,
    private val realtime: RealtimeService,
) : ListShareService {

    override suspend fun accessFor(userId: String, listId: String, type: ListType): ShareRole? =
        newSuspendedTransaction(Dispatchers.IO) { accessForInTx(userId, listId, type) }

    override suspend fun sharedListIdsFor(userId: String, type: ListType, editorOnly: Boolean): List<String> =
        newSuspendedTransaction(Dispatchers.IO) {
            when (type) {
                ListType.SCHEDULED -> ListShares.selectAll().where { ListShares.userID eq userId }
                    .filter { !editorOnly || it[ListShares.role] == ShareRole.EDITOR.name }
                    .map { it[ListShares.listID] }
                ListType.FLOATER -> FloaterListShares.selectAll().where { FloaterListShares.userID eq userId }
                    .filter { !editorOnly || it[FloaterListShares.role] == ShareRole.EDITOR.name }
                    .map { it[FloaterListShares.listID] }
            }
        }

    override suspend fun canEditList(userId: String, listId: String, type: ListType): Boolean =
        accessFor(userId, listId, type)?.canEdit == true

    override suspend fun collaboratorIdsFor(userId: String): Set<String> {
        val cacheKey = cache.cacheKey(userId, COLLABORATORS_ENDPOINT)
        cache.get<Set<String>>(cacheKey)?.let { return it }

        val collaborators = newSuspendedTransaction(Dispatchers.IO) {
            val result = mutableSetOf<String>()

            val ownedScheduled = Lists.selectAll().where { Lists.userID eq userId }.map { it[Lists.id] }
            if (ownedScheduled.isNotEmpty()) {
                ListShares.selectAll().where { ListShares.listID inList ownedScheduled }
                    .forEach { result += it[ListShares.userID] }
            }
            val ownedFloater = FloaterLists.selectAll().where { FloaterLists.userID eq userId }.map { it[FloaterLists.id] }
            if (ownedFloater.isNotEmpty()) {
                FloaterListShares.selectAll().where { FloaterListShares.listID inList ownedFloater }
                    .forEach { result += it[FloaterListShares.userID] }
            }

            val memberScheduled = ListShares.selectAll().where { ListShares.userID eq userId }.map { it[ListShares.listID] }
            if (memberScheduled.isNotEmpty()) {
                Lists.selectAll().where { Lists.id inList memberScheduled }.forEach { result += it[Lists.userID] }
                ListShares.selectAll().where { ListShares.listID inList memberScheduled }
                    .forEach { result += it[ListShares.userID] }
            }
            val memberFloater = FloaterListShares.selectAll().where { FloaterListShares.userID eq userId }.map { it[FloaterListShares.listID] }
            if (memberFloater.isNotEmpty()) {
                FloaterLists.selectAll().where { FloaterLists.id inList memberFloater }.forEach { result += it[FloaterLists.userID] }
                FloaterListShares.selectAll().where { FloaterListShares.listID inList memberFloater }
                    .forEach { result += it[FloaterListShares.userID] }
            }

            result -= userId
            result.toSet()
        }
        cache.set(cacheKey, collaborators, COLLABORATORS_TTL_MS)
        return collaborators
    }

    override suspend fun members(requesterId: String, listId: String, type: ListType): Either<AppError, ListMembersResponse> {
        val response = newSuspendedTransaction(Dispatchers.IO) {
            if (accessForInTx(requesterId, listId, type) == null) return@newSuspendedTransaction null
            val ownerId = ownerOfInTx(listId, type) ?: return@newSuspendedTransaction null
            val ownerRow = Users.selectAll().where { Users.id eq ownerId }.firstOrNull()
                ?: return@newSuspendedTransaction null
            ListMembersResponse(
                owner = ListMemberDto(
                    userId = ownerId,
                    username = ownerRow[Users.username],
                    name = ownerRow[Users.name],
                    role = ShareRole.OWNER.name,
                ),
                members = memberRowsInTx(listId, type),
            )
        }
        return response?.right() ?: AppError.NotFound("list not found").left()
    }

    override suspend fun addMember(
        requesterId: String, listId: String, type: ListType, username: String, role: String,
    ): Either<AppError, ListMemberDto> {
        val parsedRole = parseMemberRole(role) ?: return AppError.BadRequest("role must be EDITOR or VIEWER", "role").left()
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty()) return AppError.BadRequest("username is required", "username").left()

        val result = newSuspendedTransaction(Dispatchers.IO) {
            val access = accessForInTx(requesterId, listId, type)
                ?: return@newSuspendedTransaction AppError.NotFound("list not found").left()
            if (access != ShareRole.OWNER) {
                return@newSuspendedTransaction AppError.Forbidden("only the list owner can manage members").left()
            }
            val targetRow = Users.selectAll().where {
                (Users.username.lowerCase() eq normalizedUsername.lowercase()) and
                    (Users.approvalStatus eq ApprovalStatus.APPROVED)
            }.firstOrNull() ?: return@newSuspendedTransaction AppError.NotFound("user not found").left()
            val targetId = targetRow[Users.id]
            if (targetId == requesterId) {
                return@newSuspendedTransaction AppError.BadRequest("you already own this list", "username").left()
            }

            val now = LocalDateTime.now(ZoneOffset.UTC)
            when (type) {
                ListType.SCHEDULED -> {
                    val existing = ListShares.selectAll().where {
                        (ListShares.listID eq listId) and (ListShares.userID eq targetId)
                    }.firstOrNull()
                    if (existing != null) {
                        ListShares.update({ (ListShares.listID eq listId) and (ListShares.userID eq targetId) }) {
                            it[ListShares.role] = parsedRole.name
                            it[ListShares.updatedAt] = now
                        }
                    } else {
                        ListShares.insert {
                            it[ListShares.id] = CuidGenerator.newCuid()
                            it[ListShares.listID] = listId
                            it[ListShares.userID] = targetId
                            it[ListShares.role] = parsedRole.name
                            it[ListShares.createdAt] = now
                            it[ListShares.updatedAt] = now
                        }
                    }
                }
                ListType.FLOATER -> {
                    val existing = FloaterListShares.selectAll().where {
                        (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq targetId)
                    }.firstOrNull()
                    if (existing != null) {
                        FloaterListShares.update({ (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq targetId) }) {
                            it[FloaterListShares.role] = parsedRole.name
                            it[FloaterListShares.updatedAt] = now
                        }
                    } else {
                        FloaterListShares.insert {
                            it[FloaterListShares.id] = CuidGenerator.newCuid()
                            it[FloaterListShares.listID] = listId
                            it[FloaterListShares.userID] = targetId
                            it[FloaterListShares.role] = parsedRole.name
                            it[FloaterListShares.createdAt] = now
                            it[FloaterListShares.updatedAt] = now
                        }
                    }
                }
            }
            ListMemberDto(
                userId = targetId,
                username = targetRow[Users.username],
                name = targetRow[Users.name],
                role = parsedRole.name,
                addedAt = now.toString(),
            ).right()
        }

        result.onRight { afterMembershipChange(requesterId, listId, type) }
        return result
    }

    override suspend fun updateRole(
        requesterId: String, listId: String, type: ListType, memberUserId: String, role: String,
    ): Either<AppError, Unit> {
        val parsedRole = parseMemberRole(role) ?: return AppError.BadRequest("role must be EDITOR or VIEWER", "role").left()

        val result = newSuspendedTransaction(Dispatchers.IO) {
            val access = accessForInTx(requesterId, listId, type)
                ?: return@newSuspendedTransaction AppError.NotFound("list not found").left()
            if (access != ShareRole.OWNER) {
                return@newSuspendedTransaction AppError.Forbidden("only the list owner can manage members").left()
            }
            val now = LocalDateTime.now(ZoneOffset.UTC)
            val updated = when (type) {
                ListType.SCHEDULED -> ListShares.update({
                    (ListShares.listID eq listId) and (ListShares.userID eq memberUserId)
                }) {
                    it[ListShares.role] = parsedRole.name
                    it[ListShares.updatedAt] = now
                }
                ListType.FLOATER -> FloaterListShares.update({
                    (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq memberUserId)
                }) {
                    it[FloaterListShares.role] = parsedRole.name
                    it[FloaterListShares.updatedAt] = now
                }
            }
            if (updated == 0) AppError.NotFound("member not found").left() else Unit.right()
        }

        result.onRight { afterMembershipChange(requesterId, listId, type) }
        return result
    }

    override suspend fun removeMember(
        requesterId: String, listId: String, type: ListType, memberUserId: String,
    ): Either<AppError, Unit> {
        val result = newSuspendedTransaction(Dispatchers.IO) {
            val access = accessForInTx(requesterId, listId, type)
                ?: return@newSuspendedTransaction AppError.NotFound("list not found").left()
            if (access != ShareRole.OWNER) {
                return@newSuspendedTransaction AppError.Forbidden("only the list owner can manage members").left()
            }
            val deleted = deleteShareRowInTx(listId, type, memberUserId)
            if (deleted == 0) AppError.NotFound("member not found").left() else Unit.right()
        }

        result.onRight {
            afterMembershipChange(requesterId, listId, type, removedUserId = memberUserId)
        }
        return result
    }

    override suspend fun leave(userId: String, listId: String, type: ListType): Either<AppError, Unit> {
        val result = newSuspendedTransaction(Dispatchers.IO) {
            when (accessForInTx(userId, listId, type)) {
                null -> AppError.NotFound("list not found").left()
                ShareRole.OWNER -> AppError.BadRequest("the owner cannot leave their own list").left()
                else -> {
                    deleteShareRowInTx(listId, type, userId)
                    Unit.right()
                }
            }
        }

        result.onRight { afterMembershipChange(userId, listId, type, removedUserId = userId) }
        return result
    }

    override suspend fun searchUsers(requesterId: String, query: String): Either<AppError, List<UserSearchResultDto>> {
        // Usernames only allow alphanumerics plus "-._"; strip anything else so
        // the LIKE pattern can't carry wildcards.
        val sanitized = query.trim().filter { it.isLetterOrDigit() || it in "-._" }
        if (sanitized.length < MIN_SEARCH_LENGTH) return emptyList<UserSearchResultDto>().right()

        val users = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where {
                (Users.username.lowerCase() like "%${sanitized.lowercase()}%") and
                    (Users.approvalStatus eq ApprovalStatus.APPROVED) and
                    (Users.id neq requesterId)
            }.orderBy(Users.username, SortOrder.ASC)
                .limit(SEARCH_RESULT_LIMIT)
                .map {
                    UserSearchResultDto(
                        id = it[Users.id],
                        username = it[Users.username],
                        name = it[Users.name],
                    )
                }
        }
        return users.right()
    }

    /** Cache invalidation + realtime fanout shared by every membership mutation. */
    private suspend fun afterMembershipChange(
        actorId: String,
        listId: String,
        type: ListType,
        removedUserId: String? = null,
    ) {
        val affected = newSuspendedTransaction(Dispatchers.IO) {
            buildSet {
                ownerOfInTx(listId, type)?.let { add(it) }
                addAll(memberRowsInTx(listId, type).map { it.userId })
                removedUserId?.let { add(it) }
                add(actorId)
            }
        }
        affected.forEach { cache.invalidateForUser(it) }
        realtime.emitToUsers(affected, DomainEvent.MembersChanged(listId))
        // The removed/leaving user must drop the list entirely.
        removedUserId?.let {
            realtime.emitToUsers(
                listOf(it),
                when (type) {
                    ListType.SCHEDULED -> DomainEvent.ListChanged(listId)
                    ListType.FLOATER -> DomainEvent.FloaterListChanged(listId)
                },
            )
        }
    }

    private fun accessForInTx(userId: String, listId: String, type: ListType): ShareRole? {
        val ownerId = ownerOfInTx(listId, type) ?: return null
        if (ownerId == userId) return ShareRole.OWNER
        val roleName = when (type) {
            ListType.SCHEDULED -> ListShares.selectAll().where {
                (ListShares.listID eq listId) and (ListShares.userID eq userId)
            }.firstOrNull()?.get(ListShares.role)
            ListType.FLOATER -> FloaterListShares.selectAll().where {
                (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq userId)
            }.firstOrNull()?.get(FloaterListShares.role)
        }
        return ShareRole.fromString(roleName)
    }

    private fun ownerOfInTx(listId: String, type: ListType): String? = when (type) {
        ListType.SCHEDULED -> Lists.selectAll().where { Lists.id eq listId }.firstOrNull()?.get(Lists.userID)
        ListType.FLOATER -> FloaterLists.selectAll().where { FloaterLists.id eq listId }.firstOrNull()?.get(FloaterLists.userID)
    }

    private fun memberRowsInTx(listId: String, type: ListType): List<ListMemberDto> = when (type) {
        ListType.SCHEDULED ->
            ListShares.join(Users, JoinType.INNER, ListShares.userID, Users.id)
                .selectAll().where { ListShares.listID eq listId }
                .orderBy(ListShares.createdAt, SortOrder.ASC)
                .map {
                    ListMemberDto(
                        userId = it[ListShares.userID],
                        username = it[Users.username],
                        name = it[Users.name],
                        role = it[ListShares.role],
                        addedAt = it[ListShares.createdAt].toString(),
                    )
                }
        ListType.FLOATER ->
            FloaterListShares.join(Users, JoinType.INNER, FloaterListShares.userID, Users.id)
                .selectAll().where { FloaterListShares.listID eq listId }
                .orderBy(FloaterListShares.createdAt, SortOrder.ASC)
                .map {
                    ListMemberDto(
                        userId = it[FloaterListShares.userID],
                        username = it[Users.username],
                        name = it[Users.name],
                        role = it[FloaterListShares.role],
                        addedAt = it[FloaterListShares.createdAt].toString(),
                    )
                }
    }

    private fun deleteShareRowInTx(listId: String, type: ListType, userId: String): Int = when (type) {
        ListType.SCHEDULED -> ListShares.deleteWhere {
            (ListShares.listID eq listId) and (ListShares.userID eq userId)
        }
        ListType.FLOATER -> FloaterListShares.deleteWhere {
            (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq userId)
        }
    }

    private fun parseMemberRole(role: String): ShareRole? =
        ShareRole.fromString(role)?.takeIf { it != ShareRole.OWNER }

    private companion object {
        const val COLLABORATORS_ENDPOINT = "shareCollaborators"
        const val COLLABORATORS_TTL_MS = 60_000L
        const val MIN_SEARCH_LENGTH = 2
        const val SEARCH_RESULT_LIMIT = 10
    }
}
