package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.FloaterService
import com.ohmz.tday.shared.model.FloaterDto
import com.ohmz.tday.shared.model.TodoDto
import java.time.LocalDateTime
import io.ktor.client.request.delete
import io.ktor.client.request.get
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FloaterRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `create floater ignores schedule fields and calls floater service`() = testApplication {
        val floaterService = RecordingFloaterService()

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val response = client.post("/api/floater") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "title": "Idea without date",
                      "description": "keep it loose",
                      "priority": "High",
                      "listID": "list_123",
                      "due": "2026-05-29T09:00:00Z",
                      "rrule": "RRULE:FREQ=DAILY"
                    }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Idea without date", floaterService.lastCreate?.title)
        assertEquals("High", floaterService.lastCreate?.priority)
        assertEquals("list_123", floaterService.lastCreate?.listID)
    }

    @Test
    fun `list floaters returns active floater payload`() = testApplication {
        val floaterService = RecordingFloaterService(
            rows = mutableListOf(
                FloaterDto(
                    id = "floater_1",
                    title = "Paint shelf",
                    priority = "Medium",
                    completed = false,
                    listID = "list_home",
                ),
            ),
        )

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val response = client.get("/api/floater")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val first = payload.getValue("floaters").jsonArray.first().jsonObject
        assertEquals("floater_1", first.getValue("id").jsonPrimitive.content)
        assertEquals("Paint shelf", first.getValue("title").jsonPrimitive.content)
        assertFalse(first.containsKey("due"))
        assertFalse(first.containsKey("rrule"))
    }

    @Test
    fun `patch floater updates floater fields without due semantics`() = testApplication {
        val floaterService = RecordingFloaterService()

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val response = client.patch("/api/floater") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "id": "floater_1",
                      "title": "Move gently",
                      "priority": "Low",
                      "pinned": true,
                      "listID": ""
                    }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("floater_1", floaterService.lastUpdateId)
        assertEquals("Move gently", floaterService.lastUpdateFields?.get("title"))
        assertEquals("Low", floaterService.lastUpdateFields?.get("priority"))
        assertEquals(true, floaterService.lastUpdateFields?.get("pinned"))
        assertNull(floaterService.lastUpdateFields?.get("listID"))
        assertFalse(floaterService.lastUpdateFields?.containsKey("due") == true)
        assertFalse(floaterService.lastUpdateFields?.containsKey("rrule") == true)
    }

    @Test
    fun `complete and uncomplete floater use floater endpoints`() = testApplication {
        val floaterService = RecordingFloaterService()

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val completeResponse = client.patch("/api/floater/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"floater_1"}""")
        }
        val uncompleteResponse = client.patch("/api/floater/uncomplete") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":"floater_1"}""")
        }

        assertEquals(HttpStatusCode.OK, completeResponse.status)
        assertEquals(HttpStatusCode.OK, uncompleteResponse.status)
        assertEquals(listOf("complete:floater_1", "uncomplete:floater_1"), floaterService.events)
    }

    @Test
    fun `promote floater parses due and returns the created todo`() = testApplication {
        val floaterService = RecordingFloaterService()

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val response = client.post("/api/floater/floater_9/promote") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "due": "2026-07-01T09:00:00Z",
                      "rrule": "RRULE:FREQ=WEEKLY;INTERVAL=1"
                    }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("floater_9", floaterService.lastPromote?.floaterId)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=1", floaterService.lastPromote?.rrule)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            "todo_promoted",
            payload.getValue("todo").jsonObject.getValue("id").jsonPrimitive.content,
        )
    }

    @Test
    fun `promote floater rejects an invalid due`() = testApplication {
        val floaterService = RecordingFloaterService()

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val response = client.post("/api/floater/floater_9/promote") {
            contentType(ContentType.Application.Json)
            setBody("""{"due":"not-a-date"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(floaterService.lastPromote)
    }

    @Test
    fun `delete floater validates id`() = testApplication {
        val floaterService = RecordingFloaterService()

        application {
            configureFloaterRoutesTestApp(floaterService)
        }

        val response = client.delete("/api/floater") {
            contentType(ContentType.Application.Json)
            setBody("""{"id":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(floaterService.lastDeleteId)
    }

    private fun Application.configureFloaterRoutesTestApp(
        floaterService: FloaterService,
    ) {
        install(Koin) {
            modules(
                module {
                    single<FloaterService> { floaterService }
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
                        username = "testuser",
                        role = "ADMIN",
                        approvalStatus = "APPROVED",
                        timeZone = "UTC",
                    ),
                )
            }
        }
        routing {
            route("/api") {
                floaterRoutes()
            }
        }
    }

    private data class CreateCall(
        val title: String,
        val description: String?,
        val priority: String,
        val listID: String?,
    )

    private data class PromoteCall(
        val floaterId: String,
        val due: LocalDateTime,
        val rrule: String?,
    )

    private class RecordingFloaterService(
        val rows: MutableList<FloaterDto> = mutableListOf(),
    ) : FloaterService {
        var lastCreate: CreateCall? = null
        var lastUpdateId: String? = null
        var lastUpdateFields: Map<String, Any?>? = null
        var lastDeleteId: String? = null
        var lastPromote: PromoteCall? = null
        val events = mutableListOf<String>()

        override suspend fun create(
            userId: String,
            title: String,
            description: String?,
            priority: String,
            listID: String?,
        ): Either<AppError, FloaterDto> {
            lastCreate = CreateCall(title, description, priority, listID)
            return FloaterDto(
                id = "floater_created",
                title = title,
                description = description,
                priority = priority,
                completed = false,
                listID = listID,
                userID = userId,
            ).right()
        }

        override suspend fun getAll(userId: String): Either<AppError, List<FloaterDto>> =
            rows.right()

        override suspend fun update(userId: String, id: String, fields: Map<String, Any?>): Either<AppError, Unit> {
            lastUpdateId = id
            lastUpdateFields = fields
            return Unit.right()
        }

        override suspend fun delete(userId: String, id: String): Either<AppError, Int> {
            lastDeleteId = id
            return 1.right()
        }

        override suspend fun completeFloater(userId: String, floaterId: String): Either<AppError, Unit> {
            events += "complete:$floaterId"
            return Unit.right()
        }

        override suspend fun uncompleteFloater(userId: String, floaterId: String): Either<AppError, Unit> {
            events += "uncomplete:$floaterId"
            return Unit.right()
        }

        override suspend fun prioritize(userId: String, floaterId: String, priority: String): Either<AppError, Unit> =
            Unit.right()

        override suspend fun reorder(userId: String, floaterId: String, newOrder: Int): Either<AppError, Unit> =
            Unit.right()

        override suspend fun promoteToTodo(
            userId: String,
            floaterId: String,
            due: LocalDateTime,
            rrule: String?,
        ): Either<AppError, TodoDto> {
            lastPromote = PromoteCall(floaterId, due, rrule)
            return TodoDto(
                id = "todo_promoted",
                title = "Paint shelf",
                due = due.toString(),
                rrule = rrule,
            ).right()
        }
    }
}
