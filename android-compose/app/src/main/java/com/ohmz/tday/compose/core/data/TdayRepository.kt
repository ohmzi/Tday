package com.ohmz.tday.compose.core.data

import android.util.Log
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.AuthSession
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateNoteRequest
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateTaskPayload
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.DeleteTodoRequest
import com.ohmz.tday.compose.core.model.NoteItem
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.RegisterOutcome
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.model.TodoCompleteRequest
import com.ohmz.tday.compose.core.model.TodoPrioritizeRequest
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.model.TodoUncompleteRequest
import com.ohmz.tday.compose.core.model.UpdateListRequest
import com.ohmz.tday.compose.core.model.UpdateTodoRequest
import com.ohmz.tday.compose.core.model.capitalizeFirstListLetter
import com.ohmz.tday.compose.core.network.TdayApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdayRepository @Inject constructor(
    private val api: TdayApiService,
    private val json: Json,
    private val secureConfigStore: SecureConfigStore,
) {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()
    private val syncMutex = Mutex()
    private val cacheDataVersionMutable = MutableStateFlow(0L)
    val cacheDataVersion: StateFlow<Long> = cacheDataVersionMutable.asStateFlow()
    @Volatile
    private var lastPersistedState: OfflineSyncState? = null

    suspend fun restoreSession(): SessionUser? {
        val response = api.getSession()
        if (!response.isSuccessful) return null

        val payload = response.body() ?: return null
        if (payload is JsonNull) return null

        return runCatching {
            json.decodeFromJsonElement<AuthSession>(payload).user
        }.getOrNull()
    }

    suspend fun login(email: String, password: String): AuthResult {
        if (!hasServerConfigured()) {
            return AuthResult.Error("Server URL is not configured")
        }

        val csrf = runCatching {
            requireBody(api.getCsrfToken(), "Could not start sign-in flow").csrfToken
        }.getOrElse { return AuthResult.Error(it.message ?: "Could not start sign-in flow") }

        val requestCallbackUrl = secureConfigStore.buildAbsoluteAppUrl("/app/tday")
            ?: return AuthResult.Error("Server URL is not configured")

        val callback = runCatching {
            api.signInWithCredentials(
                payload =
                    mapOf(
                    "csrfToken" to csrf,
                    "email" to email,
                    "password" to password,
                    "redirect" to "false",
                    "callbackUrl" to requestCallbackUrl,
                ),
            )
        }.getOrElse {
            return AuthResult.Error(
                it.message ?: "Unable to reach server during sign in",
            )
        }

        val callbackUrlFromBody = callback.body()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()
        val callbackUrlFromHeader = callback.headers()["location"].orEmpty()
        val callbackUrlParam = callbackUrlFromBody.ifBlank { callbackUrlFromHeader }
        val params = parseQueryParams(callbackUrlParam)
        val error = params["error"]
        val code = params["code"]

        if (code == "pending_approval") {
            return AuthResult.PendingApproval
        }

        if (!error.isNullOrBlank()) {
            return AuthResult.Error(mapAuthError(error))
        }

        if (!callback.isSuccessful && callback.code() !in 300..399) {
            return AuthResult.Error(extractErrorMessage(callback, "Unable to sign in"))
        }

        val user = runCatching { restoreSession() }.getOrNull()
        return if (user?.id != null) {
            syncTimezone()
            secureConfigStore.saveCredentials(email = email, password = password)
            AuthResult.Success
        } else {
            AuthResult.Error("Sign in failed. Please check backend URL and credentials.")
        }
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
    ): RegisterOutcome {
        val response = runCatching {
            api.register(
                RegisterRequest(
                    fname = firstName,
                    lname = lastName.ifBlank { null },
                    email = email,
                    password = password,
                ),
            )
        }.getOrElse { error ->
            return RegisterOutcome(
                success = false,
                requiresApproval = false,
                message = error.message ?: "Unable to reach server",
            )
        }

        if (!response.isSuccessful) {
            val message = extractErrorMessage(response, "Unable to create account")
            return RegisterOutcome(
                success = false,
                requiresApproval = false,
                message = message,
            )
        }

        val body = response.body()
        secureConfigStore.saveCredentials(
            email = email.trim(),
            password = password,
        )
        return RegisterOutcome(
            success = true,
            requiresApproval = body?.requiresApproval ?: false,
            message = body?.message ?: "Account created",
        )
    }

    suspend fun logout() {
        val csrf = runCatching {
            requireBody(api.getCsrfToken(), "Unable to sign out").csrfToken
        }.getOrElse { return }

        val callbackUrl = secureConfigStore.buildAbsoluteAppUrl("/login") ?: "/login"

        api.signOut(
            payload = mapOf(
                "csrfToken" to csrf,
                "callbackUrl" to callbackUrl,
            ),
        )
        secureConfigStore.clearOfflineSyncState()
    }

    suspend fun syncTimezone() {
        runCatching {
            api.syncTimezone(TimeZone.getDefault().id)
        }
    }

    fun hasServerConfigured(): Boolean = secureConfigStore.hasServerUrl()

    fun getServerUrl(): String? = secureConfigStore.getServerUrl()

    fun hasCachedData(): Boolean {
        val state = loadOfflineState()
        return state.todos.isNotEmpty() ||
            state.lists.isNotEmpty() ||
            state.completedItems.isNotEmpty() ||
            state.pendingMutations.isNotEmpty()
    }

    suspend fun saveServerUrl(rawUrl: String): Result<String> = runCatching {
        val normalizedServerUrl = secureConfigStore.normalizeServerUrl(rawUrl)
            ?: throw ServerProbeException.InvalidUrl()
        val parsedServerUrl = normalizedServerUrl.toHttpUrlOrNull()
            ?: throw ServerProbeException.InvalidUrl()

        ensureSecureTransport(parsedServerUrl)

        val probeUrl = parsedServerUrl.newBuilder()
            .encodedPath(PROBE_PATH)
            .query(null)
            .fragment(null)
            .build()
            .toString()

        val probeResponse = withTimeout(PROBE_TIMEOUT_MS) {
            api.probeServer(probeUrl = probeUrl)
        }

        if (!probeResponse.isSuccessful) {
            throw IllegalStateException(
                extractErrorMessage(
                    probeResponse,
                    "Could not verify server. Check URL and try again.",
                ),
            )
        }

        val probeBody = probeResponse.body()
            ?: throw ServerProbeException.NotTdayServer()

        validateProbeContract(probeBody)
        verifyAndPersistServerTrust(parsedServerUrl, probeResponse)

        val saved = secureConfigStore.saveServerUrl(normalizedServerUrl).getOrThrow()
        secureConfigStore.clearOfflineSyncState()
        saved
    }

    fun resetTrustedServer(rawUrl: String): Result<Unit> {
        return secureConfigStore.clearTrustedServerFingerprintForUrl(rawUrl)
    }

    fun getSavedCredentials(): SavedCredentials? = secureConfigStore.getSavedCredentials()

    fun hasPendingMutations(): Boolean = loadOfflineState().pendingMutations.isNotEmpty()

    suspend fun syncCachedData(
        force: Boolean = false,
        replayPendingMutations: Boolean = true,
    ): Result<Unit> = runCatching {
        syncMutex.withLock {
            syncLocalCache(
                force = force,
                replayPendingMutations = replayPendingMutations,
            )
        }
    }

    suspend fun fetchDashboardSummary(): DashboardSummary {
        return buildDashboardSummary(loadOfflineState())
    }

    suspend fun fetchDashboardSummaryCached(): DashboardSummary {
        return buildDashboardSummary(loadOfflineState())
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
            listFromCache(
                cache = it,
                todoCountOverride = todoCountsByList[it.id] ?: 0,
            )
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

    suspend fun fetchTodos(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = loadOfflineState(),
            mode = mode,
            listId = listId,
        )
    }

    suspend fun fetchTodosCached(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return buildTodosForMode(
            state = loadOfflineState(),
            mode = mode,
            listId = listId,
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
            TodoListMode.ALL -> allTodos
            TodoListMode.SCHEDULED -> activeTodos.filter { isScheduledTodo(it, now) }

            TodoListMode.PRIORITY -> activeTodos.filter { isPriorityTodo(it.priority) }

            TodoListMode.LIST -> {
                if (listId.isNullOrBlank()) {
                    emptyList()
                } else {
                    activeTodos.filter { it.listId == listId }
                }
            }
        }
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

        updateOfflineState { state ->
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
            // Defer remote create until local list gets a server id, then replay queue.
            syncCachedData(
                force = true,
                replayPendingMutations = true,
            )
            return
        }

        runCatching {
            requireBody(
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
            val createdTodo = mapTodo(createdDto)
            updateOfflineState { state ->
                val remapped = replaceLocalTodoId(
                    state = state,
                    localTodoId = localTodoId,
                    serverTodoId = createdTodo.canonicalId,
                )
                remapped.copy(
                    todos = remapped.todos.map {
                        if (it.canonicalId == createdTodo.canonicalId) {
                            todoToCache(createdTodo)
                        } else {
                            it
                        }
                    },
                    pendingMutations = remapped.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun updateTodo(
        todo: TodoItem,
        payload: CreateTaskPayload,
    ) {
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
            updateOfflineState { state ->
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
            syncCachedData(
                force = true,
                replayPendingMutations = true,
            )
            return
        }

        updateOfflineState { state ->
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
                    } +
                    pendingMutation,
            )
        }

        if (!normalizedListId.isNullOrBlank() && normalizedListId.startsWith(LOCAL_LIST_PREFIX)) {
            syncCachedData(
                force = true,
                replayPendingMutations = true,
            )
            return
        }

        val descriptionForApi = normalizedDescription ?: if (todo.description != null) "" else null
        val rruleForApi = normalizedRrule ?: if (!todo.rrule.isNullOrBlank()) "" else null
        val listIdForApi = normalizedListId ?: if (!todo.listId.isNullOrBlank()) "" else null

        val immediateError = runCatching {
            requireBody(
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
            updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        } else {
            Log.w(
                LOG_TAG,
                "updateTodo deferred todo=$canonicalId reason=${immediateError.message}",
            )
        }
    }

    suspend fun deleteTodo(todo: TodoItem) {
        val timestampMs = System.currentTimeMillis()
        val canonicalId = todo.canonicalId
        val mutationId = UUID.randomUUID().toString()
        val instanceDateEpochMs = todo.instanceDateEpochMillis
        val isRecurringInstanceDelete = todo.isRecurring && instanceDateEpochMs != null

        updateOfflineState { state ->
            val isLocalOnly = canonicalId.startsWith(LOCAL_TODO_PREFIX)
            val prunedTodos = state.todos.filterNot { it.canonicalId == canonicalId }
            val prunedCompleted = state.completedItems.filterNot { it.originalTodoId == canonicalId }

            if (isLocalOnly) {
                state.copy(
                    todos = prunedTodos,
                    completedItems = prunedCompleted,
                    pendingMutations = state.pendingMutations.filterNot {
                        it.targetId == canonicalId
                    },
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
                        } +
                        PendingMutationRecord(
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
                requireBody(
                    api.deleteTodoInstanceByBody(
                        DeleteTodoRequest(
                            id = canonicalId,
                            instanceDate = instanceDateEpochMs ?: return@runCatching,
                        ),
                    ),
                    "Could not delete recurring task instance",
                )
            } else {
                requireBody(
                    api.deleteTodoByBody(
                        DeleteTodoRequest(id = canonicalId),
                    ),
                    "Could not delete task",
                )
            }
        }.onSuccess {
            updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun completeTodo(todo: TodoItem) {
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        updateOfflineState { state ->
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
            val completedId = "$LOCAL_COMPLETED_PREFIX${UUID.randomUUID()}"
            val listMeta = todo.listId?.let { listId -> state.lists.firstOrNull { it.id == listId } }
            val completedItem = CachedCompletedRecord(
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
                requireBody(
                    api.completeTodoByBody(
                        TodoCompleteRequest(
                            id = todo.canonicalId,
                            instanceDate = todo.instanceDateEpochMillis,
                        ),
                    ),
                    "Could not complete recurring task",
                )
            } else {
                requireBody(
                    api.completeTodoByBody(
                        TodoCompleteRequest(
                            id = todo.canonicalId,
                        ),
                    ),
                    "Could not complete task",
                )
            }
        }.onSuccess {
            updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun fetchCompletedItems(): List<CompletedItem> {
        return loadOfflineState().completedItems.map(::completedFromCache)
    }

    suspend fun uncomplete(item: CompletedItem) {
        val originalTodoId = item.originalTodoId
            ?: throw IllegalStateException("Completed todo is missing original todo id")
        val timestampMs = System.currentTimeMillis()
        val instanceDateEpochMs = item.instanceDate?.toEpochMilli()
        val mutationId = UUID.randomUUID().toString()

        updateOfflineState { state ->
            val updatedTodos = state.todos.map {
                if (it.canonicalId == originalTodoId) {
                    if (instanceDateEpochMs != null) {
                        if (it.instanceDateEpochMs == instanceDateEpochMs) {
                            it.copy(completed = false, updatedAtEpochMs = timestampMs)
                        } else {
                            it
                        }
                    } else {
                        it.copy(completed = false, updatedAtEpochMs = timestampMs)
                    }
                } else {
                    it
                }
            }
            state.copy(
                todos = updatedTodos,
                completedItems = state.completedItems.filterNot { it.id == item.id },
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.UNCOMPLETE_TODO,
                    targetId = originalTodoId,
                    timestampEpochMs = timestampMs,
                    instanceDateEpochMs = instanceDateEpochMs,
                ),
            )
        }

        if (originalTodoId.startsWith(LOCAL_TODO_PREFIX)) return

        runCatching {
            requireBody(
                api.uncompleteTodoByBody(
                    TodoUncompleteRequest(
                        id = originalTodoId,
                        instanceDate = instanceDateEpochMs,
                    ),
                ),
                "Could not restore task",
            )
        }.onSuccess {
            updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun updateCompletedTodo(
        item: CompletedItem,
        payload: CreateTaskPayload,
    ) {
        val canonicalId = item.originalTodoId ?: return
        val normalizedTitle = payload.title.trim()
        if (normalizedTitle.isBlank()) return
        val normalizedPriority = when (payload.priority.trim()) {
            "Medium" -> "Medium"
            "High" -> "High"
            else -> "Low"
        }
        val normalizedListId = payload.listId?.takeIf { it.isNotBlank() }
        val timestampMs = System.currentTimeMillis()

        updateOfflineState { state ->
            val listMeta = normalizedListId?.let { id -> state.lists.firstOrNull { it.id == id } }
            state.copy(
                todos = state.todos.map { todo ->
                    if (todo.canonicalId == canonicalId) {
                        todo.copy(
                            title = normalizedTitle,
                            description = payload.description,
                            priority = normalizedPriority,
                            dtstartEpochMs = payload.dtstart.toEpochMilli(),
                            dueEpochMs = payload.due.toEpochMilli(),
                            rrule = payload.rrule,
                            listId = normalizedListId,
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        todo
                    }
                },
                completedItems = state.completedItems.map { completed ->
                    if (completed.id == item.id) {
                        completed.copy(
                            title = normalizedTitle,
                            description = payload.description,
                            priority = normalizedPriority,
                            dtstartEpochMs = payload.dtstart.toEpochMilli(),
                            dueEpochMs = payload.due.toEpochMilli(),
                            completedAtEpochMs = completed.completedAtEpochMs.takeIf { it > 0L }
                                ?: timestampMs,
                            rrule = payload.rrule,
                            listName = listMeta?.name,
                            listColor = listMeta?.color,
                        )
                    } else {
                        completed
                    }
                },
            )
        }

        requireBody(
            api.patchCompletedTodoByBody(
                com.ohmz.tday.compose.core.model.UpdateCompletedTodoRequest(
                    id = item.id,
                    title = normalizedTitle,
                    description = payload.description,
                    priority = normalizedPriority,
                    dtstart = payload.dtstart.toString(),
                    due = payload.due.toString(),
                    rrule = payload.rrule,
                    listID = normalizedListId,
                ),
            ),
            "Could not update completed task",
        )
    }

    suspend fun deleteCompletedTodo(item: CompletedItem) {
        val canonicalId = item.originalTodoId

        updateOfflineState { state ->
            state.copy(
                todos = if (canonicalId != null) {
                    state.todos.filterNot { todo -> todo.canonicalId == canonicalId }
                } else {
                    state.todos
                },
                completedItems = state.completedItems.filterNot { it.id == item.id },
                pendingMutations = if (canonicalId != null) {
                    state.pendingMutations.filterNot { mutation -> mutation.targetId == canonicalId }
                } else {
                    state.pendingMutations
                },
            )
        }

        requireBody(
            api.deleteCompletedTodoByBody(
                com.ohmz.tday.compose.core.model.DeleteCompletedTodoRequest(
                    id = item.id,
                ),
            ),
            "Could not delete completed task",
        )
    }

    suspend fun fetchNotes(): List<NoteItem> {
        val response = requireBody(api.getNotes(), "Could not load notes")
        return response.notes.map {
            NoteItem(
                id = it.id,
                name = it.name,
                content = it.content,
            )
        }
    }

    suspend fun createNote(name: String) {
        requireBody(
            api.createNote(CreateNoteRequest(name = name.trim())),
            "Could not create note",
        )
    }

    suspend fun deleteNote(noteId: String) {
        requireBody(api.deleteNote(noteId), "Could not delete note")
    }

    suspend fun fetchLists(): List<ListSummary> {
        val state = loadOfflineState()
        val todoCountsByList = state.todos
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()
        return state.lists.map {
            listFromCache(
                cache = it,
                todoCountOverride = todoCountsByList[it.id] ?: 0,
            )
        }
    }

    suspend fun createList(
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        val normalizedName = capitalizeFirstListLetter(name).trim()
        if (normalizedName.isBlank()) return

        val localListId = "$LOCAL_LIST_PREFIX${UUID.randomUUID()}"
        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        updateOfflineState { state ->
            val newList = CachedListRecord(
                id = localListId,
                name = normalizedName,
                color = color,
                iconKey = iconKey,
                todoCount = 0,
                updatedAtEpochMs = timestampMs,
            )
            state.copy(
                lists = state.lists + newList,
                pendingMutations = state.pendingMutations + PendingMutationRecord(
                    mutationId = mutationId,
                    kind = MutationKind.CREATE_LIST,
                    targetId = localListId,
                    timestampEpochMs = timestampMs,
                    name = normalizedName,
                    color = color,
                    iconKey = iconKey,
                ),
            )
        }

        runCatching {
            requireBody(
                api.createList(
                    CreateListRequest(
                        name = normalizedName,
                        color = color,
                        iconKey = iconKey,
                    ),
                ),
                "Could not create list",
            ).list
        }.onSuccess { createdList ->
            if (createdList == null) return@onSuccess
            val createdAt = parseOptionalInstant(createdList.updatedAt)?.toEpochMilli() ?: timestampMs
            updateOfflineState { state ->
                val remapped = replaceLocalListId(
                    state = state,
                    localListId = localListId,
                    serverListId = createdList.id,
                )
                val todoCount = remapped.todos.count { !it.completed && it.listId == createdList.id }
                remapped.copy(
                    lists = remapped.lists.map { list ->
                        if (list.id == createdList.id) {
                            list.copy(
                                name = createdList.name,
                                color = createdList.color,
                                iconKey = createdList.iconKey ?: list.iconKey,
                                todoCount = todoCount,
                                updatedAtEpochMs = createdAt,
                            )
                        } else {
                            list
                        }
                    },
                    pendingMutations = remapped.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
        }
    }

    suspend fun updateList(
        listId: String,
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        val trimmedName = capitalizeFirstListLetter(name).trim()
        if (listId.isBlank()) return
        require(trimmedName.isNotBlank()) { "List name is required" }
        Log.d(
            LOG_TAG,
            "updateList start listId=$listId name=$trimmedName color=$color iconKey=$iconKey",
        )

        val timestampMs = System.currentTimeMillis()
        val mutationId = UUID.randomUUID().toString()
        if (listId.startsWith(LOCAL_LIST_PREFIX)) {
            updateOfflineState { state ->
                state.copy(
                    lists = state.lists.map { list ->
                        if (list.id == listId) {
                            list.copy(
                                name = trimmedName,
                                color = color ?: list.color,
                                iconKey = iconKey ?: list.iconKey,
                                updatedAtEpochMs = timestampMs,
                            )
                        } else {
                            list
                        }
                    },
                    pendingMutations = state.pendingMutations.map { mutation ->
                        if (mutation.kind == MutationKind.CREATE_LIST && mutation.targetId == listId) {
                            mutation.copy(
                                name = trimmedName,
                                color = color ?: mutation.color,
                                iconKey = iconKey ?: mutation.iconKey,
                                timestampEpochMs = timestampMs,
                            )
                        } else {
                            mutation
                        }
                    },
                )
            }
            iconKey?.takeIf { it.isNotBlank() }?.let {
                secureConfigStore.saveListIcon(listId, it)
            }
            // Best effort: if network is available, push pending list create/update immediately.
            syncCachedData(
                force = true,
                replayPendingMutations = true,
            )
            Log.d(LOG_TAG, "updateList local-list path finished listId=$listId")
            return
        }

        updateOfflineState { state ->
            state.copy(
                lists = state.lists.map { list ->
                    if (list.id == listId) {
                        list.copy(
                            name = trimmedName,
                            color = color ?: list.color,
                            iconKey = iconKey ?: list.iconKey,
                            updatedAtEpochMs = timestampMs,
                        )
                    } else {
                        list
                    }
                },
                pendingMutations = state.pendingMutations
                    .filterNot { it.kind == MutationKind.UPDATE_LIST && it.targetId == listId } +
                    PendingMutationRecord(
                        mutationId = mutationId,
                        kind = MutationKind.UPDATE_LIST,
                        targetId = listId,
                        timestampEpochMs = timestampMs,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    ),
            )
        }

        Log.d(LOG_TAG, "updateList patch /api/list listId=$listId")
        val pendingMutation = PendingMutationRecord(
            mutationId = mutationId,
            kind = MutationKind.UPDATE_LIST,
            targetId = listId,
            timestampEpochMs = timestampMs,
            name = trimmedName,
            color = color,
            iconKey = iconKey,
        )
        val immediateError = runCatching {
            requireBody(
                api.patchListByBody(
                    UpdateListRequest(
                        id = listId,
                        name = trimmedName,
                        color = color,
                        iconKey = iconKey,
                    ),
                ),
                "Could not update list",
            )
        }.exceptionOrNull()

        if (immediateError != null && isLikelyUnrecoverableMutationError(immediateError, pendingMutation)) {
            throw immediateError
        }

        iconKey?.takeIf { it.isNotBlank() }?.let {
            secureConfigStore.saveListIcon(listId, it)
        }

        if (immediateError == null) {
            updateOfflineState { state ->
                state.copy(
                    pendingMutations = state.pendingMutations.filterNot { it.mutationId == mutationId },
                )
            }
            Log.d(LOG_TAG, "updateList success listId=$listId")
        } else {
            Log.w(
                LOG_TAG,
                "updateList deferred listId=$listId reason=${immediateError.message}",
            )
        }
    }

    private suspend fun syncLocalCache(
        force: Boolean,
        replayPendingMutations: Boolean,
    ) {
        var state = loadOfflineState()
        val now = System.currentTimeMillis()
        if (force && (now - state.lastSyncAttemptEpochMs) < MIN_FORCE_SYNC_INTERVAL_MS) {
            return
        }

        val shouldReplayPendingMutations = replayPendingMutations &&
            state.pendingMutations.isNotEmpty()
        val shouldSync = force ||
            shouldReplayPendingMutations ||
            state.lastSuccessfulSyncEpochMs == 0L ||
            (now - state.lastSyncAttemptEpochMs) >= OFFLINE_RESYNC_INTERVAL_MS

        if (!shouldSync) return

        state = state.copy(lastSyncAttemptEpochMs = now)
        saveOfflineState(state)

        val initialPendingCount = state.pendingMutations.size
        val firstRemote = fetchRemoteSnapshot()

        if (initialPendingCount == 0 || !shouldReplayPendingMutations) {
            var mergedWithoutMutations = mergeRemoteWithLocal(
                localState = state,
                remote = firstRemote,
            )
            if (replayPendingMutations && mergedWithoutMutations.pendingMutations.isNotEmpty()) {
                val afterPending = applyPendingMutations(
                    initialState = mergedWithoutMutations,
                    remoteSnapshot = firstRemote,
                )
                val shouldRefetchRemote =
                    afterPending.pendingMutations.size < mergedWithoutMutations.pendingMutations.size
                val latestRemote = if (shouldRefetchRemote) fetchRemoteSnapshot() else firstRemote
                mergedWithoutMutations = mergeRemoteWithLocal(
                    localState = afterPending,
                    remote = latestRemote,
                )
            }
            saveOfflineState(
                mergedWithoutMutations.copy(
                    lastSyncAttemptEpochMs = now,
                    lastSuccessfulSyncEpochMs = now,
                ),
            )
            return
        }

        val afterPending = applyPendingMutations(state, firstRemote)
        saveOfflineState(afterPending.copy(lastSyncAttemptEpochMs = now))
        val shouldRefetchRemote = afterPending.pendingMutations.size < initialPendingCount
        val latestRemote = if (shouldRefetchRemote) {
            fetchRemoteSnapshot()
        } else {
            firstRemote
        }
        val mergedState = mergeRemoteWithLocal(
            localState = afterPending,
            remote = latestRemote,
        ).copy(
            lastSyncAttemptEpochMs = now,
            lastSuccessfulSyncEpochMs = now,
        )

        saveOfflineState(mergedState)
    }

    private suspend fun fetchRemoteSnapshot(): RemoteSnapshot {
        val todos = requireBody(
            api.getTodos(timeline = true),
            "Could not load timeline tasks",
        ).todos.map(::mapTodo)

        val completed = requireBody(
            api.getCompletedTodos(),
            "Could not load completed tasks",
        ).completedTodos.map { dto ->
            CompletedItem(
                id = dto.id,
                originalTodoId = dto.originalTodoID,
                title = dto.title,
                description = dto.description,
                priority = dto.priority,
                dtstart = parseInstant(dto.dtstart),
                due = parseInstant(dto.due),
                completedAt = parseOptionalInstant(dto.completedAt),
                rrule = dto.rrule,
                instanceDate = parseOptionalInstant(dto.instanceDate),
                listName = dto.listName,
                listColor = dto.listColor,
            )
        }

        val lists = requireBody(
            api.getLists(),
            "Could not load lists",
        ).lists.map {
            ListSummary(
                id = it.id,
                name = it.name,
                color = it.color,
                iconKey = it.iconKey ?: secureConfigStore.getListIcon(it.id),
                todoCount = it.todoCount,
                updatedAt = parseOptionalInstant(it.updatedAt),
            )
        }

        return RemoteSnapshot(
            todos = todos,
            completedItems = completed,
            lists = lists,
        )
    }

    private suspend fun applyPendingMutations(
        initialState: OfflineSyncState,
        remoteSnapshot: RemoteSnapshot,
    ): OfflineSyncState {
        if (initialState.pendingMutations.isEmpty()) return initialState

        var state = initialState
        val pending = initialState.pendingMutations.sortedBy { it.timestampEpochMs }.toMutableList()
        val resolvedTodoIds = mutableMapOf<String, String>()
        val resolvedListIds = mutableMapOf<String, String>()
        val remaining = mutableListOf<PendingMutationRecord>()

        for (mutation in pending) {
            val resolvedTargetId = resolveTargetId(
                targetId = mutation.targetId,
                todoIdMap = resolvedTodoIds,
                listIdMap = resolvedListIds,
            )

            val success = runCatching {
                when (mutation.kind) {
                    MutationKind.CREATE_LIST -> {
                        val localListId = mutation.targetId ?: return@runCatching false
                        if (!localListId.startsWith(LOCAL_LIST_PREFIX)) return@runCatching true
                        val localListExists = state.lists.any { it.id == localListId }
                        if (!localListExists) return@runCatching true
                        val response = requireBody(
                            api.createList(
                                CreateListRequest(
                                    name = mutation.name?.trim().orEmpty(),
                                    color = mutation.color,
                                    iconKey = mutation.iconKey,
                                ),
                            ),
                            "Could not create list",
                        )
                        val serverListId = response.list?.id ?: return@runCatching false
                        resolvedListIds[localListId] = serverListId
                        state = replaceLocalListId(state, localListId, serverListId)
                        true
                    }

                    MutationKind.UPDATE_LIST -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_LIST_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.listUpdatedAtById[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireBody(
                            api.patchListByBody(
                                UpdateListRequest(
                                    id = targetId,
                                    name = mutation.name,
                                    color = mutation.color,
                                    iconKey = mutation.iconKey,
                                ),
                            ),
                            "Could not update list",
                        )
                        true
                    }

                    MutationKind.CREATE_TODO -> {
                        val localTodoId = mutation.targetId ?: return@runCatching false
                        if (!localTodoId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching true
                        val localTodoExists = state.todos.any { it.canonicalId == localTodoId }
                        if (!localTodoExists) return@runCatching true
                        val resolvedListId = mutation.listId?.let {
                            resolvedListIds[it] ?: it
                        }
                        if (resolvedListId != null && resolvedListId.startsWith(LOCAL_LIST_PREFIX)) {
                            return@runCatching false
                        }
                        val created = requireBody(
                            api.createTodo(
                                CreateTodoRequest(
                                    title = mutation.title?.trim().orEmpty(),
                                    description = mutation.description,
                                    priority = mutation.priority ?: "Low",
                                    dtstart = Instant.ofEpochMilli(
                                        mutation.dtstartEpochMs ?: System.currentTimeMillis(),
                                    ).toString(),
                                    due = Instant.ofEpochMilli(
                                        mutation.dueEpochMs ?: System.currentTimeMillis(),
                                    ).toString(),
                                    rrule = mutation.rrule,
                                    listID = resolvedListId,
                                ),
                            ),
                            "Could not create task",
                        ).todo ?: return@runCatching false
                        val createdTodo = mapTodo(created)
                        resolvedTodoIds[localTodoId] = createdTodo.canonicalId
                        state = replaceLocalTodoId(state, localTodoId, createdTodo.canonicalId)
                        true
                    }

                    MutationKind.UPDATE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true

                        val resolvedListId = mutation.listId?.let { resolvedListIds[it] ?: it }
                        if (!resolvedListId.isNullOrBlank() && resolvedListId.startsWith(LOCAL_LIST_PREFIX)) {
                            return@runCatching false
                        }

                        val remoteTodo = remoteSnapshot.todos.firstOrNull { it.canonicalId == targetId }
                        val descriptionForApi = mutation.description
                            ?: if (remoteTodo?.description != null) "" else null
                        val rruleForApi = mutation.rrule
                            ?: if (!remoteTodo?.rrule.isNullOrBlank()) "" else null
                        val listIdForApi = resolvedListId
                            ?: if (!remoteTodo?.listId.isNullOrBlank()) "" else null

                        requireBody(
                            api.patchTodoByBody(
                                UpdateTodoRequest(
                                    id = targetId,
                                    title = mutation.title,
                                    description = descriptionForApi,
                                    pinned = mutation.pinned,
                                    priority = mutation.priority,
                                    dtstart = mutation.dtstartEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                                    due = mutation.dueEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                                    rrule = rruleForApi,
                                    listID = listIdForApi,
                                    dateChanged = true,
                                    rruleChanged = true,
                                    instanceDate = mutation.instanceDateEpochMs?.let {
                                        Instant.ofEpochMilli(it).toString()
                                    },
                                ),
                            ),
                            "Could not update task",
                        )
                        true
                    }

                    MutationKind.DELETE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching true

                        val instanceDateEpochMs = mutation.instanceDateEpochMs
                        if (instanceDateEpochMs != null) {
                            requireBody(
                                api.deleteTodoInstanceByBody(
                                    DeleteTodoRequest(
                                        id = targetId,
                                        instanceDate = instanceDateEpochMs,
                                    ),
                                ),
                                "Could not delete recurring task instance",
                            )
                            return@runCatching true
                        }

                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireBody(
                            api.deleteTodoByBody(
                                DeleteTodoRequest(id = targetId),
                            ),
                            "Could not delete task",
                        )
                        true
                    }

                    MutationKind.SET_PINNED -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireBody(
                            api.patchTodoByBody(
                                UpdateTodoRequest(
                                    id = targetId,
                                    pinned = mutation.pinned ?: false,
                                ),
                            ),
                            "Could not update pin",
                        )
                        true
                    }

                    MutationKind.SET_PRIORITY -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true

                        val priority = mutation.priority ?: "Low"
                        val instanceDateEpochMs = mutation.instanceDateEpochMs
                        if (instanceDateEpochMs != null) {
                            requireBody(
                                api.prioritizeTodoByBody(
                                    TodoPrioritizeRequest(
                                        id = targetId,
                                        priority = priority,
                                        instanceDate = instanceDateEpochMs,
                                    ),
                                ),
                                "Could not update priority",
                            )
                        } else {
                            requireBody(
                                api.patchTodoByBody(
                                    UpdateTodoRequest(
                                        id = targetId,
                                        priority = priority,
                                    ),
                                ),
                                "Could not update priority",
                            )
                        }
                        true
                    }

                    MutationKind.COMPLETE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        val remoteUpdatedAt = remoteSnapshot.todoUpdatedAtByCanonical[targetId] ?: 0L
                        if (remoteUpdatedAt > mutation.timestampEpochMs) return@runCatching true
                        requireBody(
                            api.completeTodoByBody(
                                TodoCompleteRequest(
                                    id = targetId,
                                ),
                            ),
                            "Could not complete task",
                        )
                        true
                    }

                    MutationKind.COMPLETE_TODO_INSTANCE -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        requireBody(
                            api.completeTodoByBody(
                                TodoCompleteRequest(
                                    id = targetId,
                                    instanceDate = mutation.instanceDateEpochMs,
                                ),
                            ),
                            "Could not complete recurring task",
                        )
                        true
                    }

                    MutationKind.UNCOMPLETE_TODO -> {
                        val targetId = resolvedTargetId ?: return@runCatching false
                        if (targetId.startsWith(LOCAL_TODO_PREFIX)) return@runCatching false
                        requireBody(
                            api.uncompleteTodoByBody(
                                TodoUncompleteRequest(
                                    id = targetId,
                                    instanceDate = mutation.instanceDateEpochMs,
                                ),
                            ),
                            "Could not restore task",
                        )
                        true
                    }
                }
            }.getOrElse { error ->
                if (isLikelyConnectivityIssue(error)) {
                    remaining.add(resolveLatestMutationSnapshot(state, mutation))
                    remaining.addAll(
                        pending
                            .dropWhile { it.mutationId != mutation.mutationId }
                            .drop(1)
                            .map { queued ->
                                resolveLatestMutationSnapshot(state, queued)
                            },
                    )
                    saveOfflineState(state.copy(pendingMutations = remaining))
                    return state.copy(pendingMutations = remaining)
                }
                if (isLikelyUnrecoverableMutationError(error, mutation)) {
                    Log.w(
                        LOG_TAG,
                        "Dropping unrecoverable mutation kind=${mutation.kind} target=${mutation.targetId}: ${error.message}",
                    )
                    true
                } else {
                    false
                }
            }

            if (!success) {
                remaining.add(resolveLatestMutationSnapshot(state, mutation))
            }
        }

        return state.copy(pendingMutations = remaining)
    }

    private fun mergeRemoteWithLocal(
        localState: OfflineSyncState,
        remote: RemoteSnapshot,
    ): OfflineSyncState {
        val remoteTodos = remote.todos.map(::todoToCache)
        val remoteLists = remote.lists.map(::listToCache)
        val remoteCompleted = remote.completedItems.map(::completedToCache).toMutableList()

        val pendingTodoCanonicalIds = localState.pendingMutations
            .filter { it.kind.affectsTodo() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingListIds = localState.pendingMutations
            .filter { it.kind == MutationKind.CREATE_LIST || it.kind == MutationKind.UPDATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingDeleteAllCanonicals = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_TODO && it.instanceDateEpochMs == null }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingDeleteSpecificKeys = localState.pendingMutations
            .filter { it.kind == MutationKind.DELETE_TODO && it.instanceDateEpochMs != null }
            .mapNotNull { mutation ->
                mutation.targetId?.let { targetId ->
                    todoMergeKey(targetId, mutation.instanceDateEpochMs)
                }
            }
            .toSet()

        val localTodoByKey = localState.todos.associateBy(::todoMergeKey)
        val remoteTodoByKey = remoteTodos.associateBy(::todoMergeKey)
        val mergedTodos = mutableListOf<CachedTodoRecord>()
        val allTodoKeys = LinkedHashSet<String>().apply {
            addAll(remoteTodoByKey.keys)
            addAll(localTodoByKey.keys)
        }

        allTodoKeys.forEach { key ->
            val localTodo = localTodoByKey[key]
            val remoteTodo = remoteTodoByKey[key]

            if (remoteTodo != null) {
                val blockedByPendingDelete =
                    pendingDeleteAllCanonicals.contains(remoteTodo.canonicalId) ||
                        pendingDeleteSpecificKeys.contains(key)
                if (blockedByPendingDelete) {
                    return@forEach
                }
            }

            if (remoteTodo == null && localTodo != null) {
                val hasPendingLocalMutation = pendingTodoCanonicalIds.contains(localTodo.canonicalId)
                val isUnsyncedLocalTodo = localTodo.canonicalId.startsWith(LOCAL_TODO_PREFIX)
                if (!hasPendingLocalMutation && !isUnsyncedLocalTodo) {
                    // Server no longer has this todo (deleted/completed elsewhere), so drop stale local copy.
                    return@forEach
                }
            }

            val merged = when {
                localTodo != null && remoteTodo != null -> {
                    if (shouldPreferLocalTodo(localTodo, remoteTodo, pendingTodoCanonicalIds)) {
                        localTodo
                    } else {
                        remoteTodo
                    }
                }
                localTodo != null -> localTodo
                remoteTodo != null -> remoteTodo
                else -> null
            }

            if (merged != null) {
                mergedTodos.add(merged)
            }
        }

        pendingTodoCanonicalIds.forEach { canonicalId ->
            val localCompletedForTodo = localState.completedItems.filter { it.originalTodoId == canonicalId }
            if (localCompletedForTodo.isNotEmpty()) {
                remoteCompleted.removeAll { it.originalTodoId == canonicalId }
                remoteCompleted.addAll(localCompletedForTodo)
            }
        }

        val localListById = localState.lists.associateBy { it.id }
        val remoteListById = remoteLists.associateBy { it.id }
        val mergedLists = mutableListOf<CachedListRecord>()
        val allListIds = LinkedHashSet<String>().apply {
            addAll(remoteListById.keys)
            addAll(localListById.keys)
        }

        allListIds.forEach { listId ->
            val localList = localListById[listId]
            val remoteList = remoteListById[listId]
            val merged = when {
                localList != null && remoteList != null -> {
                    if (
                        pendingListIds.contains(listId) ||
                        localList.updatedAtEpochMs > remoteList.updatedAtEpochMs
                    ) {
                        localList
                    } else {
                        remoteList
                    }
                }
                localList != null -> localList
                remoteList != null -> remoteList
                else -> null
            }
            if (merged != null) {
                mergedLists.add(merged)
            }
        }

        val todoCountByList = mergedTodos
            .asSequence()
            .filterNot { it.completed }
            .groupingBy { it.listId }
            .eachCount()

        val normalizedLists = mergedLists.map {
            it.copy(todoCount = todoCountByList[it.id] ?: 0)
        }

        val dataMergedState = localState.copy(
            todos = mergedTodos,
            completedItems = remoteCompleted,
            lists = normalizedLists,
        )
        val localWinsMutations = buildLocalWinsMutations(
            mergedState = dataMergedState,
            remote = remote,
        )
        if (localWinsMutations.isEmpty()) return dataMergedState

        return dataMergedState.copy(
            pendingMutations = mergePendingMutations(
                existing = dataMergedState.pendingMutations,
                generated = localWinsMutations,
            ),
        )
    }

    private fun todoMergeKey(todo: CachedTodoRecord): String {
        return todoMergeKey(
            canonicalId = todo.canonicalId,
            instanceDateEpochMs = todo.instanceDateEpochMs,
        )
    }

    private fun todoMergeKey(
        canonicalId: String,
        instanceDateEpochMs: Long?,
    ): String {
        return "$canonicalId::${instanceDateEpochMs ?: Long.MIN_VALUE}"
    }

    private fun shouldPreferLocalTodo(
        localTodo: CachedTodoRecord,
        remoteTodo: CachedTodoRecord,
        pendingTodoCanonicalIds: Set<String>,
    ): Boolean {
        if (pendingTodoCanonicalIds.contains(localTodo.canonicalId)) return true
        return localTodo.updatedAtEpochMs > remoteTodo.updatedAtEpochMs
    }

    private fun buildLocalWinsMutations(
        mergedState: OfflineSyncState,
        remote: RemoteSnapshot,
    ): List<PendingMutationRecord> {
        val existingPending = mergedState.pendingMutations
        val pendingTodoCanonicalIds = existingPending
            .filter { it.kind.affectsTodo() }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingListIds = existingPending
            .filter { it.kind == MutationKind.CREATE_LIST || it.kind == MutationKind.UPDATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()
        val pendingLocalListCreates = existingPending
            .filter { it.kind == MutationKind.CREATE_LIST }
            .mapNotNull { it.targetId }
            .toSet()

        val remoteTodoByKey = remote.todos
            .map(::todoToCache)
            .associateBy(::todoMergeKey)
        val remoteListById = remote.lists
            .map(::listToCache)
            .associateBy { it.id }

        val generated = mutableListOf<PendingMutationRecord>()

        mergedState.todos.forEach { localTodo ->
            if (localTodo.canonicalId.startsWith(LOCAL_TODO_PREFIX)) return@forEach
            if (pendingTodoCanonicalIds.contains(localTodo.canonicalId)) return@forEach

            val remoteTodo = remoteTodoByKey[todoMergeKey(localTodo)] ?: return@forEach
            if (!hasTodoMeaningfulDifferences(local = localTodo, remote = remoteTodo)) {
                return@forEach
            }
            val localUpdatedAt = localTodo.updatedAtEpochMs
            val remoteUpdatedAt = remoteTodo.updatedAtEpochMs
            if (localUpdatedAt <= 0L || localUpdatedAt <= remoteUpdatedAt) return@forEach

            val mutation = if (localTodo.completed != remoteTodo.completed) {
                if (localTodo.completed) {
                    PendingMutationRecord(
                        mutationId = UUID.randomUUID().toString(),
                        kind = if (localTodo.instanceDateEpochMs != null) {
                            MutationKind.COMPLETE_TODO_INSTANCE
                        } else {
                            MutationKind.COMPLETE_TODO
                        },
                        targetId = localTodo.canonicalId,
                        timestampEpochMs = localUpdatedAt,
                        instanceDateEpochMs = localTodo.instanceDateEpochMs,
                    )
                } else {
                    PendingMutationRecord(
                        mutationId = UUID.randomUUID().toString(),
                        kind = MutationKind.UNCOMPLETE_TODO,
                        targetId = localTodo.canonicalId,
                        timestampEpochMs = localUpdatedAt,
                        instanceDateEpochMs = localTodo.instanceDateEpochMs,
                    )
                }
            } else {
                val localListId = localTodo.listId
                if (!localListId.isNullOrBlank() &&
                    localListId.startsWith(LOCAL_LIST_PREFIX) &&
                    !pendingLocalListCreates.contains(localListId)
                ) {
                    return@forEach
                }
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = MutationKind.UPDATE_TODO,
                    targetId = localTodo.canonicalId,
                    timestampEpochMs = localUpdatedAt,
                    title = localTodo.title,
                    description = localTodo.description,
                    priority = localTodo.priority,
                    pinned = localTodo.pinned,
                    dtstartEpochMs = localTodo.dtstartEpochMs,
                    dueEpochMs = localTodo.dueEpochMs,
                    rrule = localTodo.rrule,
                    listId = localTodo.listId,
                    instanceDateEpochMs = localTodo.instanceDateEpochMs,
                )
            }
            generated.add(mutation)
        }

        mergedState.lists.forEach { localList ->
            if (localList.id.startsWith(LOCAL_LIST_PREFIX)) return@forEach
            if (pendingListIds.contains(localList.id)) return@forEach

            val remoteList = remoteListById[localList.id] ?: return@forEach
            if (!hasListMeaningfulDifferences(local = localList, remote = remoteList)) {
                return@forEach
            }
            val localUpdatedAt = localList.updatedAtEpochMs
            val remoteUpdatedAt = remoteList.updatedAtEpochMs
            if (localUpdatedAt <= 0L || localUpdatedAt <= remoteUpdatedAt) return@forEach

            generated.add(
                PendingMutationRecord(
                    mutationId = UUID.randomUUID().toString(),
                    kind = MutationKind.UPDATE_LIST,
                    targetId = localList.id,
                    timestampEpochMs = localUpdatedAt,
                    name = localList.name,
                    color = localList.color,
                    iconKey = localList.iconKey,
                ),
            )
        }

        return generated
    }

    private fun hasTodoMeaningfulDifferences(
        local: CachedTodoRecord,
        remote: CachedTodoRecord,
    ): Boolean {
        return local.title != remote.title ||
            local.description != remote.description ||
            local.priority != remote.priority ||
            local.dtstartEpochMs != remote.dtstartEpochMs ||
            local.dueEpochMs != remote.dueEpochMs ||
            local.rrule != remote.rrule ||
            local.instanceDateEpochMs != remote.instanceDateEpochMs ||
            local.pinned != remote.pinned ||
            local.completed != remote.completed ||
            local.listId != remote.listId
    }

    private fun hasListMeaningfulDifferences(
        local: CachedListRecord,
        remote: CachedListRecord,
    ): Boolean {
        return local.name != remote.name ||
            local.color != remote.color ||
            local.iconKey != remote.iconKey
    }

    private fun mergePendingMutations(
        existing: List<PendingMutationRecord>,
        generated: List<PendingMutationRecord>,
    ): List<PendingMutationRecord> {
        if (generated.isEmpty()) return existing
        val merged = existing.toMutableList()
        generated.forEach { candidate ->
            val replaceIndex = merged.indexOfFirst { existingMutation ->
                shouldReplacePendingMutation(
                    existing = existingMutation,
                    candidate = candidate,
                )
            }
            if (replaceIndex >= 0) {
                merged[replaceIndex] = candidate
            } else {
                merged.add(candidate)
            }
        }
        return merged.sortedBy { it.timestampEpochMs }
    }

    private fun shouldReplacePendingMutation(
        existing: PendingMutationRecord,
        candidate: PendingMutationRecord,
    ): Boolean {
        if (existing.kind != candidate.kind) return false
        if (existing.targetId != candidate.targetId) return false
        return existing.instanceDateEpochMs == candidate.instanceDateEpochMs
    }

    private fun MutationKind.affectsTodo(): Boolean {
        return this == MutationKind.CREATE_TODO ||
            this == MutationKind.UPDATE_TODO ||
            this == MutationKind.DELETE_TODO ||
            this == MutationKind.SET_PINNED ||
            this == MutationKind.SET_PRIORITY ||
            this == MutationKind.COMPLETE_TODO ||
            this == MutationKind.COMPLETE_TODO_INSTANCE ||
            this == MutationKind.UNCOMPLETE_TODO
    }

    private fun replaceLocalListId(
        state: OfflineSyncState,
        localListId: String,
        serverListId: String,
    ): OfflineSyncState {
        return state.copy(
            lists = state.lists.map {
                if (it.id == localListId) {
                    it.copy(id = serverListId)
                } else {
                    it
                }
            },
            todos = state.todos.map {
                if (it.listId == localListId) {
                    it.copy(listId = serverListId)
                } else {
                    it
                }
            },
            pendingMutations = state.pendingMutations.map {
                it.copy(
                    targetId = if (it.targetId == localListId) serverListId else it.targetId,
                    listId = if (it.listId == localListId) serverListId else it.listId,
                )
            },
        )
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

    private fun resolveTargetId(
        targetId: String?,
        todoIdMap: Map<String, String>,
        listIdMap: Map<String, String>,
    ): String? {
        if (targetId == null) return null
        return todoIdMap[targetId] ?: listIdMap[targetId] ?: targetId
    }

    private fun isLikelyConnectivityIssue(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("failed to connect") ||
            message.contains("econnrefused") ||
            message.contains("timed out") ||
            message.contains("unable to resolve host") ||
            message.contains("network is unreachable")
    }

    private fun resolveLatestMutationSnapshot(
        state: OfflineSyncState,
        mutation: PendingMutationRecord,
    ): PendingMutationRecord {
        return state.pendingMutations.firstOrNull { it.mutationId == mutation.mutationId } ?: mutation
    }

    private fun isLikelyUnrecoverableMutationError(
        error: Throwable,
        mutation: PendingMutationRecord,
    ): Boolean {
        if (error is ApiCallException) {
            // Keep pending mutations on auth/session issues so they replay after re-auth.
            if (error.statusCode == 401 || error.statusCode == 403) return false

            // Never aggressively drop offline create flows; preserve user-created local data.
            if (
                mutation.kind == MutationKind.CREATE_LIST ||
                mutation.kind == MutationKind.CREATE_TODO ||
                mutation.kind == MutationKind.UPDATE_LIST ||
                mutation.kind == MutationKind.UPDATE_TODO
            ) {
                return false
            }

            return error.statusCode in 400..499 &&
                error.statusCode != 408 &&
                error.statusCode != 429
        }
        val message = error.message.orEmpty().lowercase()
        return message.contains("bad request") ||
            message.contains("bad / malformed") ||
            message.contains("invalid request") ||
            message.contains("invalid request body") ||
            message.contains("you provided invalid values") ||
            message.contains("method not allowed") ||
            message.contains("http 405") ||
            message.contains("record to delete does not exist") ||
            message.contains("record to update not found") ||
            message.contains("todo id is required")
    }

    private fun updateOfflineState(transform: (OfflineSyncState) -> OfflineSyncState): OfflineSyncState {
        val next = transform(loadOfflineState())
        saveOfflineState(next)
        return next
    }

    private fun loadOfflineState(): OfflineSyncState {
        val decoded = decodeOfflineSyncState(
            raw = secureConfigStore.getOfflineSyncStateRaw().orEmpty(),
        )
        lastPersistedState = decoded
        return decoded
    }

    private fun saveOfflineState(state: OfflineSyncState) {
        val previous = lastPersistedState ?: decodeOfflineSyncState(
            raw = secureConfigStore.getOfflineSyncStateRaw().orEmpty(),
        )
        if (previous == state) return
        secureConfigStore.saveOfflineSyncStateRaw(json.encodeToString(state))
        lastPersistedState = state
        if (hasUiDataChanges(previous, state)) {
            cacheDataVersionMutable.value = cacheDataVersionMutable.value + 1L
        }
    }

    private fun decodeOfflineSyncState(raw: String): OfflineSyncState {
        if (raw.isBlank()) return OfflineSyncState()
        return try {
            json.decodeFromString<OfflineSyncState>(raw)
        } catch (_: SerializationException) {
            OfflineSyncState()
        } catch (_: IllegalArgumentException) {
            OfflineSyncState()
        }
    }

    private fun hasUiDataChanges(
        previous: OfflineSyncState,
        next: OfflineSyncState,
    ): Boolean {
        return previous.todos != next.todos ||
            previous.completedItems != next.completedItems ||
            previous.lists != next.lists
    }

    private fun isTodayTodo(todo: TodoItem): Boolean {
        val start = Instant.ofEpochMilli(startOfTodayMillis())
        val end = Instant.ofEpochMilli(endOfTodayMillis())
        return todo.due >= start && todo.dtstart <= end
    }

    private fun isScheduledTodo(todo: TodoItem, now: Instant = Instant.now()): Boolean {
        return !todo.due.isBefore(now)
    }

    private fun isPriorityTodo(priority: String?): Boolean {
        val normalized = priority?.trim()?.lowercase() ?: return false
        return normalized == "medium" ||
            normalized == "high" ||
            normalized == "important" ||
            normalized == "urgent"
    }

    private fun todoToCache(todo: TodoItem): CachedTodoRecord {
        return CachedTodoRecord(
            id = todo.id,
            canonicalId = todo.canonicalId,
            title = todo.title,
            description = todo.description,
            priority = todo.priority,
            dtstartEpochMs = todo.dtstart.toEpochMilli(),
            dueEpochMs = todo.due.toEpochMilli(),
            rrule = todo.rrule,
            instanceDateEpochMs = todo.instanceDateEpochMillis,
            pinned = todo.pinned,
            completed = todo.completed,
            listId = todo.listId,
            updatedAtEpochMs = todo.updatedAt?.toEpochMilli() ?: 0L,
        )
    }

    private fun todoFromCache(cache: CachedTodoRecord): TodoItem {
        return TodoItem(
            id = cache.id,
            canonicalId = cache.canonicalId,
            title = cache.title,
            description = cache.description,
            priority = cache.priority,
            dtstart = Instant.ofEpochMilli(cache.dtstartEpochMs),
            due = Instant.ofEpochMilli(cache.dueEpochMs),
            rrule = cache.rrule,
            instanceDate = cache.instanceDateEpochMs?.let(Instant::ofEpochMilli),
            pinned = cache.pinned,
            completed = cache.completed,
            listId = cache.listId,
            updatedAt = if (cache.updatedAtEpochMs > 0L) {
                Instant.ofEpochMilli(cache.updatedAtEpochMs)
            } else {
                null
            },
        )
    }

    private fun listToCache(list: ListSummary): CachedListRecord {
        return CachedListRecord(
            id = list.id,
            name = list.name,
            color = list.color,
            iconKey = list.iconKey,
            todoCount = list.todoCount,
            updatedAtEpochMs = list.updatedAt?.toEpochMilli() ?: 0L,
        )
    }

    private fun listFromCache(
        cache: CachedListRecord,
        todoCountOverride: Int,
    ): ListSummary {
        return ListSummary(
            id = cache.id,
            name = cache.name,
            color = cache.color,
            iconKey = cache.iconKey,
            todoCount = todoCountOverride,
            updatedAt = if (cache.updatedAtEpochMs > 0L) {
                Instant.ofEpochMilli(cache.updatedAtEpochMs)
            } else {
                null
            },
        )
    }

    private fun completedToCache(item: CompletedItem): CachedCompletedRecord {
        return CachedCompletedRecord(
            id = item.id,
            originalTodoId = item.originalTodoId,
            title = item.title,
            description = item.description,
            priority = item.priority,
            dtstartEpochMs = item.dtstart.toEpochMilli(),
            dueEpochMs = item.due.toEpochMilli(),
            completedAtEpochMs = item.completedAt?.toEpochMilli() ?: 0L,
            rrule = item.rrule,
            instanceDateEpochMs = item.instanceDate?.toEpochMilli(),
            listName = item.listName,
            listColor = item.listColor,
        )
    }

    private fun completedFromCache(cache: CachedCompletedRecord): CompletedItem {
        return CompletedItem(
            id = cache.id,
            originalTodoId = cache.originalTodoId,
            title = cache.title,
            description = cache.description,
            priority = cache.priority,
            dtstart = if (cache.dtstartEpochMs > 0L) {
                Instant.ofEpochMilli(cache.dtstartEpochMs)
            } else {
                Instant.ofEpochMilli(cache.dueEpochMs)
            },
            due = Instant.ofEpochMilli(cache.dueEpochMs),
            completedAt = if (cache.completedAtEpochMs > 0L) {
                Instant.ofEpochMilli(cache.completedAtEpochMs)
            } else {
                null
            },
            rrule = cache.rrule,
            instanceDate = cache.instanceDateEpochMs?.let(Instant::ofEpochMilli),
            listName = cache.listName,
            listColor = cache.listColor,
        )
    }

    private data class RemoteSnapshot(
        val todos: List<TodoItem>,
        val completedItems: List<CompletedItem>,
        val lists: List<ListSummary>,
    ) {
        val todoUpdatedAtByCanonical: Map<String, Long> = todos
            .groupBy { it.canonicalId }
            .mapValues { (_, entries) ->
                entries.maxOfOrNull { it.updatedAt?.toEpochMilli() ?: 0L } ?: 0L
            }

        val listUpdatedAtById: Map<String, Long> = lists
            .groupBy { it.id }
            .mapValues { (_, entries) ->
                entries.maxOfOrNull { it.updatedAt?.toEpochMilli() ?: 0L } ?: 0L
            }
    }

    private fun mapTodo(dto: com.ohmz.tday.compose.core.model.TodoDto): TodoItem {
        val canonicalId = dto.id.substringBefore(':')
        val suffixInstance = dto.id.substringAfter(':', "")
            .toLongOrNull()
            ?.let(Instant::ofEpochMilli)

        val explicitInstance = parseOptionalInstant(dto.instanceDate)

        return TodoItem(
            id = dto.id,
            canonicalId = canonicalId,
            title = dto.title,
            description = dto.description,
            priority = dto.priority,
            dtstart = parseInstant(dto.dtstart),
            due = parseInstant(dto.due),
            rrule = dto.rrule,
            instanceDate = explicitInstance ?: suffixInstance,
            pinned = dto.pinned,
            completed = dto.completed,
            listId = dto.listID,
            updatedAt = parseOptionalInstant(dto.updatedAt),
        )
    }

    private fun parseInstant(value: String): Instant {
        return runCatching { Instant.parse(value) }.getOrElse { Instant.now() }
    }

    private fun parseOptionalInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
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

    private fun validateProbeContract(probeBody: com.ohmz.tday.compose.core.model.MobileProbeResponse) {
        val serviceOk = probeBody.service.equals("tday", ignoreCase = true)
        val versionOk = probeBody.version == "1"
        val authOk = probeBody.auth.csrfPath == CSRF_PATH &&
            probeBody.auth.credentialsPath == CREDENTIALS_PATH &&
            probeBody.auth.registerPath == REGISTER_PATH

        if (serviceOk && versionOk && authOk) {
            return
        }

        Log.w(
            LOG_TAG,
            "probe_failed_contract service=${probeBody.service} version=${probeBody.version} auth=${probeBody.auth}",
        )

        if (!serviceOk || !versionOk) {
            throw ServerProbeException.NotTdayServer()
        }
        throw ServerProbeException.AuthContractMismatch()
    }

    private fun verifyAndPersistServerTrust(
        serverUrl: HttpUrl,
        probeResponse: Response<*>,
    ) {
        if (serverUrl.scheme != "https") return

        val serverTrustKey = secureConfigStore.serverTrustKeyForUrl(serverUrl.toString())
            ?: throw ServerProbeException.InvalidUrl()

        val certificate = probeResponse.raw()
            .handshake
            ?.peerCertificates
            ?.firstOrNull() as? X509Certificate
            ?: throw IllegalStateException("TLS certificate not available for server trust check")

        val fingerprint = certificatePublicKeyFingerprint(certificate)
        val trustedFingerprint = secureConfigStore.getTrustedServerFingerprint(serverTrustKey)

        if (trustedFingerprint.isNullOrBlank()) {
            secureConfigStore.saveTrustedServerFingerprint(
                serverTrustKey = serverTrustKey,
                fingerprint = fingerprint,
            )
            return
        }

        if (!trustedFingerprint.equals(fingerprint, ignoreCase = true)) {
            throw ServerProbeException.CertificateChanged(serverTrustKey)
        }
    }

    private fun ensureSecureTransport(serverUrl: HttpUrl) {
        if (serverUrl.scheme == "https") return
        if (isLocalDevelopmentHost(serverUrl.host)) return
        throw ServerProbeException.InsecureTransport()
    }

    private fun isLocalDevelopmentHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        if (normalizedHost == "localhost") return true
        if (normalizedHost == "10.0.2.2") return true
        if (normalizedHost.endsWith(".local")) return true
        if (normalizedHost.matches(Regex("^127\\.\\d+\\.\\d+\\.\\d+$"))) return true
        if (normalizedHost.matches(Regex("^10\\.\\d+\\.\\d+\\.\\d+$"))) return true
        if (normalizedHost.matches(Regex("^192\\.168\\.\\d+\\.\\d+$"))) return true
        return normalizedHost.matches(Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+$"))
    }

    private fun certificatePublicKeyFingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(certificate.publicKey.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        if (url.isBlank()) return emptyMap()
        return runCatching {
            val query = runCatching { URI(url).query }.getOrNull()
                ?: runCatching { URI("http://placeholder$url").query }.getOrNull()
                ?: return emptyMap()
            query.split('&')
                .mapNotNull { pair ->
                    val key = pair.substringBefore('=', missingDelimiterValue = "")
                    if (key.isBlank()) return@mapNotNull null
                    val value = pair.substringAfter('=', missingDelimiterValue = "")
                    key to URLDecoder.decode(value, Charsets.UTF_8.name())
                }
                .toMap()
        }.getOrElse { emptyMap() }
    }

    private fun mapAuthError(errorCode: String): String {
        return when (errorCode.lowercase()) {
            "credentialssignin" -> "Invalid email or password"
            "configuration" -> "Sign in failed on server. Check credentials or reset password."
            "accessdenied" -> "Access denied"
            else -> "Sign in failed: $errorCode"
        }
    }

    private fun <T> requireBody(response: Response<T>, fallback: String): T {
        if (response.isSuccessful && response.body() != null) {
            return response.body() as T
        }
        throw ApiCallException(
            statusCode = response.code(),
            message = extractErrorMessage(response, fallback),
        )
    }

    private fun extractErrorMessage(response: Response<*>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        if (raw.isNullOrBlank()) return fallback

        return runCatching {
            val element = Json.parseToJsonElement(raw)
            when (element) {
                is JsonObject -> element["message"]?.jsonPrimitive?.contentOrNull ?: fallback
                is JsonPrimitive -> element.content
                else -> fallback
            }
        }.getOrElse { fallback }
    }

    private companion object {
        const val LOG_TAG = "TdayRepository"
        const val PROBE_TIMEOUT_MS = 7_000L
        const val PROBE_PATH = "/api/mobile/probe"
        const val CSRF_PATH = "/api/auth/csrf"
        const val CREDENTIALS_PATH = "/api/auth/callback/credentials"
        const val REGISTER_PATH = "/api/auth/register"
        const val OFFLINE_RESYNC_INTERVAL_MS = 5 * 60 * 1000L
        const val MIN_FORCE_SYNC_INTERVAL_MS = 1_200L
        const val LOCAL_TODO_PREFIX = "local-todo-"
        const val LOCAL_LIST_PREFIX = "local-list-"
        const val LOCAL_COMPLETED_PREFIX = "local-completed-"
    }
}

private class ApiCallException(
    val statusCode: Int,
    override val message: String,
) : IllegalStateException(message)
