package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.FloaterUncompleteResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.security.decryptRequired
import com.ohmz.tday.security.encryptRequired
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
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
    suspend fun uncompleteFloater(userId: String, floaterId: String): Either<AppError, FloaterUncompleteResponse>
    suspend fun prioritize(userId: String, floaterId: String, priority: String): Either<AppError, Unit>
    suspend fun reorder(userId: String, floaterId: String, newOrder: Int): Either<AppError, Unit>
    suspend fun promoteToTodo(userId: String, floaterId: String, due: LocalDateTime, rrule: String?): Either<AppError, TodoResponse>
}

class FloaterServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
    private val shareService: ListShareService,
    private val publisher: RealtimePublisher,
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
        if (normalizedListID != null && !shareService.canEditList(userId, normalizedListID, ListType.FLOATER)) {
            return Either.Left(AppError.BadRequest("floater list not found"))
        }
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.insert {
                it[Floaters.id] = id
                it[Floaters.title] = fieldEncryption.encryptRequired("title", title)
                it[Floaters.description] = fieldEncryption.encryptIfSensitive("description", description)
                it[Floaters.priority] = Priority.valueOf(priority)
                it[Floaters.listID] = normalizedListID
                it[Floaters.userID] = userId
                it[Floaters.createdAt] = now
                it[Floaters.updatedAt] = now
            }
        }
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged(normalizedListID))
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
        val visibleListIds = shareService.sharedListIdsFor(userId, ListType.FLOATER)
        val floaters = newSuspendedTransaction(Dispatchers.IO) {
            Floaters.selectAll().where {
                visibleFloaters(userId, visibleListIds) and (Floaters.completed eq false)
            }
                .orderBy(Floaters.priority to SortOrder.DESC, Floaters.pinned to SortOrder.DESC, Floaters.order to SortOrder.ASC)
                .map { it.toFloaterResponse() }
        }
        return floaters.right()
    }

    override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
        val targetListId = fields["listID"] as? String
        if (fields.containsKey("listID") && targetListId != null &&
            !shareService.canEditList(userId, targetListId, ListType.FLOATER)
        ) {
            return Either.Left(AppError.BadRequest("floater list not found"))
        }
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.update({ (Floaters.id eq id) and mutableFloaters(userId, editableListIds) }) { stmt ->
                fields["title"]?.let {
                    stmt[Floaters.title] = fieldEncryption.encryptRequired("title", it as String)
                }
                fields["description"]?.let {
                    stmt[Floaters.description] = fieldEncryption.encryptIfSensitive("description", it as? String)
                }
                fields["priority"]?.let { stmt[Floaters.priority] = Priority.valueOf(it as String) }
                fields["pinned"]?.let { stmt[Floaters.pinned] = it as Boolean }
                fields["completed"]?.let { stmt[Floaters.completed] = it as Boolean }
                if (fields.containsKey("listID")) stmt[Floaters.listID] = fields["listID"] as? String
                stmt[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged(targetListId))
        return Unit.right()
    }

    override suspend fun delete(userId: String, id: String): Either<AppError, Int> {
        val editableListIds = editableListIdsFor(userId)
        val count = newSuspendedTransaction(Dispatchers.IO) {
            CompletedFloaters.deleteWhere {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq id)
            }
            Floaters.deleteWhere { (Floaters.id eq id) and mutableFloaters(userId, editableListIds) }
        }
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        return count.right()
    }

    override suspend fun completeFloater(userId: String, floaterId: String): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            val floater = Floaters.selectAll().where {
                (Floaters.id eq floaterId) and mutableFloaters(userId, editableListIds)
            }.firstOrNull() ?: return@newSuspendedTransaction

            val now = LocalDateTime.now(ZoneOffset.UTC)
            val daysToComplete = Duration.between(floater[Floaters.createdAt], now).toDays().toDouble()
            // Access was already checked against the floater; the list row is only
            // denormalized metadata, so no owner filter here (shared lists belong
            // to another user).
            val list = floater[Floaters.listID]?.let { listId ->
                FloaterLists.selectAll().where { FloaterLists.id eq listId }.firstOrNull()
            }
            val existingCompleted = CompletedFloaters.selectAll().where {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
            }.firstOrNull()

            if (existingCompleted == null) {
                CompletedFloaters.insert {
                    it[CompletedFloaters.id] = CuidGenerator.newCuid()
                    it[CompletedFloaters.originalFloaterID] = floaterId
                    // Copied at rest: both tables encrypt "title"/"description".
                    it[CompletedFloaters.title] = floater[Floaters.title]
                    it[CompletedFloaters.description] = floater[Floaters.description]
                    it[CompletedFloaters.priority] = floater[Floaters.priority]
                    it[CompletedFloaters.completedAt] = now
                    it[CompletedFloaters.daysToComplete] = BigDecimal.valueOf(daysToComplete).setScale(2, RoundingMode.HALF_UP)
                    it[CompletedFloaters.userID] = userId
                    it[CompletedFloaters.listID] = floater[Floaters.listID]
                    // Unconstrained snapshot of the same value, for
                    // uncompleteFloater() to correlate on after listID above
                    // is nulled by ON DELETE SET NULL if the list is deleted.
                    it[CompletedFloaters.originalListID] = floater[Floaters.listID]
                    it[CompletedFloaters.listName] = list?.get(FloaterLists.name)
                    it[CompletedFloaters.listColor] = list?.get(FloaterLists.color)?.name
                }
            }

            Floaters.update({ Floaters.id eq floaterId }) {
                it[Floaters.completed] = true
                it[Floaters.updatedAt] = now
            }
        }
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        publisher.publishToCollaborators(userId, DomainEvent.CompletedChanged())
        return Unit.right()
    }

    /**
     * Undoes a completion. Two cases, because the primary Floaters row does
     * not always survive to be flipped back:
     *
     * (a) The Floaters row is still here -- the common case, and the only one
     *     that existed before list deletion could detach a CompletedFloaters
     *     row instead of deleting it. Flip completed back to false, same as
     *     always, whether or not a CompletedFloaters row is still around to
     *     consume (an already-uncompleted floater is a harmless no-op here,
     *     matching the old behavior of this method).
     *
     * (b) The Floaters row is gone -- FloaterListService.deleteMany() deletes
     *     every floater in a deleted list, completed or not, alongside the
     *     list itself. The only way to land here is via a CompletedFloaters
     *     row whose list was deleted out from under it. Find-or-create the
     *     list from the CompletedFloaters row's own originalListID (falling
     *     back to its FK listID for a row written before that column
     *     existed), recreate the Floaters row from the snapshot, and consume
     *     the CompletedFloaters row.
     *
     * If neither exists, there is nothing to undo.
     */
    override suspend fun uncompleteFloater(userId: String, floaterId: String): Either<AppError, FloaterUncompleteResponse> {
        val editableListIds = editableListIdsFor(userId)
        val outcome = newSuspendedTransaction(Dispatchers.IO) {
            val completedRow = CompletedFloaters.selectAll().where {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
            }.firstOrNull()

            val liveRow = Floaters.selectAll().where {
                (Floaters.id eq floaterId) and mutableFloaters(userId, editableListIds)
            }.firstOrNull()

            if (liveRow == null && completedRow == null) {
                return@newSuspendedTransaction null
            }

            if (liveRow != null) {
                Floaters.update({ Floaters.id eq floaterId }) {
                    it[Floaters.completed] = false
                    it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
                }
                if (completedRow != null) {
                    CompletedFloaters.deleteWhere {
                        (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
                    }
                }
                val restored = Floaters.selectAll().where { Floaters.id eq floaterId }.first()
                val list = restored[Floaters.listID]?.let { listId ->
                    FloaterLists.selectAll().where { FloaterLists.id eq listId }.firstOrNull()
                }
                return@newSuspendedTransaction UncompleteOutcome(
                    floater = restored.toFloaterResponse(),
                    listRecreated = false,
                    listID = restored[Floaters.listID],
                    listName = list?.get(FloaterLists.name),
                    listColor = list?.get(FloaterLists.color)?.name,
                )
            }

            // completedRow is non-null here (the both-null case returned above).
            recreateFromCompletedRow(userId, floaterId, completedRow!!)
        } ?: return Either.Left(AppError.NotFound("nothing to restore for this floater"))

        cache.invalidateFloaterCaches(userId)
        if (outcome.listRecreated) cache.invalidateFloaterListCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        publisher.publishToCollaborators(userId, DomainEvent.CompletedChanged())
        if (outcome.listRecreated) publisher.publishToCollaborators(userId, DomainEvent.FloaterListChanged())

        return FloaterUncompleteResponse(
            message = if (outcome.listRecreated) "floater restored into a recreated list" else "floater uncompleted",
            floater = outcome.floater,
            listRecreated = outcome.listRecreated,
            listID = outcome.listID,
            listName = outcome.listName,
            listColor = outcome.listColor,
        ).right()
    }

    /** Case (b) of [uncompleteFloater]: the Floaters row is gone, only the completion snapshot is left. */
    private fun recreateFromCompletedRow(userId: String, floaterId: String, completedRow: ResultRow): UncompleteOutcome {
        val originalListId = completedRow[CompletedFloaters.originalListID] ?: completedRow[CompletedFloaters.listID]

        var landedListId: String? = null
        var landedListName: String? = null
        var landedListColor: String? = null
        var listRecreated = false

        if (originalListId != null) {
            // Defensive only: a Floaters row should never outlive the list it
            // belongs to (see FloaterListService.deleteMany()), so liveRow ==
            // null in the caller already implies the original list is gone.
            // If that ever stops holding, land back in the real original
            // list instead of manufacturing a duplicate for it.
            val stillLiveList = FloaterLists.selectAll().where {
                (FloaterLists.userID eq userId) and (FloaterLists.id eq originalListId)
            }.firstOrNull()

            if (stillLiveList != null) {
                landedListId = originalListId
                landedListName = stillLiveList[FloaterLists.name]
                landedListColor = stillLiveList[FloaterLists.color]?.name
            } else {
                // The original list is gone -- wherever this lands is, by
                // definition, not the list it was completed from.
                listRecreated = true
                val recreatedList = FloaterLists.selectAll().where {
                    (FloaterLists.userID eq userId) and (FloaterLists.recreatedFromListID eq originalListId)
                }.firstOrNull()

                if (recreatedList != null) {
                    // A previous undo from this same deleted list already
                    // recreated it -- converge onto that one, no duplicate.
                    landedListId = recreatedList[FloaterLists.id]
                    landedListName = recreatedList[FloaterLists.name]
                    landedListColor = recreatedList[FloaterLists.color]?.name
                } else {
                    val newListId = CuidGenerator.newCuid()
                    val now = LocalDateTime.now(ZoneOffset.UTC)
                    val colorEnum = completedRow[CompletedFloaters.listColor]
                        ?.let { runCatching { ListColor.valueOf(it) }.getOrNull() }
                    FloaterLists.insert {
                        it[FloaterLists.id] = newListId
                        it[FloaterLists.name] = completedRow[CompletedFloaters.listName] ?: "Recreated list"
                        it[FloaterLists.color] = colorEnum
                        it[FloaterLists.iconKey] = null
                        it[FloaterLists.userID] = userId
                        it[FloaterLists.reusable] = false
                        it[FloaterLists.recreatedFromListID] = originalListId
                        it[FloaterLists.createdAt] = now
                        it[FloaterLists.updatedAt] = now
                    }
                    landedListId = newListId
                    landedListName = completedRow[CompletedFloaters.listName] ?: "Recreated list"
                    landedListColor = completedRow[CompletedFloaters.listColor]
                }
            }
        }

        val newFloaterId = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        Floaters.insert {
            it[Floaters.id] = newFloaterId
            it[Floaters.title] = completedRow[CompletedFloaters.title]
            it[Floaters.description] = completedRow[CompletedFloaters.description]
            it[Floaters.priority] = completedRow[CompletedFloaters.priority]
            it[Floaters.listID] = landedListId
            it[Floaters.userID] = userId
            it[Floaters.completed] = false
            it[Floaters.createdAt] = now
            it[Floaters.updatedAt] = now
        }
        CompletedFloaters.deleteWhere {
            (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
        }

        val newFloaterRow = Floaters.selectAll().where { Floaters.id eq newFloaterId }.first()
        return UncompleteOutcome(
            floater = newFloaterRow.toFloaterResponse(),
            listRecreated = listRecreated,
            listID = landedListId,
            listName = landedListName,
            listColor = landedListColor,
        )
    }

    private data class UncompleteOutcome(
        val floater: FloaterResponse,
        val listRecreated: Boolean,
        val listID: String?,
        val listName: String?,
        val listColor: String?,
    )

    override suspend fun prioritize(userId: String, floaterId: String, priority: String): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.update({ (Floaters.id eq floaterId) and mutableFloaters(userId, editableListIds) }) {
                it[Floaters.priority] = Priority.valueOf(priority)
                it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        return Unit.right()
    }

    override suspend fun reorder(userId: String, floaterId: String, newOrder: Int): Either<AppError, Unit> {
        val editableListIds = editableListIdsFor(userId)
        newSuspendedTransaction(Dispatchers.IO) {
            Floaters.update({ (Floaters.id eq floaterId) and mutableFloaters(userId, editableListIds) }) {
                it[Floaters.order] = newOrder
                it[Floaters.updatedAt] = LocalDateTime.now(ZoneOffset.UTC)
            }
        }
        cache.invalidateFloaterCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        return Unit.right()
    }

    override suspend fun promoteToTodo(
        userId: String,
        floaterId: String,
        due: LocalDateTime,
        rrule: String?,
    ): Either<AppError, TodoResponse> {
        val newTodoId = CuidGenerator.newCuid()
        val now = LocalDateTime.now(ZoneOffset.UTC)
        // Owner-only: promoting moves the row out of any shared floater list, so
        // an editor collaborator must not be able to pull it into their timeline.
        val promoted = newSuspendedTransaction(Dispatchers.IO) {
            val floater = Floaters.selectAll().where {
                (Floaters.id eq floaterId) and (Floaters.userID eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction null

            Todos.insert {
                it[Todos.id] = newTodoId
                // Ciphertext copies straight across — both tables encrypt "title"/"description".
                it[Todos.title] = floater[Floaters.title]
                it[Todos.description] = floater[Floaters.description]
                it[Todos.priority] = floater[Floaters.priority]
                it[Todos.pinned] = floater[Floaters.pinned]
                it[Todos.due] = due
                it[Todos.rrule] = rrule
                // Floater lists and todo lists are separate types; membership stays behind.
                it[Todos.listID] = null
                it[Todos.userID] = userId
                it[Todos.createdAt] = floater[Floaters.createdAt]
                it[Todos.updatedAt] = now
                it[Todos.exdates] = emptyList()
            }
            CompletedFloaters.deleteWhere {
                (CompletedFloaters.userID eq userId) and (CompletedFloaters.originalFloaterID eq floaterId)
            }
            Floaters.deleteWhere { (Floaters.id eq floaterId) and (Floaters.userID eq userId) }
            floater
        } ?: return Either.Left(AppError.NotFound("floater not found"))

        cache.invalidateFloaterCaches(userId)
        cache.invalidateTodoCaches(userId)
        publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
        publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
        return TodoResponse(
            id = newTodoId,
            title = fieldEncryption.decryptRequired(promoted[Floaters.title]),
            description = fieldEncryption.decryptIfEncrypted(promoted[Floaters.description]),
            pinned = promoted[Floaters.pinned],
            priority = promoted[Floaters.priority].name,
            due = due.toString(),
            rrule = rrule,
            timeZone = "UTC",
            completed = false,
            order = 0,
            listID = null,
            userID = userId,
            createdAt = promoted[Floaters.createdAt].toString(),
            updatedAt = now.toString(),
        ).right()
    }

    private fun ResultRow.toFloaterResponse(): FloaterResponse = FloaterResponse(
        id = this[Floaters.id],
        title = fieldEncryption.decryptRequired(this[Floaters.title]),
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

    private suspend fun editableListIdsFor(userId: String): List<String> =
        shareService.sharedListIdsFor(userId, ListType.FLOATER, editorOnly = true)

    /** Floaters the user can see: their own, plus everything in lists shared with them. */
    private fun visibleFloaters(userId: String, sharedListIds: List<String>): Op<Boolean> =
        Op.build {
            if (sharedListIds.isEmpty()) {
                Floaters.userID eq userId
            } else {
                (Floaters.userID eq userId) or (Floaters.listID inList sharedListIds)
            }
        }

    /**
     * Floaters the user can mutate: their own, plus everything in lists where
     * they are an EDITOR. Viewers fail closed (0 rows matched).
     */
    private fun mutableFloaters(userId: String, editableListIds: List<String>): Op<Boolean> =
        Op.build {
            if (editableListIds.isEmpty()) {
                Floaters.userID eq userId
            } else {
                (Floaters.userID eq userId) or (Floaters.listID inList editableListIds)
            }
        }
}
