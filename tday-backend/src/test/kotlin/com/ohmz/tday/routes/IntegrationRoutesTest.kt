package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.mcp.MCP_FULL_KEY
import com.ohmz.tday.mcp.MCP_READ_KEY
import com.ohmz.tday.mcp.McpTestWorld
import com.ohmz.tday.plugins.configureSecurity
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.AuthCachedUser
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.ApiKeyInfo
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.services.GeneratedApiKey
import com.ohmz.tday.services.IntegrationContextService
import com.ohmz.tday.services.IntegrationContextServiceImpl
import com.ohmz.tday.services.ResolvedApiKey
import com.ohmz.tday.services.UserApiKeyService
import com.ohmz.tday.services.UserService
import com.ohmz.tday.models.response.UserProfileResponse
import com.ohmz.tday.models.response.UserResponse
import com.ohmz.tday.models.request.SecurityAnswerInput
import com.ohmz.tday.services.RegisterResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntegrationRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a full key reports write access and both list namespaces`() = testApplication {
        val world = McpTestWorld().apply {
            addList("Work")
            addFloaterList("Groceries")
        }
        application { configureIntegrationApp(world) }

        val body = client.get("/api/integration/context") {
            header(HttpHeaders.Authorization, "Bearer $MCP_FULL_KEY")
        }.bodyAsText()
        val payload = json.parseToJsonElement(body).jsonObject

        assertEquals("FULL", payload.getValue("apiKey").jsonObject.getValue("scope").jsonPrimitive.content)
        assertEquals("Claude", payload.getValue("apiKey").jsonObject.getValue("label").jsonPrimitive.content)
        assertTrue(payload.getValue("capabilities").jsonObject.getValue("canWrite").jsonPrimitive.boolean)
        assertEquals("ohmz", payload.getValue("user").jsonObject.getValue("username").jsonPrimitive.content)
        assertEquals(1, payload.getValue("lists").jsonArray.size)
        assertEquals(1, payload.getValue("anytimeLists").jsonArray.size)
    }

    @Test
    fun `a read key reports no write access`() = testApplication {
        application { configureIntegrationApp(McpTestWorld()) }

        val body = client.get("/api/integration/context") {
            header(HttpHeaders.Authorization, "Bearer $MCP_READ_KEY")
        }.bodyAsText()
        val payload = json.parseToJsonElement(body).jsonObject

        assertEquals("READ", payload.getValue("apiKey").jsonObject.getValue("scope").jsonPrimitive.content)
        assertFalse(payload.getValue("capabilities").jsonObject.getValue("canWrite").jsonPrimitive.boolean)
    }

    @Test
    fun `a caller with no key is rejected`() = testApplication {
        application { configureIntegrationApp(McpTestWorld()) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/integration/context").status)
    }

    @Test
    fun `apiKey is absent for a session caller`() = testApplication {
        val world = McpTestWorld()
        application { configureIntegrationApp(world) }
        val token = sessionTokenFor(world.userId)

        val body = client.get("/api/integration/context") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.bodyAsText()
        val payload = json.parseToJsonElement(body).jsonObject

        assertNull(payload["apiKey"]?.takeUnless { it is kotlinx.serialization.json.JsonNull })
        assertTrue(payload.getValue("capabilities").jsonObject.getValue("canWrite").jsonPrimitive.boolean)
    }

    // testApplication starts the app lazily, so the token is minted from an independent
    // service over the same config rather than one the app hands back.
    private val jwtService: JwtService = JwtServiceImpl(testAppConfig())

    private fun sessionTokenFor(userId: String): String = jwtService.encode(
        JwtUserClaims(
            id = userId,
            role = "USER",
            approvalStatus = "APPROVED",
            tokenVersion = 1,
            timeZone = "UTC",
        ),
    )

    private fun Application.configureIntegrationApp(world: McpTestWorld) {
        val config = testAppConfig()
        val authUserCache = AuthUserCache().apply {
            put(
                world.userId,
                AuthCachedUser(role = "USER", approvalStatus = "APPROVED", tokenVersion = 1, timeZone = "UTC"),
            )
        }
        install(Koin) {
            modules(
                module {
                    single { config }
                    single { jwtService }
                    single { authUserCache }
                    single<SecurityEventLogger> { NoOpLogger() }
                    single<UserApiKeyService> { FakeKeys(world.userId) }
                    single<IntegrationContextService> {
                        IntegrationContextServiceImpl(
                            FakeUserService(world.userId),
                            world.listService,
                            world.floaterListService,
                        )
                    }
                },
            )
        }
        configureSerialization()
        configureSecurity()
        routing { route("/api") { integrationRoutes() } }
    }

    private class NoOpLogger : SecurityEventLogger {
        override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
    }

    private class FakeKeys(private val userId: String) : UserApiKeyService {
        override suspend fun generate(
            userId: String,
            label: String?,
            scope: ApiKeyScope,
            expiresInDays: Long?,
        ): Either<AppError, GeneratedApiKey> = Either.Left(AppError.Internal("unused"))

        override suspend fun list(userId: String) = emptyList<ApiKeyInfo>().right()
        override suspend fun revokeKey(userId: String, keyId: String) = Unit.right()
        override suspend fun revoke(userId: String) = Unit.right()
        override suspend fun resolveKey(rawKey: String): ResolvedApiKey? = when (rawKey) {
            MCP_READ_KEY -> ResolvedApiKey(userId, ApiKeyScope.READ, "readkey", "Dashboard", "cret")
            MCP_FULL_KEY -> ResolvedApiKey(userId, ApiKeyScope.FULL, "fullkey", "Claude", "cret")
            else -> null
        }
    }

    private class FakeUserService(private val userId: String) : UserService {
        override suspend fun getUser(userId: String): Either<AppError, UserResponse> =
            Either.Left(AppError.Internal("unused"))

        override suspend fun getProfile(userId: String): Either<AppError, UserProfileResponse> =
            UserProfileResponse(
                id = this.userId,
                name = "Omar",
                username = "ohmz",
                image = null,
                role = "USER",
                approvalStatus = "APPROVED",
                createdAt = "2026-01-01T00:00",
            ).right()

        override suspend fun updateEncryption(userId: String, enable: Boolean) = Unit.right()
        override suspend fun updateSymmetricKey(userId: String, key: String) = Unit.right()
        override suspend fun updateProfile(userId: String, name: String?, image: String?) = Unit.right()
        override suspend fun changePassword(userId: String, currentPassword: String, newPassword: String) = true.right()
        override suspend fun verifyCurrentPassword(userId: String, password: String) = true.right()
        override suspend fun register(
            fname: String,
            lname: String?,
            username: String,
            password: String,
            securityAnswers: List<SecurityAnswerInput>,
        ): Either<AppError, RegisterResult> = Either.Left(AppError.Internal("unused"))

        override suspend fun findByUsername(username: String): Map<String, Any?>? = null
        override suspend fun isAdmin(userId: String) = false
        override suspend fun usernameExists(username: String) = false
        override suspend fun updatePasswordHash(userId: String, newHash: String) = Unit
        override suspend fun requiresPasswordChange(userId: String) = false
    }
}
