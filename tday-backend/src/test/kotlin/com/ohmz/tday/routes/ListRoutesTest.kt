package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.models.response.ListResponse
import com.ohmz.tday.models.response.ListTodoResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.ListService
import com.ohmz.tday.shared.model.CreateListRequest
import com.ohmz.tday.shared.model.DeleteListRequest
import io.ktor.client.request.get
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class ListRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `create list returns typed response payload`() = testApplication {
        val listService = RecordingListService()

        application {
            configureListRoutesTestApp(listService)
        }

        val response = client.post("/api/list") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateListRequest(
                        name = "Inbox",
                        color = "BLUE",
                        iconKey = "inbox",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("list created", payload.getValue("message").jsonPrimitive.content)
        assertEquals("list_123", payload.getValue("list").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("Inbox", payload.getValue("list").jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `get list returns list and todos payload`() = testApplication {
        val listService = RecordingListService()

        application {
            configureListRoutesTestApp(listService)
        }

        val response = client.get("/api/list/list_123")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("list_123", payload.getValue("list").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(1, payload.getValue("todos").jsonArray.size)
        assertEquals(
            "todo_123",
            payload.getValue("todos").jsonArray.first().jsonObject.getValue("id").jsonPrimitive.content,
        )
    }

    @Test
    fun `delete list accepts multiple ids and returns deleted ids`() = testApplication {
        val listService = RecordingListService()

        application {
            configureListRoutesTestApp(listService)
        }

        val response = client.delete("/api/list") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    DeleteListRequest(
                        ids = listOf("list_123", "list_456"),
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("2 lists deleted", payload.getValue("message").jsonPrimitive.content)
        assertEquals(2, payload.getValue("deletedIds").jsonArray.size)
        assertEquals("list_123", payload.getValue("deletedIds").jsonArray[0].jsonPrimitive.content)
        assertEquals("list_456", payload.getValue("deletedIds").jsonArray[1].jsonPrimitive.content)
    }

    private fun Application.configureListRoutesTestApp(
        listService: ListService,
    ) {
        install(Koin) {
            modules(
                module {
                    single<ListService> { listService }
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
                listRoutes()
            }
        }
    }

    private class RecordingListService : ListService {
        override suspend fun getAll(userId: String): Either<com.ohmz.tday.domain.AppError, List<ListResponse>> =
            emptyList<ListResponse>().right()

        override suspend fun getById(userId: String, listId: String): Either<com.ohmz.tday.domain.AppError, ListResponse> =
            ListResponse(
                id = listId,
                name = "Inbox",
                color = "BLUE",
                iconKey = "inbox",
                userID = userId,
                createdAt = "2026-04-24T18:00:00",
                updatedAt = "2026-04-24T18:00:00",
            ).right()

        override suspend fun getTodosForList(
            userId: String,
            listId: String,
        ): Either<com.ohmz.tday.domain.AppError, List<ListTodoResponse>> =
            listOf(
                ListTodoResponse(
                    id = "todo_123",
                    title = "Task",
                    priority = "Low",
                    due = "2026-04-24T18:00:00",
                    completed = false,
                    order = 0,
                ),
            ).right()

        override suspend fun create(
            userId: String,
            name: String,
            color: String?,
            iconKey: String?,
        ): Either<com.ohmz.tday.domain.AppError, ListResponse> =
            ListResponse(
                id = "list_123",
                name = name,
                color = color,
                iconKey = iconKey,
                userID = userId,
                createdAt = "2026-04-24T18:00:00",
                updatedAt = "2026-04-24T18:00:00",
            ).right()

        override suspend fun update(
            userId: String,
            id: String,
            name: String?,
            color: String?,
            iconKey: String?,
        ): Either<com.ohmz.tday.domain.AppError, Unit> = Unit.right()

        override suspend fun delete(
            userId: String,
            id: String,
        ): Either<com.ohmz.tday.domain.AppError, Int> = 1.right()

        override suspend fun deleteMany(
            userId: String,
            ids: List<String>,
        ): Either<com.ohmz.tday.domain.AppError, List<String>> = ids.right()
    }
}
