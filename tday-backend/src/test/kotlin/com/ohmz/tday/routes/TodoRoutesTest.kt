package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.models.response.AppConfigResponse
import com.ohmz.tday.models.response.FloaterResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.services.NlpParseResult
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import com.ohmz.tday.shared.model.CreateTodoRequest
import com.ohmz.tday.shared.model.TodoSummaryRequest
import com.ohmz.tday.shared.model.TodoSummaryResponse
import com.ohmz.tday.shared.model.UpdateTodoRequest
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `create todo accepts UTC timestamps from clients`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.post("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateTodoRequest(
                        title = "description",
                        description = "details",
                        priority = "Low",
                        due = "2026-03-27T15:42:00Z",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(LocalDateTime.of(2026, 3, 27, 15, 42, 0), todoService.lastCreateDue)
    }

    @Test
    fun `create todo rejects blank due date`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.post("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Floater belongs elsewhere","description":null,"priority":"Low","due":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("due is required", payload.getValue("message").jsonPrimitive.content)
        assertNull(todoService.lastCreateDue)
    }

    @Test
    fun `create todo rejects recurring task with blank due date`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.post("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "title": "Repeating task",
                      "description": null,
                      "priority": "Low",
                      "due": "",
                      "rrule": "RRULE:FREQ=DAILY;INTERVAL=1"
                    }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            "due is required",
            payload.getValue("message").jsonPrimitive.content,
        )
        assertNull(todoService.lastCreateDue)
    }

    @Test
    fun `create todo returns bad request when timestamp is invalid`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.post("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateTodoRequest(
                        title = "description",
                        description = null,
                        priority = "Low",
                        due = "not-a-date",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            "due must be a valid ISO-8601 datetime",
            payload.getValue("message").jsonPrimitive.content,
        )
        assertNull(todoService.lastCreateDue)
    }

    @Test
    fun `create todo rejects invalid priority`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.post("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateTodoRequest(
                        title = "Task",
                        priority = "Urgent",
                        due = "2026-03-27T15:42:00Z",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("priority is invalid", payload.getValue("message").jsonPrimitive.content)
        assertEquals("priority", payload.getValue("field").jsonPrimitive.content)
        assertNull(todoService.lastCreateDue)
    }

    @Test
    fun `create todo returns bad request for malformed json`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.post("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Invalid request body", payload.getValue("message").jsonPrimitive.content)
        assertNull(todoService.lastCreateDue)
    }

    @Test
    fun `patch todo rejects due clear when dateChanged true and due is missing`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.patch("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    UpdateTodoRequest(
                        id = "todo_123",
                        dateChanged = true,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("due is required", payload.getValue("message").jsonPrimitive.content)
        assertNull(todoService.lastUpdateFields)
    }

    @Test
    fun `patch todo rejects repeat rule when due is cleared`() = testApplication {
        val todoService = RecordingTodoService()

        application {
            configureTodoRoutesTestApp(todoService)
        }

        val response = client.patch("/api/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    UpdateTodoRequest(
                        id = "todo_123",
                        due = null,
                        dateChanged = true,
                        rrule = "RRULE:FREQ=DAILY;INTERVAL=1",
                        rruleChanged = true,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            "due is required",
            payload.getValue("message").jsonPrimitive.content,
        )
        assertNull(todoService.lastUpdateFields)
    }

    @Test
    fun `summary returns ai source when model responds`() = testApplication {
        val todoService = RecordingTodoService(
            timeline = listOf(
                makeTodo(title = "Submit taxes", priority = "High", due = "2026-05-30T13:00:00"),
            ),
        )
        val summaryService = FakeTodoSummaryService(response = "You have one important task today.")

        application {
            configureTodoRoutesTestApp(todoService, summaryService = summaryService)
        }

        val response = client.postSummary(TodoSummaryRequest(mode = "today", timeZone = "UTC"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TodoSummaryResponse>(response.bodyAsText())
        assertEquals("You have one important task today.", body.summary)
        assertEquals("ai", body.source)
        assertEquals("today", body.mode)
        assertEquals(1, body.taskCount)
        assertNull(body.fallbackReason)
        assertTrue(summaryService.lastPrompt.orEmpty().contains("Submit taxes"))
    }

    @Test
    fun `summary falls back to logic when model returns blank`() = testApplication {
        val todoService = RecordingTodoService(
            timeline = listOf(
                makeTodo(title = "Pay bill", priority = "High", due = "2026-05-29T13:00:00"),
            ),
        )
        val summaryService = FakeTodoSummaryService(response = "")

        application {
            configureTodoRoutesTestApp(todoService, summaryService = summaryService)
        }

        val response = client.postSummary(TodoSummaryRequest(mode = "overdue", timeZone = "UTC"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TodoSummaryResponse>(response.bodyAsText())
        assertEquals("logic", body.source)
        assertEquals("ai_unavailable", body.fallbackReason)
        assertEquals("overdue", body.mode)
        assertEquals(1, body.taskCount)
        assertNotNull(body.summary)
    }

    @Test
    fun `summary scopes scheduled custom lists`() = testApplication {
        val todoService = RecordingTodoService(
            timeline = listOf(
                makeTodo(id = "todo_one", title = "List task", listID = "list_1"),
                makeTodo(id = "todo_two", title = "Other task", listID = "list_2"),
            ),
        )
        val summaryService = FakeTodoSummaryService(response = "List summary.")

        application {
            configureTodoRoutesTestApp(todoService, summaryService = summaryService)
        }

        val response = client.postSummary(TodoSummaryRequest(mode = "LIST", listId = "list_1", timeZone = "UTC"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TodoSummaryResponse>(response.bodyAsText())
        assertEquals("ai", body.source)
        assertEquals("list", body.mode)
        assertEquals(1, body.taskCount)
        val prompt = summaryService.lastPrompt.orEmpty()
        assertTrue(prompt.contains("List task"))
        assertTrue(!prompt.contains("Other task"))
    }

    @Test
    fun `summary scopes floater lists`() = testApplication {
        val todoService = RecordingTodoService()
        val floaterService = RecordingFloaterService(
            floaters = listOf(
                makeFloater(id = "floater_one", title = "Anytime task", listID = "floater_list_1"),
                makeFloater(id = "floater_two", title = "Other floater", listID = "floater_list_2"),
            ),
        )

        application {
            configureTodoRoutesTestApp(
                todoService = todoService,
                floaterService = floaterService,
                summaryService = FakeTodoSummaryService(response = null),
            )
        }

        val response = client.postSummary(TodoSummaryRequest(mode = "floater", listId = "floater_list_1"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TodoSummaryResponse>(response.bodyAsText())
        assertEquals("logic", body.source)
        assertEquals("floater", body.mode)
        assertEquals(1, body.taskCount)
        assertTrue(body.summary.orEmpty().contains("Anytime"))
    }

    @Test
    fun `summary returns disabled response when setting is off`() = testApplication {
        val appConfigService = FakeAppConfigService(enabled = false)

        application {
            configureTodoRoutesTestApp(
                todoService = RecordingTodoService(timeline = listOf(makeTodo())),
                appConfigService = appConfigService,
            )
        }

        val response = client.postSummary(TodoSummaryRequest(mode = "all"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TodoSummaryResponse>(response.bodyAsText())
        assertNull(body.summary)
        assertEquals("disabled", body.fallbackReason)
        assertEquals("disabled", body.reason)
    }

    @Test
    fun `summary returns empty logic response for empty scope`() = testApplication {
        application {
            configureTodoRoutesTestApp(RecordingTodoService())
        }

        val response = client.postSummary(TodoSummaryRequest(mode = "scheduled"))

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TodoSummaryResponse>(response.bodyAsText())
        assertEquals("logic", body.source)
        assertEquals("empty", body.fallbackReason)
        assertEquals(0, body.taskCount)
    }

    private suspend fun io.ktor.client.HttpClient.postSummary(
        payload: TodoSummaryRequest,
    ) = post("/api/todo/summary") {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(payload))
    }

    private fun Application.configureTodoRoutesTestApp(
        todoService: TodoService,
        floaterService: FloaterService = RecordingFloaterService(),
        appConfigService: AppConfigService = FakeAppConfigService(),
        summaryService: TodoSummaryService = FakeTodoSummaryService(),
    ) {
        install(Koin) {
            modules(
                module {
                    single<TodoService> { todoService }
                    single<FloaterService> { floaterService }
                    single<TodoNlpService> { FakeTodoNlpService() }
                    single<AppConfigService> { appConfigService }
                    single<TodoSummaryService> { summaryService }
                },
            )
        }
        configureSerialization()
        configureStatusPages()
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.attributes.getOrNull(AuthUserKey) == null) {
                call.attributes.put(
                    AuthUserKey,
                    JwtUserClaims(
                        id = "user_123",
                        name = "Test User",
                        email = "user@example.com",
                        role = "ADMIN",
                        approvalStatus = "APPROVED",
                        timeZone = "UTC",
                    ),
                )
            }
        }
        routing {
            route("/api") {
                todoRoutes()
            }
        }
    }

    private class RecordingTodoService(
        private val timeline: List<TodoResponse> = emptyList(),
    ) : TodoService {
        var lastCreateDue: LocalDateTime? = null
        var lastUpdateFields: Map<String, Any?>? = null

        override suspend fun create(
            userId: String,
            title: String,
            description: String?,
            priority: String,
            due: LocalDateTime,
            rrule: String?,
            listID: String?,
        ): Either<com.ohmz.tday.domain.AppError, TodoResponse> {
            lastCreateDue = due
            return TodoResponse(
                id = "todo_123",
                title = title,
                description = description,
                priority = priority,
                due = due.toString(),
                listID = listID,
                completed = false,
                pinned = false,
            ).right()
        }

        override suspend fun getByDateRange(
            userId: String,
            start: Long,
            end: Long,
            timeZone: String,
        ) = emptyList<TodoResponse>().right()

        override suspend fun getTimeline(
            userId: String,
            timeZone: String,
            recurringFutureDays: Int,
        ) = timeline.right()

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<com.ohmz.tday.domain.AppError, Unit> {
            lastUpdateFields = fields
            return Unit.right()
        }

        override suspend fun delete(userId: String, id: String) = 1.right()

        override suspend fun completeTodo(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime?,
        ) = Unit.right()

        override suspend fun uncompleteTodo(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime?,
        ) = Unit.right()

        override suspend fun prioritize(userId: String, todoId: String, priority: String) = Unit.right()

        override suspend fun reorder(userId: String, todoId: String, newOrder: Int) = Unit.right()

        override suspend fun getOverdue(userId: String, timeZone: String) = emptyList<TodoResponse>().right()

        override suspend fun patchInstance(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime,
            fields: Map<String, Any?>,
        ) = Unit.right()

        override suspend fun deleteInstance(
            userId: String,
            todoId: String,
            instanceDate: LocalDateTime,
        ) = Unit.right()
    }

    private class RecordingFloaterService(
        private val floaters: List<FloaterResponse> = emptyList(),
    ) : FloaterService {
        override suspend fun create(
            userId: String,
            title: String,
            description: String?,
            priority: String,
            listID: String?,
        ) = makeFloater(title = title, description = description, priority = priority, listID = listID).right()

        override suspend fun getAll(userId: String) = floaters.right()

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>) = Unit.right()

        override suspend fun delete(userId: String, id: String) = 1.right()

        override suspend fun completeFloater(userId: String, floaterId: String) = Unit.right()

        override suspend fun uncompleteFloater(userId: String, floaterId: String) = Unit.right()

        override suspend fun prioritize(userId: String, floaterId: String, priority: String) = Unit.right()

        override suspend fun reorder(userId: String, floaterId: String, newOrder: Int) = Unit.right()
    }

    private class FakeTodoNlpService : TodoNlpService {
        override fun parse(
            text: String,
            locale: String?,
            referenceEpochMs: Long?,
            timezoneOffsetMinutes: Int?,
            defaultDurationMinutes: Int?,
        ) = NlpParseResult(
            cleanTitle = text,
            matchedText = null,
            matchStart = null,
            dueEpochMs = null,
        )
    }

    private class FakeAppConfigService(
        private val enabled: Boolean = true,
    ) : AppConfigService {
        override suspend fun getGlobalConfig() = AppConfigResponse(aiSummaryEnabled = enabled).right()

        override suspend fun setAiSummaryEnabled(enabled: Boolean, updatedById: String?) =
            AppConfigResponse(aiSummaryEnabled = enabled).right()
    }

    private class FakeTodoSummaryService(
        private val response: String? = null,
    ) : TodoSummaryService {
        var lastPrompt: String? = null

        override suspend fun generateSummary(prompt: String): String? {
            lastPrompt = prompt
            return response
        }

        override suspend fun isHealthy(): Boolean = true

        override suspend fun warmUp() = Unit
    }

    private companion object {
        fun makeTodo(
            id: String = "todo_123",
            title: String = "Task",
            priority: String = "Low",
            due: String = "2026-05-30T10:00:00",
            listID: String? = null,
        ) = TodoResponse(
            id = id,
            title = title,
            priority = priority,
            due = due,
            listID = listID,
            completed = false,
            pinned = false,
        )

        fun makeFloater(
            id: String = "floater_123",
            title: String = "Anytime task",
            description: String? = null,
            priority: String = "Low",
            listID: String? = null,
        ) = FloaterResponse(
            id = id,
            title = title,
            description = description,
            priority = priority,
            listID = listID,
            completed = false,
            pinned = false,
        )
    }
}
