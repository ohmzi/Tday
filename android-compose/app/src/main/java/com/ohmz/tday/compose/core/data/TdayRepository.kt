package com.ohmz.tday.compose.core.data

import android.util.Log
import com.ohmz.tday.compose.core.model.AuthResult
import com.ohmz.tday.compose.core.model.AuthSession
import com.ohmz.tday.compose.core.model.CompletedItem
import com.ohmz.tday.compose.core.model.CreateNoteRequest
import com.ohmz.tday.compose.core.model.CreateListRequest
import com.ohmz.tday.compose.core.model.CreateTodoRequest
import com.ohmz.tday.compose.core.model.DashboardSummary
import com.ohmz.tday.compose.core.model.NoteItem
import com.ohmz.tday.compose.core.model.ListSummary
import com.ohmz.tday.compose.core.model.RegisterOutcome
import com.ohmz.tday.compose.core.model.RegisterRequest
import com.ohmz.tday.compose.core.model.SessionUser
import com.ohmz.tday.compose.core.model.TodoInstanceRequest
import com.ohmz.tday.compose.core.model.TodoItem
import com.ohmz.tday.compose.core.model.TodoListMode
import com.ohmz.tday.compose.core.network.TdayApiService
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
                payload = mapOf(
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
    }

    suspend fun syncTimezone() {
        runCatching {
            api.syncTimezone(TimeZone.getDefault().id)
        }
    }

    fun hasServerConfigured(): Boolean = secureConfigStore.hasServerUrl()

    fun getServerUrl(): String? = secureConfigStore.getServerUrl()

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

        secureConfigStore.saveServerUrl(normalizedServerUrl).getOrThrow()
    }

    fun resetTrustedServer(rawUrl: String): Result<Unit> {
        return secureConfigStore.clearTrustedServerFingerprintForUrl(rawUrl)
    }

    fun getSavedCredentials(): SavedCredentials? = secureConfigStore.getSavedCredentials()

    suspend fun fetchDashboardSummary(): DashboardSummary {
        val todayTodos = fetchTodayTodos()
        val timelineTodos = fetchTimelineTodos()
        val completedTodos = fetchCompletedItems()
        val lists = fetchLists()

        val todayDate = LocalDate.now(zoneId)
        val scheduledCount = timelineTodos.count {
            LocalDate.ofInstant(it.due, zoneId) != todayDate
        }

        return DashboardSummary(
            todayCount = todayTodos.size,
            scheduledCount = scheduledCount,
            allCount = timelineTodos.size,
            flaggedCount = timelineTodos.count { it.priority.equals("High", ignoreCase = true) },
            completedCount = completedTodos.size,
            lists = lists,
        )
    }

    suspend fun fetchTodos(mode: TodoListMode, listId: String? = null): List<TodoItem> {
        return when (mode) {
            TodoListMode.TODAY -> fetchTodayTodos()
            TodoListMode.ALL -> fetchTimelineTodos()
            TodoListMode.SCHEDULED -> {
                val todayDate = LocalDate.now(zoneId)
                fetchTimelineTodos().filter {
                    LocalDate.ofInstant(it.due, zoneId) != todayDate
                }
            }

            TodoListMode.FLAGGED -> fetchTimelineTodos().filter {
                it.priority.equals("High", ignoreCase = true)
            }

            TodoListMode.LIST -> {
                if (listId.isNullOrBlank()) {
                    emptyList()
                } else {
                    fetchListTodos(listId)
                }
            }
        }
    }

    suspend fun fetchTodayTodos(): List<TodoItem> {
        val response = requireBody(
            api.getTodos(
                start = startOfTodayMillis(),
                end = endOfTodayMillis(),
            ),
            "Could not load today tasks",
        )

        return response.todos.map(::mapTodo)
    }

    suspend fun fetchTimelineTodos(): List<TodoItem> {
        val response = requireBody(
            api.getTodos(timeline = true),
            "Could not load timeline tasks",
        )

        return response.todos.map(::mapTodo)
    }

    suspend fun fetchListTodos(listId: String): List<TodoItem> {
        val response = requireBody(
            api.getListTodos(
                listId = listId,
                start = startOfTodayMillis(),
                end = endOfTodayMillis(),
            ),
            "Could not load list tasks",
        )
        return response.todos.map(::mapTodo)
    }

    suspend fun createTodo(title: String, listId: String? = null) {
        val start = Instant.now()
        val due = start.plusSeconds(30 * 60)
        val payload = CreateTodoRequest(
            title = title.trim(),
            description = null,
            priority = "Low",
            dtstart = start.toString(),
            due = due.toString(),
            rrule = null,
            listID = listId,
        )

        requireBody(api.createTodo(payload), "Could not create task")
    }

    suspend fun deleteTodo(todo: TodoItem) {
        requireBody(api.deleteTodo(todo.canonicalId), "Could not delete task")
    }

    suspend fun setTodoPinned(todo: TodoItem, pinned: Boolean) {
        requireBody(
            api.patchTodo(
                todoId = todo.canonicalId,
                payload = buildJsonObject {
                    put("pinned", pinned)
                },
            ),
            "Could not update pin",
        )
    }

    suspend fun setTodoPriority(todo: TodoItem, priority: String) {
        val instanceDateMillis = todo.instanceDateEpochMillis
        if (todo.isRecurring && instanceDateMillis != null) {
            requireBody(
                api.prioritizeTodoInstance(
                    todoId = todo.canonicalId,
                    priority = priority,
                    instanceDate = instanceDateMillis,
                ),
                "Could not update priority",
            )
            return
        }

        requireBody(
            api.patchTodo(
                todoId = todo.canonicalId,
                payload = buildJsonObject {
                    put("priority", priority)
                },
            ),
            "Could not update priority",
        )
    }

    suspend fun completeTodo(todo: TodoItem) {
        if (todo.isRecurring) {
            requireBody(
                api.completeTodoInstance(
                    todoId = todo.canonicalId,
                    payload = TodoInstanceRequest(instanceDate = todo.instanceDate?.toString()),
                ),
                "Could not complete recurring task",
            )
            return
        }

        requireBody(
            api.completeTodo(todoId = todo.canonicalId, payload = buildJsonObject {}),
            "Could not complete task",
        )
    }

    suspend fun fetchCompletedItems(): List<CompletedItem> {
        val response = requireBody(api.getCompletedTodos(), "Could not load completed tasks")
        return response.completedTodos.map { dto ->
            CompletedItem(
                id = dto.id,
                originalTodoId = dto.originalTodoID,
                title = dto.title,
                priority = dto.priority,
                due = parseInstant(dto.due),
                rrule = dto.rrule,
                instanceDate = parseOptionalInstant(dto.instanceDate),
            )
        }
    }

    suspend fun uncomplete(item: CompletedItem) {
        val originalTodoId = item.originalTodoId
            ?: throw IllegalStateException("Completed todo is missing original todo id")

        if (!item.rrule.isNullOrBlank()) {
            requireBody(
                api.uncompleteTodoInstance(
                    todoId = originalTodoId,
                    payload = TodoInstanceRequest(instanceDate = item.instanceDate?.toString()),
                ),
                "Could not restore recurring task",
            )
            return
        }

        requireBody(
            api.uncompleteTodo(
                todoId = originalTodoId,
                payload = buildJsonObject {},
            ),
            "Could not restore task",
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
        val response = requireBody(api.getLists(), "Could not load lists")
        return response.lists.map {
            ListSummary(
                id = it.id,
                name = it.name,
                color = it.color,
                iconKey = secureConfigStore.getListIcon(it.id),
                todoCount = it.todoCount,
            )
        }
    }

    suspend fun createList(
        name: String,
        color: String? = null,
        iconKey: String? = null,
    ) {
        val response = requireBody(
            api.createList(
                CreateListRequest(
                    name = name.trim(),
                    color = color,
                ),
            ),
            "Could not create list",
        )

        val created = response.list

        if (created?.id != null && !iconKey.isNullOrBlank()) {
            secureConfigStore.saveListIcon(created.id, iconKey)
        }

        // Backward compatibility for older backend instances that may ignore color on create.
        if (created?.id != null && !color.isNullOrBlank() && created.color != color) {
            requireBody(
                api.patchList(
                    listId = created.id,
                    payload = buildJsonObject { put("color", color) },
                ),
                "Could not apply list color",
            )
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
        throw IllegalStateException(extractErrorMessage(response, fallback))
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
    }
}
