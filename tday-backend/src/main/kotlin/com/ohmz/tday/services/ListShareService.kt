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
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class ListType { SCHEDULED, FLOATER }

/** The wire value clients use to tell a scheduled list from a floater list, matching the
 * `/list` vs `/floaterList` route prefix and the `list.*`/`floaterList.*` [DomainEvent] names. */
fun ListType.wireName(): String = when (this) {
    ListType.SCHEDULED -> "list"
    ListType.FLOATER -> "floaterList"
}

/** Shortest member-search query worth running, and the cap on how many members come back. */
private const val MIN_SEARCH_LENGTH = 2
private const val SEARCH_RESULT_LIMIT = 10

/**
 * The escape character bound into every member-search `LIKE`, and the characters it has to
 * protect. `%` and `_` are the wildcards PostgreSQL recognises in a pattern; the escape
 * character stands for itself once escaped.
 */
private const val LIKE_ESCAPE_CHAR = '\\'
private const val LIKE_WILDCARDS = "%_"

/**
 * [text] as a pattern fragment that matches itself, with every LIKE metacharacter prefixed by
 * [LIKE_ESCAPE_CHAR].
 *
 * Nothing is dropped. Two rounds of this search have now been broken by a filter that decided
 * which characters a query was allowed to keep: the original allow-list stripped `@`, so the
 * email-shaped usernames this app issued before V11 could not be found even by typing them in
 * full, and removing the wildcards instead did the same to `_`, which the registration username
 * pattern permits and which display names use freely. Escaping is what makes a query mean itself
 * without narrowing which accounts can be looked up.
 *
 * This mirrors what `LikePattern.ofLiteral` does for PostgreSQL, spelled out here because that
 * helper reads `currentDialect` and so only works inside a transaction.
 */
internal fun escapeLikeLiteral(text: String): String = buildString {
    text.forEach { char ->
        if (char == LIKE_ESCAPE_CHAR || char in LIKE_WILDCARDS) append(LIKE_ESCAPE_CHAR)
        append(char)
    }
}

/**
 * The LIKE pattern for a member search, or null when the query is too short to run.
 *
 * The pattern carries its own `ESCAPE` clause, so a typed `%` or `_` matches that character
 * rather than acting as a wildcard. It is bound as a statement parameter either way — this is
 * about what the query means, not an injection defence.
 */
internal fun userSearchPattern(query: String): LikePattern? {
    val trimmed = query.trim()
    if (trimmed.length < MIN_SEARCH_LENGTH) return null
    return LikePattern("%${escapeLikeLiteral(trimmed.lowercase())}%", LIKE_ESCAPE_CHAR)
}

/**
 * Matches [pattern] against the username and the display name, as one OR group so that the
 * approval and requester filters still apply to both:
 * `(username LIKE ? ESCAPE ? OR name LIKE ? ESCAPE ?) AND ...`.
 *
 * `name` is nullable and coalesced to an empty string, so a member who never set a display name
 * stays findable by username.
 */
private fun matchesUserSearch(pattern: LikePattern): Op<Boolean> =
    (Users.username.lowerCase() like pattern) or
        (Coalesce(Users.name, stringLiteral("")).lowerCase() like pattern)

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
    private val push: PushNotificationService,
) : ListShareService {
    private val logger = LoggerFactory.getLogger(ListShareServiceImpl::class.java)

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

        // Set inside the transaction below; read after it commits to decide whether this is a
        // brand-new share (worth a push) or a re-share/role-change on an existing member (not).
        var isNewMember = false
        var listName = ""
        var sharerLabel = "Someone"

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

            listName = listNameInTx(listId, type)
            sharerLabelInTx(requesterId)?.let { sharerLabel = it }

            val now = LocalDateTime.now(ZoneOffset.UTC)
            isNewMember = upsertShareRoleInTx(listId, type, targetId, parsedRole.name, now)
            ListMemberDto(
                userId = targetId,
                username = targetRow[Users.username],
                name = targetRow[Users.name],
                role = parsedRole.name,
                addedAt = now.toString(),
            ).right()
        }

        result.onRight { member ->
            afterMembershipChange(requesterId, listId, type)
            // Only a genuinely new share is push-worthy — re-adding an existing member just
            // changes their role via the same request and already reaches them over the
            // realtime `list.members` event above.
            if (isNewMember) {
                notifyNewMember(targetId = member.userId, listId = listId, type = type, listName = listName, sharerLabel = sharerLabel)
            }
        }
        return result
    }

    /**
     * Pushes the newly-added member a "you've been shared a list" notification.
     *
     * Awaited (not detached like [PushNotificationService.notifyDataChanged]) because this fires
     * once per explicit "add member" action, not on every mutation — the added latency of a
     * network push send is an acceptable trade for keeping the send observable/testable in the
     * same request instead of a fire-and-forget scope. A failed send is logged, never surfaced
     * to the caller: the share itself already succeeded.
     */
    private suspend fun notifyNewMember(targetId: String, listId: String, type: ListType, listName: String, sharerLabel: String) {
        val title = "New list shared with you"
        val body = if (listName.isNotBlank()) {
            "$sharerLabel shared \"$listName\" with you"
        } else {
            "$sharerLabel shared a list with you"
        }
        runCatching {
            push.sendToUser(
                userId = targetId,
                title = title,
                body = body,
                listId = listId,
                listType = type.wireName(),
                listName = listName.ifBlank { null },
            )
        }.onSuccess { result ->
            result.fold(
                { error -> logger.warn("List-share push to {} failed: {}", targetId, error.message) },
                { /* delivered, or no-op if the member has no push subscription */ },
            )
        }.onFailure { logger.warn("List-share push to {} threw: {}", targetId, it.message) }
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
        val pattern = userSearchPattern(query) ?: return emptyList<UserSearchResultDto>().right()

        val users = newSuspendedTransaction(Dispatchers.IO) {
            Users.selectAll().where {
                matchesUserSearch(pattern) and
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

    private fun listNameInTx(listId: String, type: ListType): String = when (type) {
        ListType.SCHEDULED -> Lists.selectAll().where { Lists.id eq listId }.firstOrNull()?.get(Lists.name)
        ListType.FLOATER -> FloaterLists.selectAll().where { FloaterLists.id eq listId }.firstOrNull()?.get(FloaterLists.name)
    }.orEmpty()

    private fun sharerLabelInTx(userId: String): String? =
        Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let { it[Users.name] ?: it[Users.username] }

    /**
     * Inserts a new share row, or updates the role on an existing one. Returns whether this was
     * a brand-new share (an insert) rather than a role change on an existing member — that
     * distinction is what decides whether [addMember] pushes the target a notification.
     */
    private fun upsertShareRoleInTx(listId: String, type: ListType, targetId: String, role: String, now: LocalDateTime): Boolean {
        val isNew = when (type) {
            ListType.SCHEDULED -> ListShares.selectAll().where {
                (ListShares.listID eq listId) and (ListShares.userID eq targetId)
            }.firstOrNull() == null
            ListType.FLOATER -> FloaterListShares.selectAll().where {
                (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq targetId)
            }.firstOrNull() == null
        }
        when (type) {
            ListType.SCHEDULED -> if (isNew) {
                ListShares.insert {
                    it[ListShares.id] = CuidGenerator.newCuid()
                    it[ListShares.listID] = listId
                    it[ListShares.userID] = targetId
                    it[ListShares.role] = role
                    it[ListShares.createdAt] = now
                    it[ListShares.updatedAt] = now
                }
            } else {
                ListShares.update({ (ListShares.listID eq listId) and (ListShares.userID eq targetId) }) {
                    it[ListShares.role] = role
                    it[ListShares.updatedAt] = now
                }
            }
            ListType.FLOATER -> if (isNew) {
                FloaterListShares.insert {
                    it[FloaterListShares.id] = CuidGenerator.newCuid()
                    it[FloaterListShares.listID] = listId
                    it[FloaterListShares.userID] = targetId
                    it[FloaterListShares.role] = role
                    it[FloaterListShares.createdAt] = now
                    it[FloaterListShares.updatedAt] = now
                }
            } else {
                FloaterListShares.update({ (FloaterListShares.listID eq listId) and (FloaterListShares.userID eq targetId) }) {
                    it[FloaterListShares.role] = role
                    it[FloaterListShares.updatedAt] = now
                }
            }
        }
        return isNew
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
    }
}
