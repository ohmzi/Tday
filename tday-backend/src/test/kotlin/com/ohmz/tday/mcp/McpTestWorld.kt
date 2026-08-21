package com.ohmz.tday.mcp

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.FloaterListResponse
import com.ohmz.tday.models.response.FloaterListTodoResponse
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.ListResponse
import com.ohmz.tday.models.response.ListTodoResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.services.CompletedFloaterService
import com.ohmz.tday.services.CompletedTodoService
import com.ohmz.tday.services.FloaterListService
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.services.IntegrationContextService
import com.ohmz.tday.services.ListService
import com.ohmz.tday.services.OccurrenceOverride
import com.ohmz.tday.services.RecurrenceState
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.shared.model.CompletedFloaterDto
import com.ohmz.tday.shared.model.CompletedTodoDto
import com.ohmz.tday.shared.model.IntegrationApiKeyDto
import com.ohmz.tday.shared.model.IntegrationCapabilitiesDto
import com.ohmz.tday.shared.model.IntegrationContextResponse
import com.ohmz.tday.shared.model.IntegrationUserDto
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * An in-memory T'Day for the MCP tests.
 *
 * The MCP layer's job is deciding *which* service call to make — scheduled vs Anytime,
 * promote vs update, create-list vs refuse — so the fakes here record real state rather
 * than returning canned values, and the assertions read that state back.
 */
class McpTestWorld(
    val userId: String = "user_123",
    val timeZone: String = "UTC",
) {
    val todos = linkedMapOf<String, TodoResponse>()
    val floaters = linkedMapOf<String, FloaterResponse>()
    val lists = linkedMapOf<String, ListResponse>()
    val floaterLists = linkedMapOf<String, FloaterListResponse>()
    val completedTodos = mutableListOf<CompletedTodoDto>()
    val completedFloaters = mutableListOf<CompletedFloaterDto>()
    val recurrenceStates = mutableMapOf<String, RecurrenceState>()

    /** Recorded calls, for asserting that an operation did *not* happen. */
    val promoted = mutableListOf<String>()
    val demoted = mutableListOf<String>()
    val patchedInstances = mutableListOf<Pair<String, LocalDateTime>>()
    val deletedInstances = mutableListOf<Pair<String, LocalDateTime>>()

    private var sequence = 0
    private fun nextId(prefix: String) = "${prefix}_${++sequence}"

    fun addList(name: String, id: String = nextId("list")): ListResponse =
        ListResponse(id = id, name = name).also { lists[id] = it }

    fun addFloaterList(name: String, id: String = nextId("flist")): FloaterListResponse =
        FloaterListResponse(id = id, name = name).also { floaterLists[id] = it }

    fun addTodo(
        title: String,
        due: LocalDateTime,
        rrule: String? = null,
        listId: String? = null,
        completed: Boolean = false,
        id: String = nextId("todo"),
    ): TodoResponse = TodoResponse(
        id = id,
        title = title,
        due = due.toString(),
        rrule = rrule,
        listID = listId,
        completed = completed,
        userID = userId,
    ).also { todos[id] = it }

    fun addFloater(
        title: String,
        listId: String? = null,
        completed: Boolean = false,
        id: String = nextId("floater"),
    ): FloaterResponse = FloaterResponse(
        id = id,
        title = title,
        listID = listId,
        completed = completed,
        userID = userId,
    ).also { floaters[id] = it }

    fun setRecurrence(todoId: String, exdates: List<LocalDateTime> = emptyList(), overrides: List<OccurrenceOverride> = emptyList()) {
        recurrenceStates[todoId] = RecurrenceState(exdates, overrides)
    }

    // ------------------------------------------------------------------- fakes

    val todoService: TodoService = object : TodoService {
        override suspend fun create(
            userId: String, title: String, description: String?, priority: String,
            due: LocalDateTime, rrule: String?, listID: String?,
        ): Either<AppError, TodoResponse> {
            if (listID != null && listID !in lists) return AppError.BadRequest("list not found", "listID").left()
            val todo = TodoResponse(
                id = nextId("todo"), title = title, description = description, priority = priority,
                due = due.toString(), rrule = rrule, listID = listID, userID = userId,
            )
            todos[todo.id] = todo
            return todo.right()
        }

        override suspend fun getByDateRange(userId: String, start: Long, end: Long, timeZone: String) =
            todos.values.toList().right()

        override suspend fun getTimeline(userId: String, timeZone: String, recurringFutureDays: Int) =
            todos.values.toList().right()

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
            val existing = todos[id] ?: return AppError.NotFound("todo not found").left()
            val listId = fields["listID"] as? String
            if (fields.containsKey("listID") && listId != null && listId !in lists) {
                return AppError.BadRequest("list not found", "listID").left()
            }
            todos[id] = existing.copy(
                title = fields["title"] as? String ?: existing.title,
                description = fields["description"] as? String ?: existing.description,
                priority = fields["priority"] as? String ?: existing.priority,
                pinned = fields["pinned"] as? Boolean ?: existing.pinned,
                due = (fields["due"] as? LocalDateTime)?.toString() ?: existing.due,
                rrule = if (fields.containsKey("rrule")) fields["rrule"] as? String else existing.rrule,
                listID = if (fields.containsKey("listID")) listId else existing.listID,
            )
            return Unit.right()
        }

        override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
            (if (todos.remove(id) != null) 1 else 0).right()

        override suspend fun completeTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit> {
            val existing = todos[todoId] ?: return AppError.NotFound("todo not found").left()
            if (instanceDate == null) todos[todoId] = existing.copy(completed = true)
            return Unit.right()
        }

        override suspend fun uncompleteTodo(userId: String, todoId: String, instanceDate: LocalDateTime?): Either<AppError, Unit> {
            val existing = todos[todoId] ?: return AppError.NotFound("todo not found").left()
            if (instanceDate == null) todos[todoId] = existing.copy(completed = false)
            return Unit.right()
        }

        override suspend fun prioritize(userId: String, todoId: String, priority: String) = Unit.right()

        override suspend fun reorder(userId: String, todoId: String, newOrder: Int) = Unit.right()

        override suspend fun getOverdue(userId: String, timeZone: String) = todos.values.toList().right()

        override suspend fun patchInstance(
            userId: String, todoId: String, instanceDate: LocalDateTime, fields: Map<String, Any?>,
        ): Either<AppError, Unit> {
            if (todoId !in todos) return AppError.NotFound("todo not found").left()
            patchedInstances += todoId to instanceDate
            return Unit.right()
        }

        override suspend fun deleteInstance(userId: String, todoId: String, instanceDate: LocalDateTime): Either<AppError, Unit> {
            if (todoId !in todos) return AppError.NotFound("todo not found").left()
            deletedInstances += todoId to instanceDate
            return Unit.right()
        }

        override suspend fun demoteToFloater(userId: String, todoId: String): Either<AppError, FloaterResponse> {
            val existing = todos[todoId] ?: return AppError.NotFound("todo not found").left()
            if (!existing.rrule.isNullOrBlank()) {
                return AppError.BadRequest("a repeating todo cannot be demoted").left()
            }
            todos.remove(todoId)
            demoted += todoId
            val floater = FloaterResponse(
                id = nextId("floater"), title = existing.title, description = existing.description,
                priority = existing.priority, userID = userId,
            )
            floaters[floater.id] = floater
            return floater.right()
        }

        override suspend fun getRecurrenceStates(
            userId: String, todoIds: List<String>,
        ): Either<AppError, Map<String, RecurrenceState>> =
            todoIds.mapNotNull { id -> recurrenceStates[id]?.let { id to it } }.toMap().right()
    }

    val floaterService: FloaterService = object : FloaterService {
        override suspend fun create(
            userId: String, title: String, description: String?, priority: String, listID: String?,
        ): Either<AppError, FloaterResponse> {
            if (listID != null && listID !in floaterLists) return AppError.BadRequest("floater list not found").left()
            val floater = FloaterResponse(
                id = nextId("floater"), title = title, description = description,
                priority = priority, listID = listID, userID = userId,
            )
            floaters[floater.id] = floater
            return floater.right()
        }

        override suspend fun getAll(userId: String) = floaters.values.toList().right()

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
            val existing = floaters[id] ?: return AppError.NotFound("floater not found").left()
            floaters[id] = existing.copy(
                title = fields["title"] as? String ?: existing.title,
                description = fields["description"] as? String ?: existing.description,
                priority = fields["priority"] as? String ?: existing.priority,
                pinned = fields["pinned"] as? Boolean ?: existing.pinned,
                listID = if (fields.containsKey("listID")) fields["listID"] as? String else existing.listID,
            )
            return Unit.right()
        }

        override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
            (if (floaters.remove(id) != null) 1 else 0).right()

        override suspend fun completeFloater(userId: String, floaterId: String): Either<AppError, Unit> {
            val existing = floaters[floaterId] ?: return AppError.NotFound("floater not found").left()
            floaters[floaterId] = existing.copy(completed = true)
            return Unit.right()
        }

        override suspend fun uncompleteFloater(userId: String, floaterId: String): Either<AppError, Unit> {
            val existing = floaters[floaterId] ?: return AppError.NotFound("floater not found").left()
            floaters[floaterId] = existing.copy(completed = false)
            return Unit.right()
        }

        override suspend fun prioritize(userId: String, floaterId: String, priority: String) = Unit.right()

        override suspend fun reorder(userId: String, floaterId: String, newOrder: Int) = Unit.right()

        override suspend fun promoteToTodo(
            userId: String, floaterId: String, due: LocalDateTime, rrule: String?,
        ): Either<AppError, TodoResponse> {
            val existing = floaters[floaterId] ?: return AppError.NotFound("floater not found").left()
            floaters.remove(floaterId)
            promoted += floaterId
            val todo = TodoResponse(
                id = nextId("todo"), title = existing.title, description = existing.description,
                priority = existing.priority, due = due.toString(), rrule = rrule, userID = userId,
            )
            todos[todo.id] = todo
            return todo.right()
        }
    }

    val listService: ListService = object : ListService {
        override suspend fun getAll(userId: String) = lists.values.toList().right()

        override suspend fun getById(userId: String, listId: String) =
            lists[listId]?.right() ?: AppError.NotFound("list not found").left()

        override suspend fun getTodosForList(userId: String, listId: String) = emptyList<ListTodoResponse>().right()

        override suspend fun create(userId: String, name: String, color: String?, iconKey: String?) =
            addList(name).copy(color = color, iconKey = iconKey).also { lists[it.id] = it }.right()

        override suspend fun update(userId: String, id: String, name: String?, color: String?, iconKey: String?) = Unit.right()

        override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
            (if (lists.remove(id) != null) 1 else 0).right()
    }

    val floaterListService: FloaterListService = object : FloaterListService {
        override suspend fun getAll(userId: String) = floaterLists.values.toList().right()

        override suspend fun getById(userId: String, listId: String) =
            floaterLists[listId]?.right() ?: AppError.NotFound("floater list not found").left()

        override suspend fun getFloatersForList(userId: String, listId: String) =
            emptyList<FloaterListTodoResponse>().right()

        override suspend fun create(userId: String, name: String, color: String?, iconKey: String?, reusable: Boolean) =
            addFloaterList(name).copy(color = color, iconKey = iconKey, reusable = reusable)
                .also { floaterLists[it.id] = it }.right()

        override suspend fun update(
            userId: String, id: String, name: String?, color: String?, iconKey: String?, reusable: Boolean?,
        ) = Unit.right()

        override suspend fun resetFloaters(userId: String, listId: String) = 0.right()

        override suspend fun delete(userId: String, id: String): Either<AppError, Int> =
            (if (floaterLists.remove(id) != null) 1 else 0).right()
    }

    val completedTodoService: CompletedTodoService = object : CompletedTodoService {
        override suspend fun getAll(userId: String) = completedTodos.toList().right()
        override suspend fun deleteAll(userId: String) = 0.right()
        override suspend fun deleteById(userId: String, id: String) = 0.right()
        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>) = 0.right()
    }

    val completedFloaterService: CompletedFloaterService = object : CompletedFloaterService {
        override suspend fun getAll(userId: String) = completedFloaters.toList().right()
        override suspend fun deleteAll(userId: String) = 0.right()
        override suspend fun deleteById(userId: String, id: String) = 0.right()
        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>) = 0.right()
    }

    val integrationContextService: IntegrationContextService = object : IntegrationContextService {
        override suspend fun contextFor(
            userId: String, timeZone: String?, apiKey: IntegrationApiKeyDto?,
        ): Either<AppError, IntegrationContextResponse> = IntegrationContextResponse(
            apiKey = apiKey,
            user = IntegrationUserDto(id = userId, username = "ohmz", name = "Omar", timeZone = timeZone),
            serverTime = LocalDateTime.now(ZoneOffset.UTC).toString(),
            capabilities = IntegrationCapabilitiesDto(canWrite = apiKey == null || apiKey.scope == "FULL"),
            lists = lists.values.toList(),
            anytimeLists = floaterLists.values.toList(),
        ).right()
    }
}
