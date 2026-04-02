package com.ohmz.tday.compose.core.data.todo

import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.completedFromCache
import com.ohmz.tday.compose.core.data.cache.listFromCache
import com.ohmz.tday.compose.core.data.cache.mapTodoDto
import com.ohmz.tday.compose.core.data.cache.todoFromCache
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceDeleteRequest
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoSummaryRequest
import com.ohmz.tday.compose.core.model.TodoSummaryResponse
import com.ohmz.tday.compose.core.model.TodoTitleNlpRequest
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import android.util.Log
import com.ohmz.tday.compose.core.data.cache.LOCAL_LIST_PREFIX
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val syncManager: SyncManager,
) {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    suspend fun fetchDashboardSummary(): DashboardSummary {
        return buildDashboardSummary(cacheManager.loadOfflineState())
    }

    suspend fun fetchDashboardSummaryCached(): DashboardSummary {
        return buildDashboardSummary(cacheManager.loadOfflineState())
    }

    fun fetchDashboardSummarySnapshot(): DashboardSummary {
        return buildDashboardSummary(cacheManager.loadOfflineState())
    }

    suspend fun fetchTodos(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = cacheManager.loadOfflineState(),
            mode = mode,
            listId = listId,
        )
    }

    suspend fun fetchTodosCached(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = cacheManager.loadOfflineState(),
            mode = mode,
            listId = listId,
        )
    }

    fun fetchTodosSnapshot(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = cacheManager.loadOfflineState(),
            mode = mode,
            listId = listId,
        )
    }

    suspend fun createTodo(payload: CreateTaskPayload) {
        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = when (payload.priority.trim()) {
            "Medium" -> "Medium"
            "High" -> "High"
            else -> "Low"
        }
        val normalizedStart = payload.dtstart
        val normalizedDue = if (payload.due > normalizedStart) {
            payload.due
        } else {
            normalizedStart.plusSeconds(3 * 60 * 60)
        }
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }

        val localTodoId = "$LOCAL_TODO_PREFIX${UUID.randomUUID()}"
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()

        cacheManager.updateOfflineState { state ->
            val newTodo = CachedTodoRecord(
                id = localTodoId,
                canonicalId = localTodoId,
                title = trimmedTitle,
                description = normalizedDescription,
                priority = normalizedPriority,
                dtstartEpochMs = normalizedStart.toEpochMilli(),
                dueEpochMs = normalizedDue.toEpochMilli(),
                rrule = payload.rrule,
                instanceDateEpochMs = null,
                pinned = false,
                completed = false,
                listId = normalizedListId,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                todos = state.todos + newTodo,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.CREATE_TODO,
                    targetId = localTodoId,
                    timestampEpochMs = timestampMs,
                    title = trimmedTitle,
                    description = normalizedDescription,
                    priority = normalizedPriority,
                    dtstartEpochMs = normalizedStart.toEpochMilli(),
                    dueEpochMs = normalizedDue.toEpochMilli(),
                    rrule = payload.rrule,
                    listId = normalizedListId,
                ),
            )
        }

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(LOCAL_LIST_PREFIX)) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        runCatching {
            requireApiBody(
                api.createTodo(
                    CreateTodoRequest(
                        title = trimmedTitle,
                        description = normalizedDescription,
                        priority = normalizedPriority,
                        dtstart = normalizedStart.toString(),
                        due = normalizedDue.toString(),
                        rrule = payload.rrule,
                        listID = normalizedListId,
                    ),
                ),
                "Could not create task",
            ).todo
        }.onSuccess { createdDto ->
            if (createdDto == null) return@onSuccess
            val createdTodo = mapTodoDto(createdDto)
            cacheManager.updateOfflineState { state ->
                val remapped = replaceLocalTodoId(
                    state = state,
                    localTodoId = localTodoId,
                    serverTodoId = createdTodo.canonicalId,
                )
                remapped.copy(
                    todos = remapped.todos.map {
                        if (it.canonicalId == createdTodo.canonicalId) {
                            com.ohmz.tday.compose.core.data.cache.todoToCache(createdTodo)
                        } else {
                            it
                        }
                    },
                    pendingMutations = remapped.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun updateTodo(todo: TodoItem, payload: CreateTaskPayload) {
        val canonicalId = todo.canonicalId
        if (canonicalId.isBlank()) return

        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = when (payload.priority.trim()) {
            "Medium" -> "Medium"
            "High" -> "High"
            else -> "Low"
        }
        val normalizedStart = payload.dtstart
        val normalizedDue = if (payload.due > normalizedStart) {
            payload.due
        } else {
            normalizedStart.plusSeconds(60L * 60L)
        }
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedRrule = payload.rrule?.takeIf { it.isNotBlank() }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val instanceDateEpochMs = todo.instanceDateEpochMillis
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_TODO,
            targetId = canonicalId,
            timestampEpochMs = timestampMs,
            title = trimmedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            dtstartEpochMs = normalizedStart.toEpochMilli(),
            dueEpochMs = normalizedDue.toEpochMilli(),
            rrule = normalizedRrule,
            listId = normalizedListId,
            instanceDateEpochMs = instanceDateEpochMs,
        )

        if (canonicalId.startsWith(LOCAL_TODO_PREFIX)) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    todos = state.todos.map { cached ->
                        val isTarget = cached.canonicalId == canonicalId &&
                            (instanceDateEpochMs == null || cached.instanceDateEpochMs == instanceDateEpochMs)
                        if (isTarget) {
                            cached.copy(
                                title = trimmedTitle,
                                description = normalizedDescription,
                                priority = normalizedPriority,
                                dtstartEpochMs = normalizedStart.toEpochMilli(),
                                dueEpochMs = normalizedDue.toEpochMilli(),
                                rrule = normalizedRrule,
                                listId = normalizedListId,
                                updatedAtEpochMs = timestampMs,
                            )
                        } else {
                            cached
                        }
                    },
                    pendingMutations = state.pendingMutations.map { mutation ->
                        if (mutation.kind == MutationKind.CREATE_TODO && mutation.targetId == canonicalId) {
                            mutation.copy(
                                title = trimmedTitle,
                                description = normalizedDescription,
                                priority = normalizedPriority,
                                dtstartEpochMs = normalizedStart.toEpochMilli(),
                                dueEpochMs = normalizedDue.toEpochMilli(),
                                rrule = normalizedRrule,
                                listId = normalizedListId,
                                timestampEpochMs = timestampMs,
                            )
                        } else {
                            mutation
                        }
                    },
                )
            }
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        cacheManager.updateOfflineState { state ->
            state.copy(
                todos = state.todos.map { cached ->
                    val isTarget = cached.canonicalId == canonicalId &&
                        (instanceDateEpochMs == null || cached.instanceDateEpochMs == instanceDateEpochMs)
                    if (isTarget) {
                        cached.copy(
                            title = trimmedTitle,
                            description = normalizedDescription,
                            priority = normalizedPriority,
                            dtstartEpochMs = normalizedStart.toEpochMilli(),
                            dueEpochMs = normalizedDue.toEpochMilli(),
                            rrule = normalizedRrule,
                            listId = normalizedListId,
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        cached
                    }
                },
                pendingMutations = state.pendingMutations
                    .filterNot {
                        it.kind == MutationKind.UPDATE_TODO &&
                            it.targetId == canonicalId &&
                            it.instanceDateEpochMs == instanceDateEpochMs
                    } + pendingMutation,
            )
        }

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(LOCAL_LIST_PREFIX)) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        val descriptionForApi = normalizedDescription ?: if (todo.description != null) "" else null
        val rruleForApi = normalizedRrule ?: if (!todo.rrule.isNullOrBlank()) "" else null
        val listIdForApi = normalizedListId ?: if (!todo.listId.isNullOrBlank()) "" else null

        val immediateError = runCatching {
            requireApiBody(
                api.patchTodoByBody(
                    UpdateTodoRequest(
                        id = canonicalId,
                        title = trimmedTitle,
                        description = descriptionForApi,
                        priority = normalizedPriority,
                        dtstart = normalizedStart.toString(),
                        due = normalizedDue.toString(),
                        rrule = rruleForApi,
                        listID = listIdForApi,
                        dateChanged = true,
                        rruleChanged = true,
                        instanceDate = instanceDateEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                    ),
                ),
                "Could not update task",
            )
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(immediateError, pendingMutation)) {
            throw immediateError
        }

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        } else {
            Log.w(LOG_TAG, "updateTodo deferred todo=$canonicalId reason=${immediateError.message}")
        }
    }

    suspend fun deleteTodo(todo: TodoItem) {
        val timestampMs = System.currentTimeMillis()
        val canonicalId = todo.canonicalId
        val mutationId = UUID.randomUUID().toString()
        val instanceDateEpochMs = todo.instanceDateEpochMillis
        val isRecurringInstanceDelete = todo.isRecurring && instanceDateEpochMs != null

        cacheManager.updateOfflineState { state ->
            val isLocalOnly = canonicalId.startsWith(LOCAL_TODO_PREFIX)
            val prunedTodos = state.todos.filterNot { it.canonicalId == canonicalId }
            val prunedCompleted = state.completedItems.filterNot { it.originalTodoId == canonicalId }

            if (isLocalOnly) {
                state.copy(
                    todos = prunedTodos,
                    completedItems = prunedCompleted,
                    pendingMutations = state.pendingMutations.filterNot { it.targetId == canonicalId },
                )
            } else {
                state.copy(
                    todos = prunedTodos,
                    completedItems = prunedCompleted,
                    pendingMutations = state.pendingMutations
                        .filterNot {
                            it.kind == MutationKind.DELETE_TODO &&
                                it.targetId == canonicalId &&
                                it.instanceDateEpochMs == instanceDateEpochMs
                        } + PendingMutationRecord(
                        mutationId = mutationId,
                        kind = MutationKind.DELETE_TODO,
                        targetId = canonicalId,
                        timestampEpochMs = timestampMs,
                        instanceDateEpochMs = instanceDateEpochMs,
                    ),
                )
            }
        }

        if (canonicalId.startsWith(LOCAL_TODO_PREFIX)) return

        runCatching {
            if (isRecurringInstanceDelete) {
                requireApiBody(
                    api.deleteTodoInstanceByBody(
                        TodoInstanceDeleteRequest(
                            todoId = canonicalId,
                            instanceDate = Instant.ofEpochMilli(
                                instanceDateEpochMs ?: return@runCatching,
                            ).toString(),
                        ),
                    ),
                    "Could not delete recurring task instance",
                )
            } else {
                requireApiBody(
                    api.deleteTodoByBody(DeleteTodoRequest(id = canonicalId)),
                    "Could not delete task",
                )
            }
        }.onSuccess {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun completeTodo(todo: TodoItem) {
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        cacheManager.updateOfflineState { state ->
            val updatedTodos = state.todos.map {
                if (it.canonicalId == todo.canonicalId) {
                    if (todo.isRecurring && todo.instanceDate != null) {
                        if (it.instanceDateEpochMs == todo.instanceDate.toEpochMilli()) {
                            it.copy(completed = true, updatedAtEpochMs = timestampMs)
                        } else {
                            it
                        }
                    } else {
                        it.copy(completed = true, updatedAtEpochMs = timestampMs)
                    }
                } else {
                    it
                }
            }
            val completedId = "${com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_PREFIX}${UUID.randomUUID()}"
            val listMeta = todo.listId?.let { listId -> state.lists.firstOrNull { it.id == listId } }
            val completedItem = com.ohmz.tday.compose.core.data.CachedCompletedRecord(
                id = completedId,
                originalTodoId = todo.canonicalId,
                title = todo.title,
                description = todo.description,
                priority = todo.priority,
                dtstartEpochMs = todo.dtstart.toEpochMilli(),
                dueEpochMs = todo.due.toEpochMilli(),
                completedAtEpochMs = timestampMs,
                rrule = todo.rrule,
                instanceDateEpochMs = todo.instanceDateEpochMillis,
                listName = listMeta?.name,
                listColor = listMeta?.color,
            )

            val mutationKind = if (todo.isRecurring && todo.instanceDate != null) {
                MutationKind.COMPLETE_TODO_INSTANCE
            } else {
                MutationKind.COMPLETE_TODO
            }

            state.copy(
                todos = updatedTodos,
                completedItems = state.completedItems + completedItem,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = mutationKind,
                    targetId = todo.canonicalId,
                    timestampEpochMs = timestampMs,
                    instanceDateEpochMs = todo.instanceDateEpochMillis,
                ),
            )
        }

        if (todo.canonicalId.startsWith(LOCAL_TODO_PREFIX)) return

        runCatching {
            if (todo.isRecurring && todo.instanceDateEpochMillis != null) {
                requireApiBody(
                    api.completeTodoByBody(
                        TodoCompleteRequest(
                            id = todo.canonicalId,
                            instanceDate = todo.instanceDateEpochMillis?.let {
                                Instant.ofEpochMilli(it).toString()
                            },
                        ),
                    ),
                    "Could not complete recurring task",
                )
            } else {
                requireApiBody(
                    api.completeTodoByBody(TodoCompleteRequest(id = todo.canonicalId)),
                    "Could not complete task",
                )
            }
        }.onSuccess {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun summarizeTodos(
        mode: TodoListMode,
        listId: String? = null,
    ): TodoSummaryResponse {
        val modeValue = when (mode) {
            TodoListMode.TODAY -> "today"
            TodoListMode.OVERDUE -> throw IllegalStateException(
                "Summary is available only for Today, Scheduled, All, and Priority screens",
            )
            TodoListMode.SCHEDULED -> "scheduled"
            TodoListMode.ALL -> "all"
            TodoListMode.PRIORITY -> "priority"
            TodoListMode.LIST -> throw IllegalStateException(
                "Summary is available only for Today, Scheduled, All, and Priority screens",
            )
        }
        return requireApiBody(
            api.summarizeTodos(TodoSummaryRequest(mode = modeValue, listId = listId)),
            "Could not summarize tasks",
        )
    }

    suspend fun parseTodoTitleNlp(
        text: String,
        referenceStartEpochMs: Long,
        referenceDueEpochMs: Long,
    ): TodoTitleNlpResponse? {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return null

        val durationMinutes = ((referenceDueEpochMs - referenceStartEpochMs) / 60_000L)
            .coerceAtLeast(1L)
            .coerceAtMost(24L * 60L)
            .toInt()
        val timezoneOffsetMinutes = ZoneId.systemDefault()
            .rules
            .getOffset(Instant.ofEpochMilli(referenceStartEpochMs))
            .totalSeconds / 60

        return runCatching {
            requireApiBody(
                api.parseTodoTitleNlp(
                    TodoTitleNlpRequest(
                        text = trimmedText,
                        locale = Locale.getDefault().toLanguageTag(),
                        referenceEpochMs = referenceStartEpochMs,
                        timezoneOffsetMinutes = timezoneOffsetMinutes,
                        defaultDurationMinutes = durationMinutes,
                    ),
                ),
                "Could not parse task title",
            )
        }.getOrNull()
    }

    private fun buildDashboardSummary(state: OfflineSyncState): DashboardSummary {
        val timelineTodos = state.todos
            .asSequence()
            .map(::todoFromCache)
            .filterNot { it.completed }
            .toList()
        val todayTodos = timelineTodos.filter(::isTodayTodo)
        val now = Instant.now()
        val scheduledTodos = timelineTodos.filter { isScheduledTodo(it, now) }
        val completedTodos = state.completedItems.map(::completedFromCache)
        val todoCountsByList = timelineTodos
            .groupingBy { it.listId }
            .eachCount()

        val lists = state.lists.map {
            listFromCache(cache = it, todoCountOverride = todoCountsByList[it.id] ?: 0)
        }

        return DashboardSummary(
            todayCount = todayTodos.size,
            scheduledCount = scheduledTodos.size,
            allCount = timelineTodos.size,
            priorityCount = timelineTodos.count { isPriorityTodo(it.priority) },
            completedCount = completedTodos.size,
            lists = lists,
        )
    }

    private fun buildTodosForMode(
        state: OfflineSyncState,
        mode: TodoListMode,
        listId: String?,
    ): List<TodoItem> {
        val allTodos = state.todos
            .asSequence()
            .map(::todoFromCache)
            .toList()
        val activeTodos = allTodos.filterNot { it.completed }
        val now = Instant.now()

        return when (mode) {
            TodoListMode.TODAY -> activeTodos.filter(::isTodayTodo)
            TodoListMode.OVERDUE -> activeTodos.filter { isOverdueTodo(it, now) }
            TodoListMode.ALL -> activeTodos
            TodoListMode.SCHEDULED -> activeTodos.filter { isScheduledTodo(it, now) }
            TodoListMode.PRIORITY -> activeTodos.filter { isPriorityTodo(it.priority) }
            TodoListMode.LIST -> {
                if (listId.isNullOrBlank()) emptyList()
                else activeTodos.filter { it.listId == listId }
            }
        }
    }

    private fun isTodayTodo(todo: TodoItem): Boolean {
        val start = Instant.ofEpochMilli(startOfTodayMillis())
        val end = Instant.ofEpochMilli(endOfTodayMillis())
        return todo.due >= start && todo.dtstart <= end
    }

    private fun isScheduledTodo(todo: TodoItem, now: Instant = Instant.now()): Boolean {
        return !todo.due.isBefore(now)
    }

    private fun isOverdueTodo(todo: TodoItem, now: Instant = Instant.now()): Boolean {
        return todo.due.isBefore(now)
    }

    private fun isPriorityTodo(priority: String?): Boolean {
        val normalized = priority?.trim()?.lowercase() ?: return false
        return normalized == "medium" ||
            normalized == "high" ||
            normalized == "important" ||
            normalized == "urgent"
    }

    private fun startOfTodayMillis(): Long {
        val now = ZonedDateTime.now(zoneId)
        return now.toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun endOfTodayMillis(): Long {
        val now = ZonedDateTime.now(zoneId)
        return now.toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .minusNanos(1)
            .toInstant()
            .toEpochMilli()
    }

    private fun replaceLocalTodoId(
        state: OfflineSyncState,
        localTodoId: String,
        serverTodoId: String,
    ): OfflineSyncState {
        return state.copy(
            todos = state.todos.map {
                if (it.canonicalId == localTodoId) {
                    it.copy(
                        id = if (it.id == localTodoId) serverTodoId else it.id,
                        canonicalId = serverTodoId,
                    )
                } else {
                    it
                }
            },
            pendingMutations = state.pendingMutations.map {
                if (it.targetId == localTodoId) {
                    it.copy(targetId = serverTodoId)
                } else {
                    it
                }
            },
        )
    }

    private companion object {
        const val LOG_TAG = "TodoRepository"
    }
}
