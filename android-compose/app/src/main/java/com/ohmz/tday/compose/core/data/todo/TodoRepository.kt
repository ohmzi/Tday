package com.ohmz.tday.compose.core.data.todo

import android.util.Log
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.floaterFromCache
import com.ohmz.tday.compose.core.data.cache.floaterToCache
import com.ohmz.tday.compose.core.data.cache.listFromCache
import com.ohmz.tday.compose.core.data.cache.mapFloaterDto
import com.ohmz.tday.compose.core.data.cache.mapTodoDto
import com.ohmz.tday.compose.core.data.cache.orderListsLikeWeb
import com.ohmz.tday.compose.core.data.cache.todoFromCache
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CreateFloaterRequest
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.DeleteFloaterRequest
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.FloaterCompleteRequest
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceDeleteRequest
import com.ohmz.tday.compose.core.model.TodoInstanceUpdateRequest
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoSummaryRequest
import com.ohmz.tday.compose.core.model.TodoSummaryResponse
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import com.ohmz.tday.compose.core.model.UpdateFloaterRequest
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.feature.widget.FloaterTasksWidgetRefresher
import com.ohmz.tday.compose.feature.widget.TodayTasksWidgetRefresher
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import com.ohmz.tday.shared.summary.SummaryEngine
import com.ohmz.tday.shared.summary.SummaryScope
import com.ohmz.tday.shared.summary.SummaryTaskInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val settingsRepository: SettingsRepository,
    private val todayTasksWidgetRefresher: TodayTasksWidgetRefresher,
    private val floaterTasksWidgetRefresher: FloaterTasksWidgetRefresher,
) {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    private suspend fun refreshTodayWidgetNow() {
        withContext(NonCancellable) {
            runCatching { todayTasksWidgetRefresher.refreshNow() }
        }
    }

    private suspend fun refreshFloaterWidgetNow() {
        withContext(NonCancellable) {
            runCatching { floaterTasksWidgetRefresher.refreshNow() }
        }
    }

    suspend fun fetchDashboardSummary(): DashboardSummary {
        return buildDashboardSummary(cacheManager.loadOfflineState())
    }

    suspend fun fetchDashboardSummaryCached(): DashboardSummary {
        return buildDashboardSummary(cacheManager.loadOfflineState())
    }

    fun fetchDashboardSummarySnapshot(): DashboardSummary {
        return buildDashboardSummary(cacheManager.loadOfflineStateBlocking())
    }

    suspend fun fetchTodos(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = cacheManager.loadOfflineState(),
            mode = mode,
            listId = listId,
        )
    }

    suspend fun fetchTodosCached(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        // Reading the whole offline cache is blocking Room I/O; keep it off the caller's
        // (often Main) dispatcher. fetchTodosSnapshot below is the deliberately-synchronous
        // variant for widget/Glance contexts that can't suspend.
        val state = withContext(Dispatchers.IO) { cacheManager.loadOfflineState() }
        return buildTodosForMode(
            state = state,
            mode = mode,
            listId = listId,
        )
    }

    fun fetchTodosSnapshot(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = cacheManager.loadOfflineStateBlocking(),
            mode = mode,
            listId = listId,
        )
    }

    suspend fun createTodo(payload: CreateTaskPayload) {
        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDue = payload.due ?: ZonedDateTime.now(zoneId).plusHours(1).toInstant()
        val normalizedRrule = payload.rrule?.takeIf { it.isNotBlank() }
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
                dueEpochMs = normalizedDue.toEpochMilli(),
                rrule = normalizedRrule,
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
                    dueEpochMs = normalizedDue.toEpochMilli(),
                    rrule = normalizedRrule,
                    listId = normalizedListId,
                ),
            )
        }
        refreshTodayWidgetNow()

        if (syncManager.isLocalMode()) return

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(
                LOCAL_FLOATER_LIST_PREFIX
            )
        ) {
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
                        due = normalizedDue.toString(),
                        rrule = normalizedRrule,
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
        }.onFailure {
            // Creates are intentionally never dropped (isLikelyUnrecoverableMutationError
            // treats CREATE_TODO as recoverable), so the pending mutation is retained for
            // background-sync replay. Log it so a permanently-rejected create is at least
            // diagnosable instead of failing completely silently.
            Log.w(LOG_TAG, "createTodo deferred reason=${it.javaClass.simpleName}")
        }
    }

    suspend fun createFloater(payload: CreateTaskPayload) {
        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val localFloaterId = "$LOCAL_FLOATER_PREFIX${UUID.randomUUID()}"
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()

        cacheManager.updateOfflineState { state ->
            val newFloater = CachedFloaterRecord(
                id = localFloaterId,
                canonicalId = localFloaterId,
                title = trimmedTitle,
                description = normalizedDescription,
                priority = normalizedPriority,
                pinned = false,
                completed = false,
                listId = normalizedListId,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                floaters = state.floaters + newFloater,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.CREATE_FLOATER,
                    targetId = localFloaterId,
                    timestampEpochMs = timestampMs,
                    title = trimmedTitle,
                    description = normalizedDescription,
                    priority = normalizedPriority,
                    listId = normalizedListId,
                ),
            )
        }
        refreshFloaterWidgetNow()

        if (syncManager.isLocalMode()) return

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(
                LOCAL_FLOATER_LIST_PREFIX
            )
        ) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        runCatching {
            requireApiBody(
                api.createFloater(
                    CreateFloaterRequest(
                        title = trimmedTitle,
                        description = normalizedDescription,
                        priority = normalizedPriority,
                        listID = normalizedListId,
                    ),
                ),
                "Could not create floater",
            ).floater
        }.onSuccess { createdDto ->
            if (createdDto == null) return@onSuccess
            val createdFloater = mapFloaterDto(createdDto)
            cacheManager.updateOfflineState { state ->
                val remapped = replaceLocalFloaterId(
                    state = state,
                    localFloaterId = localFloaterId,
                    serverFloaterId = createdFloater.canonicalId,
                )
                remapped.copy(
                    floaters = remapped.floaters.map {
                        if (it.canonicalId == createdFloater.canonicalId) {
                            floaterToCache(createdFloater)
                        } else {
                            it
                        }
                    },
                    pendingMutations = remapped.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }.onFailure {
            // See createTodo: creates are retained for background-sync replay; log so a
            // permanently-rejected create is diagnosable rather than silently retried.
            Log.w(LOG_TAG, "createFloater deferred reason=${it.javaClass.simpleName}")
        }
    }

    suspend fun updateTodo(todo: TodoItem, payload: CreateTaskPayload) {
        val canonicalId = todo.canonicalId
        if (canonicalId.isBlank()) return

        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDue =
            payload.due ?: todo.due ?: ZonedDateTime.now(zoneId).plusHours(1).toInstant()
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
            refreshTodayWidgetNow()
            if (syncManager.isLocalMode()) return
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
        refreshTodayWidgetNow()

        if (syncManager.isLocalMode()) return

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(
                LOCAL_FLOATER_LIST_PREFIX
            )
        ) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        val descriptionForApi = normalizedDescription ?: if (todo.description != null) "" else null
        val rruleForApi = normalizedRrule ?: if (!todo.rrule.isNullOrBlank()) "" else null
        val listIdForApi = normalizedListId ?: if (!todo.listId.isNullOrBlank()) "" else null

        val immediateError = runCatching {
            if (instanceDateEpochMs != null) {
                requireApiBody(
                    api.patchTodoInstanceByBody(
                        TodoInstanceUpdateRequest(
                            todoId = canonicalId,
                            instanceDate = Instant.ofEpochMilli(instanceDateEpochMs).toString(),
                            title = trimmedTitle,
                            description = descriptionForApi,
                            priority = normalizedPriority,
                            due = normalizedDue.toString(),
                        ),
                    ),
                    "Could not update recurring task instance",
                )
            } else {
                requireApiBody(
                    api.patchTodoByBody(
                        UpdateTodoRequest(
                            id = canonicalId,
                            title = trimmedTitle,
                            description = descriptionForApi,
                            priority = normalizedPriority,
                            due = normalizedDue.toString(),
                            rrule = rruleForApi,
                            listID = listIdForApi,
                            dateChanged = true,
                            rruleChanged = true,
                            instanceDate = null,
                        ),
                    ),
                    "Could not update task",
                )
            }
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
            Log.w(LOG_TAG, "updateTodo deferred reason=${immediateError.javaClass.simpleName}")
        }
    }

    suspend fun updateFloater(floater: TodoItem, payload: CreateTaskPayload) {
        val canonicalId = floater.canonicalId
        if (canonicalId.isBlank()) return
        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDescription = payload.description?.trim()?.ifBlank { null }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_FLOATER,
            targetId = canonicalId,
            timestampEpochMs = timestampMs,
            title = trimmedTitle,
            description = normalizedDescription,
            priority = normalizedPriority,
            listId = normalizedListId,
        )

        if (canonicalId.startsWith(LOCAL_FLOATER_PREFIX)) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    floaters = state.floaters.map { cached ->
                        if (cached.canonicalId == canonicalId) {
                            cached.copy(
                                title = trimmedTitle,
                                description = normalizedDescription,
                                priority = normalizedPriority,
                                listId = normalizedListId,
                                updatedAtEpochMs = timestampMs,
                            )
                        } else {
                            cached
                        }
                    },
                    pendingMutations = state.pendingMutations.map { mutation ->
                        if (mutation.kind == MutationKind.CREATE_FLOATER && mutation.targetId == canonicalId) {
                            mutation.copy(
                                title = trimmedTitle,
                                description = normalizedDescription,
                                priority = normalizedPriority,
                                listId = normalizedListId,
                                timestampEpochMs = timestampMs,
                            )
                        } else {
                            mutation
                        }
                    },
                )
            }
            refreshFloaterWidgetNow()
            if (syncManager.isLocalMode()) return
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        cacheManager.updateOfflineState { state ->
            state.copy(
                floaters = state.floaters.map { cached ->
                    if (cached.canonicalId == canonicalId) {
                        cached.copy(
                            title = trimmedTitle,
                            description = normalizedDescription,
                            priority = normalizedPriority,
                            listId = normalizedListId,
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        cached
                    }
                },
                pendingMutations = state.pendingMutations
                    .filterNot { it.kind == MutationKind.UPDATE_FLOATER && it.targetId == canonicalId } + pendingMutation,
            )
        }
        refreshFloaterWidgetNow()

        if (syncManager.isLocalMode()) return

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(LOCAL_LIST_PREFIX)) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        val descriptionForApi =
            normalizedDescription ?: if (floater.description != null) "" else null
        val listIdForApi = normalizedListId ?: if (!floater.listId.isNullOrBlank()) "" else null
        val immediateError = runCatching {
            requireApiBody(
                api.patchFloaterByBody(
                    UpdateFloaterRequest(
                        id = canonicalId,
                        title = trimmedTitle,
                        description = descriptionForApi,
                        priority = normalizedPriority,
                        listID = listIdForApi,
                    ),
                ),
                "Could not update floater",
            )
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(
                immediateError,
                pendingMutation
            )
        ) {
            throw immediateError
        }

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId })
            }
        } else {
            Log.w(LOG_TAG, "updateFloater deferred reason=${immediateError.javaClass.simpleName}")
        }
    }

    suspend fun moveTodo(todo: TodoItem, due: Instant) {
        val canonicalId = todo.canonicalId
        if (canonicalId.isBlank()) return

        val instanceDateEpochMs = todo.instanceDateEpochMillis
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_TODO,
            targetId = canonicalId,
            timestampEpochMs = timestampMs,
            dueEpochMs = due.toEpochMilli(),
            instanceDateEpochMs = instanceDateEpochMs,
        )

        val isLocalOnly = canonicalId.startsWith(LOCAL_TODO_PREFIX)
        cacheManager.updateOfflineState { state ->
            val hasExistingUpdateMutation = state.pendingMutations.any { mutation ->
                mutation.kind == MutationKind.UPDATE_TODO &&
                        mutation.targetId == canonicalId &&
                        mutation.instanceDateEpochMs == instanceDateEpochMs
            }
            val updatedMutations = state.pendingMutations
                .map { mutation ->
                    when {
                        mutation.kind == MutationKind.CREATE_TODO && mutation.targetId == canonicalId -> {
                            mutation.copy(
                                dueEpochMs = due.toEpochMilli(),
                                timestampEpochMs = timestampMs,
                            )
                        }

                        mutation.kind == MutationKind.UPDATE_TODO &&
                                mutation.targetId == canonicalId &&
                                mutation.instanceDateEpochMs == instanceDateEpochMs -> {
                            mutation.copy(
                                dueEpochMs = due.toEpochMilli(),
                                timestampEpochMs = timestampMs,
                            )
                        }

                        else -> mutation
                    }
                }
            state.copy(
                todos = state.todos.map { cached ->
                    val isTarget = cached.canonicalId == canonicalId &&
                            (instanceDateEpochMs == null || cached.instanceDateEpochMs == instanceDateEpochMs)
                    if (isTarget) {
                        cached.copy(
                            dueEpochMs = due.toEpochMilli(),
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        cached
                    }
                },
                pendingMutations = if (isLocalOnly || hasExistingUpdateMutation) {
                    updatedMutations
                } else {
                    updatedMutations + pendingMutation
                },
            )
        }
        refreshTodayWidgetNow()

        if (syncManager.isLocalMode()) return

        if (isLocalOnly) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
            return
        }

        val immediateError = runCatching {
            if (instanceDateEpochMs != null) {
                requireApiBody(
                    api.patchTodoInstanceByBody(
                        TodoInstanceUpdateRequest(
                            todoId = canonicalId,
                            instanceDate = Instant.ofEpochMilli(instanceDateEpochMs).toString(),
                            due = due.toString(),
                        ),
                    ),
                    "Could not reschedule recurring task instance",
                )
            } else {
                requireApiBody(
                    api.patchTodoByBody(
                        UpdateTodoRequest(
                            id = canonicalId,
                            due = due.toString(),
                            dateChanged = true,
                            instanceDate = null,
                        ),
                    ),
                    "Could not reschedule task",
                )
            }
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(
                immediateError,
                pendingMutation
            )
        ) {
            throw immediateError
        }

        if (immediateError == null) {
            cacheManager.updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        } else {
            Log.w(LOG_TAG, "moveTodo deferred reason=${immediateError.javaClass.simpleName}")
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
            state.withDeletedTodoCached(
                canonicalId = canonicalId,
                instanceDateEpochMs = instanceDateEpochMs,
                isRecurringInstanceDelete = isRecurringInstanceDelete,
                isLocalOnly = isLocalOnly,
                mutationId = mutationId,
                timestampEpochMs = timestampMs,
            )
        }
        refreshTodayWidgetNow()

        if (syncManager.isLocalMode()) return

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

    suspend fun deleteFloater(floater: TodoItem) {
        val timestampMs = System.currentTimeMillis()
        val canonicalId = floater.canonicalId
        val mutationId = UUID.randomUUID().toString()

        cacheManager.updateOfflineState { state ->
            val isLocalOnly = canonicalId.startsWith(LOCAL_FLOATER_PREFIX)
            val prunedFloaters = state.floaters.filterNot { it.canonicalId == canonicalId }
            val prunedCompleted =
                state.completedFloaters.filterNot { it.originalFloaterId == canonicalId }

            if (isLocalOnly) {
                state.copy(
                    floaters = prunedFloaters,
                    completedFloaters = prunedCompleted,
                    pendingMutations = state.pendingMutations.filterNot { it.targetId == canonicalId },
                )
            } else {
                state.copy(
                    floaters = prunedFloaters,
                    completedFloaters = prunedCompleted,
                    pendingMutations = state.pendingMutations
                        .filterNot { it.kind == MutationKind.DELETE_FLOATER && it.targetId == canonicalId } +
                            PendingMutationRecord(
                                mutationId = mutationId,
                                kind = MutationKind.DELETE_FLOATER,
                                targetId = canonicalId,
                                timestampEpochMs = timestampMs,
                            ),
                )
            }
        }
        refreshFloaterWidgetNow()

        if (syncManager.isLocalMode()) return

        if (canonicalId.startsWith(LOCAL_FLOATER_PREFIX)) return

        runCatching {
            requireApiBody(
                api.deleteFloaterByBody(DeleteFloaterRequest(id = canonicalId)),
                "Could not delete floater",
            )
        }.onSuccess {
            cacheManager.updateOfflineState { state ->
                state.copy(pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId })
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
                dueEpochMs = todo.due?.toEpochMilli(),
                completedAtEpochMs = timestampMs,
                rrule = todo.rrule,
                instanceDateEpochMs = todo.instanceDateEpochMillis,
                listId = todo.listId,
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
        refreshTodayWidgetNow()

        if (syncManager.isLocalMode()) return

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

    suspend fun completeFloater(floater: TodoItem) {
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        cacheManager.updateOfflineState { state ->
            val updatedFloaters = state.floaters.map {
                if (it.canonicalId == floater.canonicalId) {
                    it.copy(completed = true, updatedAtEpochMs = timestampMs)
                } else {
                    it
                }
            }
            val completedId = "$LOCAL_COMPLETED_FLOATER_PREFIX${UUID.randomUUID()}"
            val listMeta =
                floater.listId?.let { listId -> state.floaterLists.firstOrNull { it.id == listId } }
            val completedItem = com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord(
                id = completedId,
                originalFloaterId = floater.canonicalId,
                title = floater.title,
                description = floater.description,
                priority = floater.priority,
                completedAtEpochMs = timestampMs,
                listId = floater.listId,
                listName = listMeta?.name,
                listColor = listMeta?.color,
            )

            state.copy(
                floaters = updatedFloaters,
                completedFloaters = state.completedFloaters + completedItem,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.COMPLETE_FLOATER,
                    targetId = floater.canonicalId,
                    timestampEpochMs = timestampMs,
                ),
            )
        }
        refreshFloaterWidgetNow()

        if (syncManager.isLocalMode()) return

        if (floater.canonicalId.startsWith(LOCAL_FLOATER_PREFIX)) return

        runCatching {
            requireApiBody(
                api.completeFloaterByBody(FloaterCompleteRequest(id = floater.canonicalId)),
                "Could not complete floater",
            )
        }.onSuccess {
            cacheManager.updateOfflineState { state ->
                state.copy(pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId })
            }
        }
    }

    suspend fun summarizeTodos(
        mode: TodoListMode,
        listId: String? = null,
    ): TodoSummaryResponse {
        val modeValue = when (mode) {
            TodoListMode.TODAY -> "today"
            TodoListMode.OVERDUE -> "overdue"
            TodoListMode.SCHEDULED -> "scheduled"
            TodoListMode.ALL -> "all"
            TodoListMode.PRIORITY -> "priority"
            TodoListMode.FLOATER -> "floater"
            TodoListMode.LIST -> "list"
        }
        val timeZoneId = ZoneId.systemDefault().id
        val locale = Locale.getDefault().toLanguageTag()

        val canUseAi = !syncManager.isLocalMode() &&
                settingsRepository.aiSummaryConfiguredSnapshot() &&
                settingsRepository.aiSummaryHealthySnapshot()

        if (canUseAi) {
            val aiResult = runCatching {
                requireApiBody(
                    api.summarizeTodos(
                        TodoSummaryRequest(
                            mode = modeValue,
                            listId = listId,
                            timeZone = timeZoneId,
                            locale = locale,
                        ),
                    ),
                    "Could not summarize tasks",
                )
            }
            // A transient AI outage falls back to the on-device engine below.
            aiResult.getOrNull()?.let { return it }
        }

        return summarizeLocally(
            mode = mode,
            modeValue = modeValue,
            listId = listId,
            timeZoneId = timeZoneId,
            locale = locale,
        )
    }

    private suspend fun summarizeLocally(
        mode: TodoListMode,
        modeValue: String,
        listId: String?,
        timeZoneId: String,
        locale: String,
    ): TodoSummaryResponse {
        val state = cacheManager.loadOfflineState()
        val inputs = if (mode == TodoListMode.FLOATER) {
            state.floaters
                .filterNot { it.completed }
                .map { floater ->
                    SummaryTaskInput(
                        title = floater.title,
                        priority = floater.priority,
                        dueEpochMs = null,
                        pinned = floater.pinned,
                        recurring = false,
                        listId = floater.listId,
                        completed = floater.completed,
                        kind = "anytime",
                    )
                }
        } else {
            state.todos
                .filterNot { it.completed }
                .map { todo ->
                    SummaryTaskInput(
                        title = todo.title,
                        priority = todo.priority,
                        dueEpochMs = todo.dueEpochMs,
                        pinned = todo.pinned,
                        recurring = !todo.rrule.isNullOrBlank(),
                        listId = todo.listId,
                        completed = todo.completed,
                        kind = "task",
                    )
                }
        }

        val scope = SummaryScope.from(modeValue) ?: SummaryScope.TODAY
        val summary = SummaryEngine.summarize(
            tasks = inputs,
            scope = scope,
            nowEpochMs = System.currentTimeMillis(),
            timeZoneId = timeZoneId,
            locale = locale,
            listId = listId,
        )
        return TodoSummaryResponse(
            summary = summary,
            source = "logic",
            mode = modeValue,
            taskCount = inputs.size,
        )
    }

    suspend fun parseTodoTitleNlp(
        text: String,
        referenceDueEpochMs: Long,
    ): TodoTitleNlpResponse? {
        if (text.isBlank()) return null
        // Parsed entirely on-device (offline, no AI/network), so it also works in
        // local mode. `text` is passed raw so the matched-span offsets line up with
        // the title shown in the field for the highlight.
        return runCatching {
            OnDeviceTitleNlpParser.parse(text, referenceDueEpochMs)
        }.getOrNull()
    }

    private fun buildDashboardSummary(state: OfflineSyncState): DashboardSummary {
        val timelineTodos = state.todos
            .asSequence()
            .map(::todoFromCache)
            .filterNot { it.completed }
            .toList()
        val activeFloaters = state.floaters
            .asSequence()
            .map(::floaterFromCache)
            .filterNot { it.completed }
            .toList()
        val todayTodos = timelineTodos.filter(::isTodayTodo)
        val now = Instant.now()
        val scheduledTodos = timelineTodos.filter { isScheduledTodo(it, now) }
        val todoCountsByList = timelineTodos
            .groupingBy { it.listId }
            .eachCount()

        val lists = orderListsLikeWeb(state.lists).map {
            listFromCache(cache = it, todoCountOverride = todoCountsByList[it.id] ?: 0)
        }

        return DashboardSummary(
            todayCount = todayTodos.size,
            scheduledCount = scheduledTodos.size,
            allCount = timelineTodos.size,
            priorityCount = timelineTodos.count { isPriorityTodo(it.priority) },
            floaterCount = activeFloaters.size,
            completedCount = state.completedItems.size,
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
        val activeFloaters = state.floaters
            .asSequence()
            .map(::floaterFromCache)
            .filterNot { it.completed }
            .toList()
        val now = Instant.now()

        return when (mode) {
            TodoListMode.TODAY -> activeTodos.filter(::isTodayTodo)
            TodoListMode.OVERDUE -> activeTodos.filter { isOverdueTodo(it, now) }
            TodoListMode.ALL -> activeTodos
            TodoListMode.SCHEDULED -> activeTodos.filter { isScheduledTodo(it, now) }
            TodoListMode.PRIORITY -> activeTodos.filter { isPriorityTodo(it.priority) }
            TodoListMode.FLOATER -> {
                if (listId.isNullOrBlank()) activeFloaters
                else activeFloaters.filter { it.listId == listId }
            }
            TodoListMode.LIST -> {
                if (listId.isNullOrBlank()) emptyList()
                else activeTodos.filter { it.listId == listId }
            }
        }
    }

    private fun isTodayTodo(todo: TodoItem): Boolean {
        val start = Instant.ofEpochMilli(startOfTodayMillis())
        val end = Instant.ofEpochMilli(endOfTodayMillis())
        val due = todo.due ?: return false
        return due >= start && due <= end
    }

    private fun isScheduledTodo(todo: TodoItem, now: Instant = Instant.now()): Boolean {
        return todo.due?.isBefore(now) == false
    }

    private fun isOverdueTodo(todo: TodoItem, now: Instant = Instant.now()): Boolean {
        return todo.due?.isBefore(now) == true
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

    private fun replaceLocalFloaterId(
        state: OfflineSyncState,
        localFloaterId: String,
        serverFloaterId: String,
    ): OfflineSyncState {
        return state.copy(
            floaters = state.floaters.map {
                if (it.canonicalId == localFloaterId) {
                    it.copy(
                        id = if (it.id == localFloaterId) serverFloaterId else it.id,
                        canonicalId = serverFloaterId,
                    )
                } else {
                    it
                }
            },
            pendingMutations = state.pendingMutations.map {
                if (it.targetId == localFloaterId) {
                    it.copy(targetId = serverFloaterId)
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

internal fun OfflineSyncState.withDeletedTodoCached(
    canonicalId: String,
    instanceDateEpochMs: Long?,
    isRecurringInstanceDelete: Boolean,
    isLocalOnly: Boolean,
    mutationId: String,
    timestampEpochMs: Long,
): OfflineSyncState {
    fun matchesTodo(record: CachedTodoRecord): Boolean {
        if (record.canonicalId != canonicalId) return false
        return !isRecurringInstanceDelete || record.instanceDateEpochMs == instanceDateEpochMs
    }

    fun matchesCompleted(recordOriginalTodoId: String?, recordInstanceDateEpochMs: Long?): Boolean {
        if (recordOriginalTodoId != canonicalId) return false
        return !isRecurringInstanceDelete || recordInstanceDateEpochMs == instanceDateEpochMs
    }

    val prunedTodos = todos.filterNot(::matchesTodo)
    val prunedCompleted = completedItems.filterNot {
        matchesCompleted(it.originalTodoId, it.instanceDateEpochMs)
    }

    if (isLocalOnly) {
        return copy(
            todos = prunedTodos,
            completedItems = prunedCompleted,
            pendingMutations = if (isRecurringInstanceDelete) {
                pendingMutations
            } else {
                pendingMutations.filterNot { it.targetId == canonicalId }
            },
        )
    }

    return copy(
        todos = prunedTodos,
        completedItems = prunedCompleted,
        pendingMutations = pendingMutations
            .filterNot {
                it.kind == MutationKind.DELETE_TODO &&
                    it.targetId == canonicalId &&
                    it.instanceDateEpochMs == instanceDateEpochMs
            } + PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.DELETE_TODO,
            targetId = canonicalId,
            timestampEpochMs = timestampEpochMs,
            instanceDateEpochMs = instanceDateEpochMs,
        ),
    )
}
