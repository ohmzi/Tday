package com.ohmz.tday.services

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.db.enums.Direction
import com.ohmz.tday.db.enums.GroupBy
import com.ohmz.tday.db.enums.ListColor
import com.ohmz.tday.db.enums.Priority
import com.ohmz.tday.db.enums.SortBy
import com.ohmz.tday.db.tables.CompletedFloaters
import com.ohmz.tday.db.tables.CompletedTodos
import com.ohmz.tday.db.tables.FloaterLists
import com.ohmz.tday.db.tables.Floaters
import com.ohmz.tday.db.tables.Lists
import com.ohmz.tday.db.tables.TodoInstances
import com.ohmz.tday.db.tables.Todos
import com.ohmz.tday.db.tables.UserPreferences
import com.ohmz.tday.db.util.CuidGenerator
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.domain.DomainEvent
import com.ohmz.tday.routes.parseTodoDateTime
import com.ohmz.tday.security.FieldEncryption
import com.ohmz.tday.shared.model.CompletedFloaterDto
import com.ohmz.tday.shared.model.CompletedTodoDto
import com.ohmz.tday.shared.model.ExportedTodoDto
import com.ohmz.tday.shared.model.FloaterDto
import com.ohmz.tday.shared.model.FloaterListDto
import com.ohmz.tday.shared.model.ImportCounts
import com.ohmz.tday.shared.model.ImportRequest
import com.ohmz.tday.shared.model.ImportResponse
import com.ohmz.tday.shared.model.ListDto
import com.ohmz.tday.shared.model.PreferencesDto
import com.ohmz.tday.shared.model.TdayExport
import com.ohmz.tday.shared.model.TodoDto
import com.ohmz.tday.shared.model.TodoInstanceDto
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneOffset

interface ExportService {
    /** The full portable bundle for [userId] — descriptions decrypted to plaintext. */
    suspend fun exportAll(userId: String): Either<AppError, TdayExport>

    /** Restore/merge a bundle into [userId]'s data. Never overwrites existing rows. */
    suspend fun import(userId: String, request: ImportRequest): Either<AppError, ImportResponse>
}

class ExportServiceImpl(
    private val fieldEncryption: FieldEncryption,
    private val cache: CacheService,
    private val publisher: RealtimePublisher,
) : ExportService {

    override suspend fun exportAll(userId: String): Either<AppError, TdayExport> {
        val bundle = newSuspendedTransaction(Dispatchers.IO) {
            val todoRows = Todos.selectAll().where { Todos.userID eq userId }.toList()
            val todoIds = todoRows.map { it[Todos.id] }
            val instancesByTodo = if (todoIds.isEmpty()) {
                emptyMap()
            } else {
                TodoInstances.selectAll().where { TodoInstances.todoId inList todoIds }
                    .groupBy({ it[TodoInstances.todoId] }, { it.toInstanceDto() })
            }

            TdayExport(
                exportedAt = LocalDateTime.now(ZoneOffset.UTC).toString(),
                source = "server",
                lists = Lists.selectAll().where { Lists.userID eq userId }.map { it.toListDto() },
                floaterLists = FloaterLists.selectAll().where { FloaterLists.userID eq userId }
                    .map { it.toFloaterListDto() },
                todos = todoRows.map { row ->
                    ExportedTodoDto(
                        todo = row.toTodoDto(),
                        exdates = row[Todos.exdates].map { it.toString() },
                        instances = instancesByTodo[row[Todos.id]] ?: emptyList(),
                    )
                },
                floaters = Floaters.selectAll().where { Floaters.userID eq userId }
                    .map { it.toFloaterDto() },
                completedTodos = CompletedTodos.selectAll().where { CompletedTodos.userID eq userId }
                    .map { it.toCompletedTodoDto() },
                completedFloaters = CompletedFloaters.selectAll()
                    .where { CompletedFloaters.userID eq userId }
                    .map { it.toCompletedFloaterDto() },
                preferences = UserPreferences.selectAll().where { UserPreferences.userID eq userId }
                    .firstOrNull()?.toPreferencesDto(),
            )
        }
        return bundle.right()
    }

    override suspend fun import(userId: String, request: ImportRequest): Either<AppError, ImportResponse> {
        if (request.export.schemaVersion > TdayExport.CURRENT_SCHEMA_VERSION) {
            return Either.Left(
                AppError.BadRequest("this backup was created by a newer version of T'Day; update the server first"),
            )
        }

        return try {
            // Read the target user's existing ids so remap only touches genuine
            // collisions, plus their list ids so we can null out dangling references
            // instead of tripping a foreign-key constraint mid-import.
            val context = newSuspendedTransaction(Dispatchers.IO) { readImportContext(userId) }

            val remapped = ExportRemap.remapCollisions(
                export = request.export,
                idExists = context.existingIds::contains,
                newId = CuidGenerator::newCuid,
            )
            val bundle = remapped.export

            val validListIds = bundle.lists.map { it.id }.toSet() + context.existingListIds
            val validFloaterListIds = bundle.floaterLists.map { it.id }.toSet() + context.existingFloaterListIds

            val counts = ImportCounts(
                lists = bundle.lists.size,
                floaterLists = bundle.floaterLists.size,
                todos = bundle.todos.size,
                floaters = bundle.floaters.size,
                todoInstances = bundle.todos.sumOf { it.instances.size },
                completedTodos = if (request.includeCompleted) bundle.completedTodos.size else 0,
                completedFloaters = if (request.includeCompleted) bundle.completedFloaters.size else 0,
                remappedIds = remapped.remappedIds,
                preferencesApplied = request.includePreferences && bundle.preferences != null,
            )

            if (request.dryRun) {
                return ImportResponse(
                    dryRun = true,
                    imported = counts,
                    message = "Preview only — nothing was written.",
                ).right()
            }

            newSuspendedTransaction(Dispatchers.IO) {
                writeBundle(userId, bundle, request, validListIds, validFloaterListIds)
            }

            cache.invalidateForUser(userId)
            publisher.publishToCollaborators(userId, DomainEvent.ListChanged())
            publisher.publishToCollaborators(userId, DomainEvent.FloaterListChanged())
            publisher.publishToCollaborators(userId, DomainEvent.TodoChanged())
            publisher.publishToCollaborators(userId, DomainEvent.FloaterChanged())
            publisher.publishToCollaborators(userId, DomainEvent.CompletedChanged())

            ImportResponse(dryRun = false, imported = counts, message = "Import complete.").right()
        } catch (e: IllegalArgumentException) {
            Either.Left(AppError.BadRequest(e.message ?: "the backup file could not be read"))
        } catch (e: Exception) {
            Either.Left(AppError.Internal(e.message ?: "import failed", e))
        }
    }

    // ── Import write (single transaction) ─────────────────────────────────────

    private fun writeBundle(
        userId: String,
        bundle: TdayExport,
        request: ImportRequest,
        validListIds: Set<String>,
        validFloaterListIds: Set<String>,
    ) {
        val now = LocalDateTime.now(ZoneOffset.UTC)

        bundle.lists.forEach { list ->
            Lists.insert {
                it[Lists.id] = list.id
                it[Lists.name] = list.name
                it[Lists.color] = parseListColor(list.color)
                it[Lists.iconKey] = list.iconKey
                it[Lists.userID] = userId
                it[Lists.createdAt] = parseTodoDateTime(list.createdAt) ?: now
                it[Lists.updatedAt] = parseTodoDateTime(list.updatedAt) ?: now
            }
        }

        bundle.floaterLists.forEach { list ->
            FloaterLists.insert {
                it[FloaterLists.id] = list.id
                it[FloaterLists.name] = list.name
                it[FloaterLists.color] = parseListColor(list.color)
                it[FloaterLists.iconKey] = list.iconKey
                it[FloaterLists.userID] = userId
                it[FloaterLists.createdAt] = parseTodoDateTime(list.createdAt) ?: now
                it[FloaterLists.updatedAt] = parseTodoDateTime(list.updatedAt) ?: now
            }
        }

        // Preserve relative order: insert by ascending exported order so the
        // autoincrement `order` column re-sequences them the same way.
        bundle.todos.sortedBy { it.todo.order ?: Int.MAX_VALUE }.forEach { exported ->
            val todo = exported.todo
            Todos.insert {
                it[Todos.id] = todo.id
                it[Todos.title] = todo.title
                it[Todos.description] = fieldEncryption.encryptIfSensitive("description", todo.description)
                it[Todos.priority] = Priority.fromApiOrDefault(todo.priority)
                it[Todos.pinned] = todo.pinned
                it[Todos.due] = parseTodoDateTime(todo.due) ?: now
                it[Todos.rrule] = todo.rrule
                it[Todos.timeZone] = todo.timeZone ?: "UTC"
                it[Todos.completed] = todo.completed
                it[Todos.listID] = todo.listID?.takeIf(validListIds::contains)
                it[Todos.userID] = userId
                it[Todos.createdAt] = parseTodoDateTime(todo.createdAt) ?: now
                it[Todos.updatedAt] = parseTodoDateTime(todo.updatedAt) ?: now
                it[Todos.exdates] = exported.exdates.mapNotNull(::parseTodoDateTime)
            }
            insertTodoInstances(todo.id, exported.instances)
        }

        bundle.floaters.sortedBy { it.order ?: Int.MAX_VALUE }.forEach { floater ->
            Floaters.insert {
                it[Floaters.id] = floater.id
                it[Floaters.title] = floater.title
                it[Floaters.description] = fieldEncryption.encryptIfSensitive("description", floater.description)
                it[Floaters.priority] = Priority.fromApiOrDefault(floater.priority)
                it[Floaters.pinned] = floater.pinned
                it[Floaters.completed] = floater.completed
                it[Floaters.listID] = floater.listID?.takeIf(validFloaterListIds::contains)
                it[Floaters.userID] = userId
                it[Floaters.createdAt] = parseTodoDateTime(floater.createdAt) ?: now
                it[Floaters.updatedAt] = parseTodoDateTime(floater.updatedAt) ?: now
            }
        }

        if (request.includeCompleted) {
            insertCompletedHistory(userId, bundle, validListIds, validFloaterListIds, now)
        }

        if (request.includePreferences) {
            bundle.preferences?.let { prefs -> upsertPreferences(userId, prefs) }
        }
    }

    private fun insertTodoInstances(todoId: String, instances: List<TodoInstanceDto>) {
        instances.forEach { inst ->
            val instanceDate = parseTodoDateTime(inst.instanceDate)
                ?: throw IllegalArgumentException("a recurring override is missing its date")
            TodoInstances.insert {
                it[TodoInstances.id] = inst.id
                it[TodoInstances.todoId] = todoId
                it[TodoInstances.recurId] = inst.recurId
                it[TodoInstances.instanceDate] = instanceDate
                it[TodoInstances.overriddenTitle] = inst.overriddenTitle
                it[TodoInstances.overriddenDescription] =
                    fieldEncryption.encryptIfSensitive("overriddenDescription", inst.overriddenDescription)
                it[TodoInstances.overriddenPriority] = inst.overriddenPriority?.let(Priority::fromApiOrDefault)
                it[TodoInstances.overriddenDue] = inst.overriddenDue?.let(::parseTodoDateTime)
                it[TodoInstances.completedAt] = inst.completedAt?.let(::parseTodoDateTime)
            }
        }
    }

    private fun insertCompletedHistory(
        userId: String,
        bundle: TdayExport,
        validListIds: Set<String>,
        validFloaterListIds: Set<String>,
        now: LocalDateTime,
    ) {
        bundle.completedTodos.forEach { completed ->
            CompletedTodos.insert {
                it[CompletedTodos.id] = completed.id
                it[CompletedTodos.originalTodoID] = completed.originalTodoID ?: completed.id
                it[CompletedTodos.title] = completed.title
                it[CompletedTodos.description] =
                    fieldEncryption.encryptIfSensitive("description", completed.description)
                it[CompletedTodos.priority] = Priority.fromApiOrDefault(completed.priority)
                it[CompletedTodos.completedAt] = parseTodoDateTime(completed.completedAt) ?: now
                it[CompletedTodos.due] = parseTodoDateTime(completed.due) ?: now
                it[CompletedTodos.completedOnTime] = completed.completedOnTime
                it[CompletedTodos.daysToComplete] = decimal(completed.daysToComplete)
                it[CompletedTodos.rrule] = completed.rrule
                it[CompletedTodos.userID] = userId
                it[CompletedTodos.instanceDate] = completed.instanceDate?.let(::parseTodoDateTime)
                it[CompletedTodos.listID] = completed.listID?.takeIf(validListIds::contains)
                it[CompletedTodos.listName] = completed.listName
                it[CompletedTodos.listColor] = completed.listColor
            }
        }
        bundle.completedFloaters.forEach { completed ->
            CompletedFloaters.insert {
                it[CompletedFloaters.id] = completed.id
                it[CompletedFloaters.originalFloaterID] = completed.originalFloaterID ?: completed.id
                it[CompletedFloaters.title] = completed.title
                it[CompletedFloaters.description] =
                    fieldEncryption.encryptIfSensitive("description", completed.description)
                it[CompletedFloaters.priority] = Priority.fromApiOrDefault(completed.priority)
                it[CompletedFloaters.completedAt] = parseTodoDateTime(completed.completedAt) ?: now
                it[CompletedFloaters.daysToComplete] = decimal(completed.daysToComplete)
                it[CompletedFloaters.userID] = userId
                it[CompletedFloaters.listID] = completed.listID?.takeIf(validFloaterListIds::contains)
                it[CompletedFloaters.listName] = completed.listName
                it[CompletedFloaters.listColor] = completed.listColor
            }
        }
    }

    private fun upsertPreferences(userId: String, prefs: PreferencesDto) {
        val exists = UserPreferences.selectAll().where { UserPreferences.userID eq userId }.firstOrNull() != null
        val sortBy = prefs.sortBy?.let { s -> SortBy.entries.firstOrNull { it.name == s } }
        val groupBy = prefs.groupBy?.let { s -> GroupBy.entries.firstOrNull { it.name == s } }
        val direction = prefs.direction?.let { s -> Direction.entries.firstOrNull { it.name == s } }
        if (exists) {
            UserPreferences.update({ UserPreferences.userID eq userId }) {
                it[UserPreferences.sortBy] = sortBy
                it[UserPreferences.groupBy] = groupBy
                it[UserPreferences.direction] = direction
                it[UserPreferences.aiSummaryEnabled] = prefs.aiSummaryEnabled
            }
        } else {
            UserPreferences.insert {
                it[UserPreferences.id] = CuidGenerator.newCuid()
                it[UserPreferences.userID] = userId
                it[UserPreferences.sortBy] = sortBy
                it[UserPreferences.groupBy] = groupBy
                it[UserPreferences.direction] = direction
                it[UserPreferences.aiSummaryEnabled] = prefs.aiSummaryEnabled
            }
        }
    }

    private data class ImportContext(
        val existingIds: Set<String>,
        val existingListIds: Set<String>,
        val existingFloaterListIds: Set<String>,
    )

    private fun readImportContext(userId: String): ImportContext {
        val listIds = Lists.selectAll().where { Lists.userID eq userId }.map { it[Lists.id] }
        val floaterListIds = FloaterLists.selectAll().where { FloaterLists.userID eq userId }
            .map { it[FloaterLists.id] }
        val todoIds = Todos.selectAll().where { Todos.userID eq userId }.map { it[Todos.id] }
        val instanceIds = if (todoIds.isEmpty()) {
            emptyList()
        } else {
            TodoInstances.selectAll().where { TodoInstances.todoId inList todoIds }
                .map { it[TodoInstances.id] }
        }
        val existing = buildSet {
            addAll(listIds)
            addAll(floaterListIds)
            addAll(todoIds)
            addAll(instanceIds)
            addAll(Floaters.selectAll().where { Floaters.userID eq userId }.map { it[Floaters.id] })
            addAll(CompletedTodos.selectAll().where { CompletedTodos.userID eq userId }.map { it[CompletedTodos.id] })
            addAll(
                CompletedFloaters.selectAll().where { CompletedFloaters.userID eq userId }
                    .map { it[CompletedFloaters.id] },
            )
        }
        return ImportContext(existing, listIds.toSet(), floaterListIds.toSet())
    }

    // ── Export read mappers ───────────────────────────────────────────────────

    private fun ResultRow.toListDto() = ListDto(
        id = this[Lists.id],
        name = this[Lists.name],
        color = this[Lists.color]?.name,
        iconKey = this[Lists.iconKey],
        userID = this[Lists.userID],
        createdAt = this[Lists.createdAt].toString(),
        updatedAt = this[Lists.updatedAt].toString(),
    )

    private fun ResultRow.toFloaterListDto() = FloaterListDto(
        id = this[FloaterLists.id],
        name = this[FloaterLists.name],
        color = this[FloaterLists.color]?.name,
        iconKey = this[FloaterLists.iconKey],
        userID = this[FloaterLists.userID],
        createdAt = this[FloaterLists.createdAt].toString(),
        updatedAt = this[FloaterLists.updatedAt].toString(),
    )

    private fun ResultRow.toTodoDto() = TodoDto(
        id = this[Todos.id],
        title = this[Todos.title],
        description = fieldEncryption.decryptIfEncrypted(this[Todos.description]),
        pinned = this[Todos.pinned],
        priority = this[Todos.priority].name,
        due = this[Todos.due].toString(),
        rrule = this[Todos.rrule],
        timeZone = this[Todos.timeZone],
        completed = this[Todos.completed],
        order = this[Todos.order],
        listID = this[Todos.listID],
        userID = this[Todos.userID],
        updatedAt = this[Todos.updatedAt].toString(),
        createdAt = this[Todos.createdAt].toString(),
    )

    private fun ResultRow.toInstanceDto() = TodoInstanceDto(
        id = this[TodoInstances.id],
        recurId = this[TodoInstances.recurId],
        instanceDate = this[TodoInstances.instanceDate].toString(),
        overriddenTitle = this[TodoInstances.overriddenTitle],
        overriddenDescription = fieldEncryption.decryptIfEncrypted(this[TodoInstances.overriddenDescription]),
        overriddenPriority = this[TodoInstances.overriddenPriority]?.name,
        overriddenDue = this[TodoInstances.overriddenDue]?.toString(),
        completedAt = this[TodoInstances.completedAt]?.toString(),
    )

    private fun ResultRow.toFloaterDto() = FloaterDto(
        id = this[Floaters.id],
        title = this[Floaters.title],
        description = fieldEncryption.decryptIfEncrypted(this[Floaters.description]),
        pinned = this[Floaters.pinned],
        priority = this[Floaters.priority].name,
        completed = this[Floaters.completed],
        order = this[Floaters.order],
        listID = this[Floaters.listID],
        userID = this[Floaters.userID],
        updatedAt = this[Floaters.updatedAt].toString(),
        createdAt = this[Floaters.createdAt].toString(),
    )

    private fun ResultRow.toCompletedTodoDto() = CompletedTodoDto(
        id = this[CompletedTodos.id],
        originalTodoID = this[CompletedTodos.originalTodoID],
        title = this[CompletedTodos.title],
        description = fieldEncryption.decryptIfEncrypted(this[CompletedTodos.description]),
        priority = this[CompletedTodos.priority].name,
        due = this[CompletedTodos.due].toString(),
        completedAt = this[CompletedTodos.completedAt].toString(),
        completedOnTime = this[CompletedTodos.completedOnTime],
        daysToComplete = this[CompletedTodos.daysToComplete].toDouble(),
        rrule = this[CompletedTodos.rrule],
        userID = this[CompletedTodos.userID],
        instanceDate = this[CompletedTodos.instanceDate]?.toString(),
        listID = this[CompletedTodos.listID],
        listName = this[CompletedTodos.listName],
        listColor = this[CompletedTodos.listColor],
    )

    private fun ResultRow.toCompletedFloaterDto() = CompletedFloaterDto(
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

    private fun ResultRow.toPreferencesDto() = PreferencesDto(
        sortBy = this[UserPreferences.sortBy]?.name,
        groupBy = this[UserPreferences.groupBy]?.name,
        direction = this[UserPreferences.direction]?.name,
        aiSummaryEnabled = this[UserPreferences.aiSummaryEnabled],
    )

    private fun parseListColor(raw: String?): ListColor? =
        raw?.let { s -> ListColor.entries.firstOrNull { it.name == s } }

    private fun decimal(value: Double?): BigDecimal =
        BigDecimal.valueOf(value ?: 0.0).setScale(2, RoundingMode.HALF_UP)
}
