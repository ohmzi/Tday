package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.models.response.AppConfigResponse
import com.ohmz.tday.models.response.TodoResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.AppConfigService
import com.ohmz.tday.services.NlpParseResult
import com.ohmz.tday.services.TodoNlpService
import com.ohmz.tday.services.TodoService
import com.ohmz.tday.services.TodoSummaryService
import com.ohmz.tday.shared.model.CreateTodoRequest
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                        dtstart = "2026-03-27T14:42:00Z",
                        due = "2026-03-27T15:42:00Z",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(LocalDateTime.of(2026, 3, 27, 14, 42, 0), todoService.lastCreateDtstart)
        assertEquals(LocalDateTime.of(2026, 3, 27, 15, 42, 0), todoService.lastCreateDue)
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
                        dtstart = "not-a-date",
                        due = "2026-03-27T15:42:00Z",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            "dtstart must be a valid ISO-8601 datetime",
            payload.getValue("message").jsonPrimitive.content,
        )
        assertNull(todoService.lastCreateDtstart)
        assertNull(todoService.lastCreateDue)
    }

    private fun Application.configureTodoRoutesTestApp(
        todoService: TodoService,
    ) {
        install(Koin) {
            modules(
                module {
                    single<TodoService> { todoService }
                    single<TodoNlpService> { FakeTodoNlpService() }
                    single<AppConfigService> { FakeAppConfigService() }
                    single<TodoSummaryService> { FakeTodoSummaryService() }
                },
            )
        }
        configureSerialization()
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

    private class RecordingTodoService : TodoService {
        var lastCreateDtstart: LocalDateTime? = null
        var lastCreateDue: LocalDateTime? = null

        override suspend fun create(
            userId: String,
            title: String,
            description: String?,
            priority: String,
            dtstart: LocalDateTime,
            due: LocalDateTime,
            rrule: String?,
            listID: String?,
        ): Either<com.ohmz.tday.domain.AppError, TodoResponse> {
            lastCreateDtstart = dtstart
            lastCreateDue = due
            return TodoResponse(
                id = "todo_123",
                title = title,
                description = description,
                priority = priority,
                dtstart = dtstart.toString(),
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
        ) = emptyList<TodoResponse>().right()

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>) = Unit.right()

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
            startEpochMs = null,
            dueEpochMs = null,
        )
    }

    private class FakeAppConfigService : AppConfigService {
        override suspend fun getGlobalConfig() = AppConfigResponse(aiSummaryEnabled = true).right()

        override suspend fun setAiSummaryEnabled(enabled: Boolean, updatedById: String?) =
            AppConfigResponse(aiSummaryEnabled = enabled).right()
    }

    private class FakeTodoSummaryService : TodoSummaryService {
        override suspend fun generateSummary(prompt: String): String? = null

        override suspend fun isHealthy(): Boolean = true
    }
}
