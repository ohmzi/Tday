package com.ohmz.tday.compose.core.data.todo

import android.util.Log
import com.ohmz.tday.compose.core.data.CachedFloaterRecord
import com.ohmz.tday.compose.core.data.CachedTodoRecord
import com.ohmz.tday.compose.core.data.MutationKind
import com.ohmz.tday.compose.core.data.OfflineSyncState
import com.ohmz.tday.compose.core.data.PendingMutationRecord
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_FLOATER_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_LIST_PREFIX
import com.ohmz.tday.compose.core.data.cache.LOCAL_TODO_PREFIX
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.cache.floaterFromCache
import com.ohmz.tday.compose.core.data.cache.listFromCache
import com.ohmz.tday.compose.core.data.cache.orderListsLikeWeb
import com.ohmz.tday.compose.core.data.cache.todoFromCache
import com.ohmz.tday.compose.core.data.isLikelyUnrecoverableMutationError
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.settings.SettingsRepository
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.model.CreateTaskPayload
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
import com.ohmz.tday.compose.feature.widget.WidgetRefresher
import com.ohmz.tday.compose.ui.priority.canonicalPriorityValue
import com.ohmz.tday.shared.sort.TaskSortEngine
import com.ohmz.tday.shared.sort.TaskSortKey
import com.ohmz.tday.shared.summary.SummaryEngine
import com.ohmz.tday.shared.summary.SummaryScope
import com.ohmz.tday.shared.summary.SummaryTaskInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
    private val widgetRefresher: WidgetRefresher,
) {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    /**
     * One call repaints every placed widget instance with its OWN kind — there is no longer a
     * per-kind refresher to pick between, and picking wrong is exactly what left a widget rendering
     * as the wrong one. `NonCancellable` because the repaint must still land when the caller's
     * scope is torn down mid-write.
     */
    private suspend fun refreshWidgetsNow() {
        withContext(NonCancellable) {
            runCatching { widgetRefresher.refreshNow() }
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

    /**
     * Today's collapsible "Earlier" bucket. See [todayEarlierItems] for why this
     * is the overdue set clipped to the day boundary instead of the overdue set.
     */
    fun fetchTodayEarlierSnapshot(): List<TodoItem> {
        return todayEarlierItems(
            overdueTodos = buildTodosForMode(
                state = cacheManager.loadOfflineStateBlocking(),
                mode = TodoListMode.OVERDUE,
                listId = null,
            ),
            zoneId = zoneId,
        )
    }

    /** Completed-today count from the local cache, for the Day Done state. */
    fun completedTodayCount(): Int {
        val zone = zoneId
        val today = java.time.LocalDate.now(zone)
        return cacheManager.loadOfflineStateBlocking().completedItems.count { record ->
            java.time.Instant.ofEpochMilli(record.completedAtEpochMs)
                .atZone(zone)
                .toLocalDate() == today
        }
    }

    suspend fun createTodo(payload: CreateTaskPayload) {
        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDue = (payload.due ?: ZonedDateTime.now(zoneId).plusHours(1).toInstant()).truncatedTo(ChronoUnit.MINUTES)
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
        refreshWidgetsNow()

        if (syncManager.isLocalMode()) return

        // Route the create through the single, sync-lock-protected replay path that updates and
        // local-list creates already use. A direct api.createTodo here, on top of the replayable
        // CREATE_TODO pending mutation, was a second server-write path: a concurrent
        // replayPendingMutations sync could re-issue the create before the direct call removed
        // the mutation, producing a duplicate todo that then synced to every device.
        syncManager.syncCachedData(force = true, replayPendingMutations = true)
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
        refreshWidgetsNow()

        if (syncManager.isLocalMode()) return

        // See createTodo: route through the single, sync-lock-protected replay path instead of
        // also firing a direct api.createFloater. The redundant direct call raced the replayable
        // CREATE_FLOATER pending mutation and produced duplicate floaters that synced to every
        // device.
        syncManager.syncCachedData(force = true, replayPendingMutations = true)
    }

    /**
     * A save with no due on a task that has one is the sheet's Schedule toggle turned OFF,
     * and that is a conversion rather than a field write. A scheduled task and a Floater are
     * two entities in two tables (`todos` has a NOT NULL `due`; `floaters` has no due, no
     * rrule and its own list type), so the todo row cannot be made dateless in place and
     * `PATCH /api/todo` has no kind field to carry the intent. The conversion is demote: the
     * todo row is consumed and a floater takes its place.
     *
     * The rest of the save is written first, because `demoteToFloater` copies the todo row's
     * fields server-side; the replay queue preserves that order (this update is stamped before
     * the demote it queues). A recurring task is never converted: the backend refuses to
     * demote one (its series would be silently destroyed), and the sheet does not offer the
     * toggle for it.
     */
    suspend fun updateTodo(todo: TodoItem, payload: CreateTaskPayload) {
        when (taskSaveFor(todo.due, todo.isRecurring, payload.due)) {
            TaskSave.CONVERT_TO_FLOATER -> {
                updateScheduledTodo(todo, payload.copy(due = todo.due, rrule = null))
                demoteTodo(
                    todo.copy(
                        title = payload.title.trim(),
                        description = payload.description?.trim()?.ifBlank { null },
                        priority = canonicalPriorityValue(payload.priority),
                    ),
                )
            }

            TaskSave.UPDATE_KEEPING_SCHEDULE -> updateScheduledTodo(
                todo,
                payload.copy(due = todo.due, rrule = todo.rrule),
            )

            TaskSave.UPDATE -> updateScheduledTodo(todo, payload)
        }
    }

    private suspend fun updateScheduledTodo(todo: TodoItem, payload: CreateTaskPayload) {
        val canonicalId = todo.canonicalId
        if (canonicalId.isBlank()) return

        val trimmedTitle = payload.title.trim()
        if (trimmedTitle.isBlank()) return

        val normalizedPriority = canonicalPriorityValue(payload.priority)
        val normalizedDue =
            (payload.due ?: todo.due ?: ZonedDateTime.now(zoneId).plusHours(1).toInstant())
                .truncatedTo(ChronoUnit.MINUTES)
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
            refreshWidgetsNow()
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
        refreshWidgetsNow()

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
            refreshWidgetsNow()
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
        refreshWidgetsNow()

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
        val due = due.truncatedTo(ChronoUnit.MINUTES)
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
        refreshWidgetsNow()

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

    /**
     * Stage step of the delayed-commit delete flow: prunes the task from the local
     * cache exactly like the prune-half of [deleteTodo], but records nothing for
     * the server (no DELETE pending mutation), so nothing can sync out during the
     * undo window. The removed records are captured so [undoStagedTodoDeletion]
     * can restore them exactly; the commit step is the existing [deleteTodo],
     * whose prune-half re-runs as a no-op on the already-pruned state.
     *
     * Runs inside [OfflineCacheManager.withSyncLock], like
     * [ListRepository.stageDeleteList] and [stageTodoCompletions]: a sync whose
     * read-fetch-merge-save span covers this write would otherwise save a merge
     * built from its pre-fetch snapshot (the list still present) over it.
     */
    suspend fun stageDeleteTodo(todo: TodoItem): StagedTodoDeletion {
        val canonicalId = todo.canonicalId
        val instanceDateEpochMs = todo.instanceDateEpochMillis
        val isRecurringInstanceDelete = todo.isRecurring && instanceDateEpochMs != null
        val isLocalOnly = canonicalId.startsWith(LOCAL_TODO_PREFIX)

        var staged = StagedTodoDeletion()
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { state ->
                val (pruned, removed) = state.withStagedTodoDeletion(
                    canonicalId = canonicalId,
                    instanceDateEpochMs = instanceDateEpochMs,
                    isRecurringInstanceDelete = isRecurringInstanceDelete,
                    isLocalOnly = isLocalOnly,
                )
                staged = removed
                pruned
            }
        }
        refreshWidgetsNow()
        return staged
    }

    /** Undo step: re-inserts the records captured by [stageDeleteTodo]. Idempotent. */
    suspend fun undoStagedTodoDeletion(staged: StagedTodoDeletion) {
        cacheManager.updateOfflineState { state ->
            val todoIds = state.todos.map { it.id }.toSet()
            val completedIds = state.completedItems.map { it.id }.toSet()
            val mutationIds = state.pendingMutations.map { it.mutationId }.toSet()
            state.copy(
                todos = state.todos +
                    staged.removedTodos.filterNot { it.id in todoIds },
                completedItems = state.completedItems +
                    staged.removedCompletedItems.filterNot { it.id in completedIds },
                pendingMutations = state.pendingMutations +
                    staged.removedPendingMutations.filterNot { it.mutationId in mutationIds },
            )
        }
        refreshWidgetsNow()
    }

    /**
     * Stage step of the delayed-commit floater delete; see [stageDeleteTodo] —
     * including its [OfflineCacheManager.withSyncLock].
     */
    suspend fun stageDeleteFloater(floater: TodoItem): StagedFloaterDeletion {
        val canonicalId = floater.canonicalId
        val isLocalOnly = canonicalId.startsWith(LOCAL_FLOATER_PREFIX)

        var staged = StagedFloaterDeletion()
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { state ->
                val (pruned, removed) = state.withStagedFloaterDeletion(
                    canonicalId = canonicalId,
                    isLocalOnly = isLocalOnly,
                )
                staged = removed
                pruned
            }
        }
        refreshWidgetsNow()
        return staged
    }

    /** Undo step: re-inserts the records captured by [stageDeleteFloater]. Idempotent. */
    suspend fun undoStagedFloaterDeletion(staged: StagedFloaterDeletion) {
        cacheManager.updateOfflineState { state ->
            val floaterIds = state.floaters.map { it.id }.toSet()
            val completedIds = state.completedFloaters.map { it.id }.toSet()
            val mutationIds = state.pendingMutations.map { it.mutationId }.toSet()
            state.copy(
                floaters = state.floaters +
                    staged.removedFloaters.filterNot { it.id in floaterIds },
                completedFloaters = state.completedFloaters +
                    staged.removedCompletedFloaters.filterNot { it.id in completedIds },
                pendingMutations = state.pendingMutations +
                    staged.removedPendingMutations.filterNot { it.mutationId in mutationIds },
            )
        }
        refreshWidgetsNow()
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
        refreshWidgetsNow()

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
            state.withDeletedFloaterCached(
                canonicalId = canonicalId,
                isLocalOnly = canonicalId.startsWith(LOCAL_FLOATER_PREFIX),
                mutationId = mutationId,
                timestampEpochMs = timestampMs,
            )
        }
        refreshWidgetsNow()

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

    /**
     * Stage step of the delayed-commit complete, the mirror of [stageDeleteTodo]:
     * writes the completion into the local cache in one pass — the row flips to
     * `completed`, its history row is filed, and the matching mutation is queued
     * with [PendingMutationRecord.staged] set so nothing can sync out during the
     * undo window.
     *
     * This is what lets the completion outlive a re-read. The read path
     * (`buildTodosForMode`) hides a row by its cached `completed` flag, so while
     * a completion lived only in a ViewModel's `items` list, ANY hydrate inside
     * the window put the row straight back — `observeCacheChanges` fires on every
     * `cacheDataVersion` bump, i.e. on every sync, including the echo of the
     * caller's own write — and the deferred commit then took it away again. The
     * hydrator and the tap now agree from the first frame.
     *
     * A batch is the same write folded over its rows, so the single-item and
     * multi-select paths cannot drift. The returned snapshot is what
     * [undoStagedTodoCompletion] puts back.
     *
     * Runs inside [OfflineCacheManager.withSyncLock] — the same mutex a sync
     * holds for its whole read-fetch-merge-save span (see
     * [SyncManager.syncCachedData]) — for the reason
     * [ListRepository.stageDeleteList] states: the sync's final save is built
     * from the snapshot it loaded BEFORE the network phase, so a stage landing
     * inside that span is invisible to the merge and the save writes the older
     * `completed = false` row back while dropping the staged mutation this call
     * just queued. That is the reported "comes back, then leaves again" — and it
     * is most reachable in exactly the "completing too many tasks together" case,
     * because every commit starts a sync.
     */
    suspend fun stageTodoCompletions(todos: List<TodoItem>): StagedTodoCompletion {
        if (todos.isEmpty()) return StagedTodoCompletion()
        val timestampMs = System.currentTimeMillis()
        var staged = StagedTodoCompletion()
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { state ->
                var collected = StagedTodoCompletion()
                val next = todos.fold(state) { current, todo ->
                    val (completed, added) = current.withStagedTodoCompletion(
                        todo = todo,
                        timestampEpochMs = timestampMs,
                        mutationId = UUID.randomUUID().toString(),
                        completedRecordId = "$LOCAL_COMPLETED_PREFIX${UUID.randomUUID()}",
                    )
                    collected = collected + added
                    completed
                }
                staged = collected
                next
            }
        }
        refreshWidgetsNow()
        return staged
    }

    /**
     * Stage step of the delayed-commit floater complete; see
     * [stageTodoCompletions] — including its [OfflineCacheManager.withSyncLock],
     * for the same reason.
     */
    suspend fun stageFloaterCompletions(floaters: List<TodoItem>): StagedFloaterCompletion {
        if (floaters.isEmpty()) return StagedFloaterCompletion()
        val timestampMs = System.currentTimeMillis()
        var staged = StagedFloaterCompletion()
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { state ->
                var collected = StagedFloaterCompletion()
                val next = floaters.fold(state) { current, floater ->
                    val (completed, added) = current.withStagedFloaterCompletion(
                        floater = floater,
                        timestampEpochMs = timestampMs,
                        mutationId = UUID.randomUUID().toString(),
                        completedRecordId = "$LOCAL_COMPLETED_FLOATER_PREFIX${UUID.randomUUID()}",
                    )
                    collected = collected + added
                    completed
                }
                staged = collected
                next
            }
        }
        refreshWidgetsNow()
        return staged
    }

    /**
     * Commit step of the delayed-commit complete: drops the staged marker so the
     * mutations [stageTodoCompletions] already queued replay to the server.
     *
     * A flush rather than a second transform. The stage did the whole write —
     * row, history row and mutation — so re-running the completion here would
     * file a duplicate completed-history row for one completion. The queued
     * mutation carries the occurrence's `instanceDate`, because
     * `PATCH /api/todo/complete` without one writes a history row and leaves the
     * task standing.
     *
     * The un-stage write runs under [OfflineCacheManager.withSyncLock], the same
     * as the stage, and it is the half that matters more of the two. A sync holds
     * the lock across its whole read-fetch-merge-save span and its final save is
     * built from the snapshot it loaded BEFORE the network phase — so a marker
     * flip landing inside that span is saved back as `staged = true`, the queued
     * mutation never replays and the completion never reaches the server. iOS's
     * counterpart (`TodoRepository.unstageMutations`) has always taken the lock
     * for exactly this reason; Android's did not, which left the two clients
     * asymmetric on the one write that decides whether a batch is sent at all.
     * It is most reachable in the "completing too many tasks together" case,
     * because every commit starts a sync and a batch is what keeps one in flight.
     */
    suspend fun commitStagedTodoCompletions(todos: List<TodoItem>) {
        if (todos.isEmpty()) return
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { state ->
                todos.fold(state) { current, todo -> current.withTodoCompletionCommitted(todo) }
            }
        }
        refreshWidgetsNow()
        if (syncManager.isLocalMode()) return
        syncManager.syncCachedData(force = true, replayPendingMutations = true)
    }

    /**
     * Floater counterpart of [commitStagedTodoCompletions] — including its
     * [OfflineCacheManager.withSyncLock], for the same reason.
     */
    suspend fun commitStagedFloaterCompletions(floaters: List<TodoItem>) {
        if (floaters.isEmpty()) return
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { state ->
                floaters.fold(state) { current, floater -> current.withFloaterCompletionCommitted(floater) }
            }
        }
        refreshWidgetsNow()
        if (syncManager.isLocalMode()) return
        syncManager.syncCachedData(force = true, replayPendingMutations = true)
    }

    /**
     * Undo step: reverses [stageTodoCompletions] exactly. Idempotent.
     *
     * Runs inside [OfflineCacheManager.withSyncLock] for the same reason
     * [stageTodoCompletions] and [commitStagedTodoCompletions] do: a sync's
     * final save is built from the snapshot it loaded before its network
     * phase, so an unlocked write landing inside that span is invisible to
     * the merge and gets silently overwritten by the sync's own stale save —
     * the same "comes back, then leaves again" failure mode, just reached
     * from Undo instead of stage/commit.
     */
    suspend fun undoStagedTodoCompletion(staged: StagedTodoCompletion) {
        if (staged.isEmpty) return
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { it.withTodoCompletionUndone(staged) }
        }
        refreshWidgetsNow()
    }

    /**
     * Undo step: reverses [stageFloaterCompletions] exactly. Idempotent.
     *
     * Runs inside [OfflineCacheManager.withSyncLock] — see
     * [undoStagedTodoCompletion] for why.
     */
    suspend fun undoStagedFloaterCompletion(staged: StagedFloaterCompletion) {
        if (staged.isEmpty) return
        cacheManager.withSyncLock {
            cacheManager.updateOfflineState { it.withFloaterCompletionUndone(staged) }
        }
        refreshWidgetsNow()
    }

    suspend fun completeTodo(todo: TodoItem, eagerSync: Boolean = true) {
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        cacheManager.updateOfflineState { state ->
            state.withCompletedTodoCached(
                todo = todo,
                timestampEpochMs = timestampMs,
                mutationId = mutationId,
                completedRecordId = "${com.ohmz.tday.compose.core.data.cache.LOCAL_COMPLETED_PREFIX}${UUID.randomUUID()}",
            )
        }
        refreshWidgetsNow()

        // Widget-initiated completions pass eagerSync=false so the tap returns the moment the
        // optimistic write + widget refresh are done, instead of the Glance action holding the
        // widget's re-render until this network call finishes (which looked like the check-off
        // "not updating", worst offline). The queued COMPLETE_TODO mutation still syncs on the
        // next sync (foreground/periodic/realtime).
        if (!eagerSync) return

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

    suspend fun completeFloater(floater: TodoItem, eagerSync: Boolean = true) {
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        cacheManager.updateOfflineState { state ->
            state.withCompletedFloaterCached(
                floater = floater,
                timestampEpochMs = timestampMs,
                mutationId = mutationId,
                completedRecordId = "$LOCAL_COMPLETED_FLOATER_PREFIX${UUID.randomUUID()}",
            )
        }
        refreshWidgetsNow()

        // See completeTodo: widget taps skip the blocking eager sync so the widget re-renders
        // immediately; the queued COMPLETE_FLOATER mutation syncs on the next sync.
        if (!eagerSync) return

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

    /**
     * Schedules a floater into a real Todo. Optimistically moves the row
     * between the cached silos; the replay case remaps the interim
     * `local-todo-` id (carried in the mutation's spare `name` field) to the
     * server id — CREATE_TODO-style reconciliation.
     */
    suspend fun promoteFloater(floater: TodoItem, dueEpochMs: Long, rrule: String? = null) {
        val dueEpochMs = dueEpochMs - (dueEpochMs % 60_000L)
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val localTodoId = "$LOCAL_TODO_PREFIX${UUID.randomUUID()}"
        cacheManager.updateOfflineState { state ->
            val promoted = CachedTodoRecord(
                id = localTodoId,
                canonicalId = localTodoId,
                title = floater.title,
                description = floater.description,
                priority = floater.priority,
                dueEpochMs = dueEpochMs,
                rrule = rrule,
                instanceDateEpochMs = null,
                pinned = floater.pinned,
                completed = false,
                // Floater lists and todo lists are separate types; membership stays behind.
                listId = null,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                floaters = state.floaters.filterNot { it.canonicalId == floater.canonicalId },
                todos = state.todos + promoted,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.PROMOTE_FLOATER,
                    targetId = floater.canonicalId,
                    timestampEpochMs = timestampMs,
                    dueEpochMs = dueEpochMs,
                    rrule = rrule,
                    name = localTodoId,
                ),
            )
        }
        refreshWidgetsNow()

        if (syncManager.isLocalMode()) return

        syncManager.syncCachedData(force = true, replayPendingMutations = true)
    }

    /**
     * "Let it float": demotes a todo into an Anytime floater. Recurring todos
     * are rejected server-side (their series would be silently destroyed), so
     * callers hide the action for them; this guards anyway.
     */
    suspend fun demoteTodo(todo: TodoItem) {
        if (!todo.rrule.isNullOrBlank()) return
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        val localFloaterId = "$LOCAL_FLOATER_PREFIX${UUID.randomUUID()}"
        cacheManager.updateOfflineState { state ->
            val demoted = CachedFloaterRecord(
                id = localFloaterId,
                canonicalId = localFloaterId,
                title = todo.title,
                description = todo.description,
                priority = todo.priority,
                pinned = todo.pinned,
                completed = false,
                // Todo lists and floater lists are separate types; membership stays behind.
                listId = null,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                todos = state.todos.filterNot { it.canonicalId == todo.canonicalId },
                floaters = state.floaters + demoted,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.DEMOTE_TODO,
                    targetId = todo.canonicalId,
                    timestampEpochMs = timestampMs,
                    name = localFloaterId,
                ),
            )
        }
        refreshWidgetsNow()

        if (syncManager.isLocalMode()) return

        syncManager.syncCachedData(force = true, replayPendingMutations = true)
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
            state.floaters.filterNot { it.completed }.map(CachedFloaterRecord::toSummaryInput)
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

        // Apply the shared cross-platform ordering HERE, at the single source, so every
        // consumer (the scheduled task home screen's "today" preview that renders this list directly, the widgets,
        // and the list screens) shows the same order — due-minute → priority → most-recently
        // modified. Previously this returned raw cache order and only the list-screen section
        // builders re-sorted, so the scheduled task home feed ignored priority.
        return when (mode) {
            TodoListMode.TODAY -> activeTodos.filter(::isTodayTodo).sortedAsTodos()
            TodoListMode.OVERDUE -> activeTodos.filter { isOverdueTodo(it, now) }.sortedAsTodos()
            TodoListMode.ALL -> activeTodos.sortedAsTodos()
            TodoListMode.SCHEDULED -> activeTodos.filter { isScheduledTodo(it, now) }.sortedAsTodos()
            TodoListMode.PRIORITY -> activeTodos.filter { isPriorityTodo(it.priority) }.sortedAsTodos()
            TodoListMode.FLOATER -> {
                val floaters = if (listId.isNullOrBlank()) activeFloaters
                else activeFloaters.filter { it.listId == listId }
                TaskSortEngine.sortedFloaters(floaters) { it.toSortKey() }
            }
            TodoListMode.LIST -> {
                if (listId.isNullOrBlank()) emptyList()
                else activeTodos.filter { it.listId == listId }.sortedAsTodos()
            }
        }
    }

    private fun List<TodoItem>.sortedAsTodos(): List<TodoItem> =
        TaskSortEngine.sortedTodos(this) { it.toSortKey() }

    private fun TodoItem.toSortKey(): TaskSortKey = TaskSortKey(
        id = id,
        pinned = pinned,
        dueEpochMs = due?.toEpochMilli(),
        priorityRank = TaskSortEngine.priorityRank(priority),
        updatedAtEpochMs = updatedAt?.toEpochMilli(),
    )

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

    private companion object {
        const val LOG_TAG = "TodoRepository"
    }
}

/**
 * Local cache records removed by [TodoRepository.stageDeleteTodo], retained so an
 * Undo within the delete-toast window can restore the exact pre-delete state.
 * Nothing here has been sent to the server.
 */
data class StagedTodoDeletion(
    val removedTodos: List<CachedTodoRecord> = emptyList(),
    val removedCompletedItems: List<com.ohmz.tday.compose.core.data.CachedCompletedRecord> = emptyList(),
    val removedPendingMutations: List<PendingMutationRecord> = emptyList(),
)

/** Floater counterpart of [StagedTodoDeletion]; see [TodoRepository.stageDeleteFloater]. */
data class StagedFloaterDeletion(
    val removedFloaters: List<CachedFloaterRecord> = emptyList(),
    val removedCompletedFloaters: List<com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord> = emptyList(),
    val removedPendingMutations: List<PendingMutationRecord> = emptyList(),
)

/**
 * What [TodoRepository.stageTodoCompletions] changed, retained so an Undo inside
 * the completion-toast window can put the cache back exactly as it found it.
 *
 * The completion is a write, not a removal like [StagedTodoDeletion], so the
 * snapshot is three-sided: the row versions the stage replaced (restored), and
 * the completed-history row and the staged mutation it added (dropped). The
 * completion itself has NOT been sent to the server — the queued mutation
 * carries [PendingMutationRecord.staged] and is never replayed while the window
 * is open.
 */
data class StagedTodoCompletion(
    val previousTodos: List<CachedTodoRecord> = emptyList(),
    val addedCompletedItems: List<com.ohmz.tday.compose.core.data.CachedCompletedRecord> = emptyList(),
    val addedPendingMutations: List<PendingMutationRecord> = emptyList(),
) {
    /** True when the stage wrote nothing — a no-op undo/commit pair. */
    val isEmpty: Boolean
        get() = previousTodos.isEmpty() && addedCompletedItems.isEmpty() && addedPendingMutations.isEmpty()
}

/** Floater counterpart of [StagedTodoCompletion]; see [TodoRepository.stageFloaterCompletions]. */
data class StagedFloaterCompletion(
    val previousFloaters: List<CachedFloaterRecord> = emptyList(),
    val addedCompletedFloaters: List<com.ohmz.tday.compose.core.data.CachedCompletedFloaterRecord> = emptyList(),
    val addedPendingMutations: List<PendingMutationRecord> = emptyList(),
) {
    /** True when the stage wrote nothing — a no-op undo/commit pair. */
    val isEmpty: Boolean
        get() = previousFloaters.isEmpty() &&
            addedCompletedFloaters.isEmpty() &&
            addedPendingMutations.isEmpty()
}

/**
 * Today's own "Earlier" bucket, given the raw overdue set.
 *
 * Deliberately NOT the overdue set itself. That set is `isOverdueTodo` --
 * `due < now` -- while Today's `items` is `isTodayTodo`, the whole local
 * calendar day. A task due earlier today and still pending satisfies both: 9am
 * at 3pm is inside today's day AND before the current instant. Rendering the
 * raw overdue set under Earlier therefore put one task in two places at once,
 * its time-of-day bucket and Earlier, which is the duplicate this exists to
 * prevent.
 *
 * Clipping to the day boundary makes the two lists disjoint by construction --
 * `due < startOfToday` cannot hold for anything `isTodayTodo` accepts -- and
 * both read the one boundary `isTodayTodo` itself uses, so they cannot drift
 * apart. iOS (`buildSections`, `.today`: `due < startOfToday`) and web
 * (`buildTimelineSections`: `dayKey < todayKey`) already build theirs this way.
 *
 * A task due earlier today consequently stays in its Morning/Afternoon/Tonight
 * bucket, which is what "today" means on all three platforms. Dropping it from
 * Earlier is the fix; moving it there would be the opposite one.
 */
internal fun todayEarlierItems(
    overdueTodos: List<TodoItem>,
    zoneId: ZoneId,
    now: Instant = Instant.now(),
): List<TodoItem> {
    val startOfToday = ZonedDateTime.ofInstant(now, zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
    return overdueTodos.filter { todo -> todo.due?.isBefore(startOfToday) == true }
}

/** Which write a task-sheet save is. */
internal enum class TaskSave {
    /** The ordinary field write through `PATCH /api/todo`. */
    UPDATE,

    /** Demote: the todo row is consumed and a floater takes its place. */
    CONVERT_TO_FLOATER,

    /**
     * The sheet asked for an unscheduled task on a recurring one. The conversion is refused
     * (a series would be silently destroyed), so the save stays a field write and the
     * schedule — due and recurrence alike — is carried through unchanged.
     */
    UPDATE_KEEPING_SCHEDULE,
}

/**
 * A save that drops the due from a task that has one is the sheet's Schedule toggle turned
 * OFF, and an unscheduled task is a Floater: a todo's `due` is NOT NULL, so the row cannot
 * be made dateless in place and the intent has nowhere to land in `PATCH /api/todo` (its
 * request carries no kind). That save is a conversion — demote — not a field write.
 *
 * A recurring task is never converted: the backend refuses to demote one, because its
 * series would be silently destroyed. The sheet does not offer the toggle for it, so this
 * only answers a stale or restored one — and answers it by not ending the recurrence the
 * toggle-off could not have been honoured for.
 */
internal fun taskSaveFor(
    todoDue: Instant?,
    todoIsRecurring: Boolean,
    payloadDue: Instant?,
): TaskSave =
    when {
        payloadDue != null -> TaskSave.UPDATE
        !todoIsRecurring && todoDue != null -> TaskSave.CONVERT_TO_FLOATER
        else -> TaskSave.UPDATE_KEEPING_SCHEDULE
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

/**
 * Offline cache row -> summary engine input, for the Anytime view.
 *
 * Pulled out of [TodoRepository.summarizeLocally] so the dormancy half of the summary has a seam
 * a unit test can reach: the note only fires when `updatedAtEpochMs` actually crosses the wire,
 * and it did not — the field was declared on the input, defaulted to null here, and every
 * "untouched in months" sentence was unreachable in the shipped app. `0L` is the cache's "never
 * synced" sentinel, mapped to null so an unstamped row reads as ACTIVE rather than as 1970.
 */
internal fun CachedFloaterRecord.toSummaryInput(): SummaryTaskInput = SummaryTaskInput(
    title = title,
    priority = priority,
    dueEpochMs = null,
    pinned = pinned,
    recurring = false,
    listId = listId,
    completed = completed,
    kind = "anytime",
    updatedAtEpochMs = updatedAtEpochMs.takeIf { it > 0L },
)
