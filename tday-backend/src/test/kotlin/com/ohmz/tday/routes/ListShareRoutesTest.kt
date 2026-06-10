package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.ListShareService
import com.ohmz.tday.services.ListType
import com.ohmz.tday.shared.model.AddMemberRequest
import com.ohmz.tday.shared.model.ListMemberDto
import com.ohmz.tday.shared.model.ListMembersResponse
import com.ohmz.tday.shared.model.RemoveMemberRequest
import com.ohmz.tday.shared.model.ShareRole
import com.ohmz.tday.shared.model.UpdateMemberRoleRequest
import com.ohmz.tday.shared.model.UserSearchResultDto
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class ListShareRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `get members returns owner and members payload`() = testApplication {
        val shareService = RecordingListShareService()

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.get("/api/list/list_123/members")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("owner_1", payload.getValue("owner").jsonObject.getValue("userId").jsonPrimitive.content)
        assertEquals("OWNER", payload.getValue("owner").jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(1, payload.getValue("members").jsonArray.size)
        assertEquals(
            "member_1",
            payload.getValue("members").jsonArray.first().jsonObject.getValue("userId").jsonPrimitive.content,
        )
        assertEquals(ListType.SCHEDULED, shareService.lastListType)
    }

    @Test
    fun `floater list members route uses floater list type`() = testApplication {
        val shareService = RecordingListShareService()

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.get("/api/floaterList/list_456/members")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ListType.FLOATER, shareService.lastListType)
        assertEquals("list_456", shareService.lastListId)
    }

    @Test
    fun `add member returns added member payload`() = testApplication {
        val shareService = RecordingListShareService()

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.post("/api/list/list_123/members") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddMemberRequest(username = "frieda", role = "VIEWER")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("member added", payload.getValue("message").jsonPrimitive.content)
        assertEquals("frieda", payload.getValue("member").jsonObject.getValue("username").jsonPrimitive.content)
        assertEquals("VIEWER", payload.getValue("member").jsonObject.getValue("role").jsonPrimitive.content)
    }

    @Test
    fun `add member as non-owner is forbidden`() = testApplication {
        val shareService = RecordingListShareService(
            addMemberResult = AppError.Forbidden("only the list owner can manage members").left(),
        )

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.post("/api/list/list_123/members") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddMemberRequest(username = "frieda", role = "EDITOR")))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `update member role returns confirmation`() = testApplication {
        val shareService = RecordingListShareService()

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.patch("/api/list/list_123/members") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpdateMemberRoleRequest(userId = "member_1", role = "VIEWER")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("member role updated", payload.getValue("message").jsonPrimitive.content)
        assertEquals("member_1", shareService.lastMemberUserId)
        assertEquals("VIEWER", shareService.lastRole)
    }

    @Test
    fun `remove member returns confirmation`() = testApplication {
        val shareService = RecordingListShareService()

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.delete("/api/list/list_123/members") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RemoveMemberRequest(userId = "member_1")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("member removed", payload.getValue("message").jsonPrimitive.content)
        assertEquals("member_1", shareService.lastMemberUserId)
    }

    @Test
    fun `leave removes own membership`() = testApplication {
        val shareService = RecordingListShareService()

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.post("/api/list/list_123/leave")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("left list", payload.getValue("message").jsonPrimitive.content)
        assertEquals("user_123", shareService.lastLeaveUserId)
    }

    @Test
    fun `owner cannot leave their own list`() = testApplication {
        val shareService = RecordingListShareService(
            leaveResult = AppError.BadRequest("the owner cannot leave their own list").left(),
        )

        application {
            configureShareRoutesTestApp(shareService)
        }

        val response = client.post("/api/list/list_123/leave")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun Application.configureShareRoutesTestApp(
        shareService: ListShareService,
    ) {
        install(Koin) {
            modules(
                module {
                    single<ListShareService> { shareService }
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
                listShareRoutes()
            }
        }
    }

    private class RecordingListShareService(
        private val addMemberResult: Either<AppError, ListMemberDto>? = null,
        private val leaveResult: Either<AppError, Unit>? = null,
    ) : ListShareService {
        var lastListId: String? = null
        var lastListType: ListType? = null
        var lastMemberUserId: String? = null
        var lastRole: String? = null
        var lastLeaveUserId: String? = null

        override suspend fun accessFor(userId: String, listId: String, type: ListType): ShareRole? = ShareRole.OWNER

        override suspend fun sharedListIdsFor(userId: String, type: ListType, editorOnly: Boolean): List<String> = emptyList()

        override suspend fun canEditList(userId: String, listId: String, type: ListType): Boolean = true

        override suspend fun collaboratorIdsFor(userId: String): Set<String> = emptySet()

        override suspend fun members(requesterId: String, listId: String, type: ListType): Either<AppError, ListMembersResponse> {
            lastListId = listId
            lastListType = type
            return ListMembersResponse(
                owner = ListMemberDto(userId = "owner_1", username = "owner", role = ShareRole.OWNER.name),
                members = listOf(
                    ListMemberDto(userId = "member_1", username = "frieda", role = ShareRole.EDITOR.name),
                ),
            ).right()
        }

        override suspend fun addMember(
            requesterId: String,
            listId: String,
            type: ListType,
            username: String,
            role: String,
        ): Either<AppError, ListMemberDto> {
            lastListId = listId
            lastListType = type
            lastRole = role
            return addMemberResult ?: ListMemberDto(
                userId = "member_new",
                username = username,
                role = role,
            ).right()
        }

        override suspend fun updateRole(
            requesterId: String,
            listId: String,
            type: ListType,
            memberUserId: String,
            role: String,
        ): Either<AppError, Unit> {
            lastListId = listId
            lastMemberUserId = memberUserId
            lastRole = role
            return Unit.right()
        }

        override suspend fun removeMember(
            requesterId: String,
            listId: String,
            type: ListType,
            memberUserId: String,
        ): Either<AppError, Unit> {
            lastListId = listId
            lastMemberUserId = memberUserId
            return Unit.right()
        }

        override suspend fun leave(userId: String, listId: String, type: ListType): Either<AppError, Unit> {
            lastListId = listId
            lastLeaveUserId = userId
            return leaveResult ?: Unit.right()
        }

        override suspend fun searchUsers(requesterId: String, query: String): Either<AppError, List<UserSearchResultDto>> =
            emptyList<UserSearchResultDto>().right()
    }
}
