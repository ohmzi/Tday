package com.ohmz.tday.plugins

import arrow.core.right
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.security.AuthCachedUser
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.services.GeneratedApiKey
import com.ohmz.tday.services.ResolvedApiKey
import com.ohmz.tday.services.UserApiKeyService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import arrow.core.Either
import com.ohmz.tday.domain.AppError
import io.ktor.client.statement.bodyAsText
import kotlin.test.assertEquals

class ApiKeyScopeGuardTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val readKey = "tday_readkey_secret"
    private val fullKey = "tday_fullkey_secret"

    @Test
    fun `read-only key is rejected on a mutating request`() = testApplication {
        application { configureGuardApp() }

        val response = client.post("/api/thing") {
            header(HttpHeaders.Authorization, "Bearer $readKey")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("api_key_read_only", payload.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun `read-only key is allowed on a GET request`() = testApplication {
        application { configureGuardApp() }

        val response = client.get("/api/thing") {
            header(HttpHeaders.Authorization, "Bearer $readKey")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `read-only key reaches the MCP endpoint despite it being a POST`() = testApplication {
        application { configureGuardApp() }

        // Every MCP message is a POST, including read-only ones, so the method-based
        // guard is bypassed for this one path and scope is enforced per tool instead.
        // See isScopeExemptPath in Security.kt and McpToolDispatcher.
        val response = client.post("/mcp") {
            header(HttpHeaders.Authorization, "Bearer $readKey")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `the MCP exemption does not leak to neighbouring paths`() = testApplication {
        application { configureGuardApp() }

        for (path in listOf("/api/mcp", "/mcp/tools", "/mcpx")) {
            val response = client.post(path) {
                header(HttpHeaders.Authorization, "Bearer $readKey")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status, "$path should stay guarded")
        }
    }

    @Test
    fun `a key is accepted from the header names hosted connector UIs offer`() = testApplication {
        application { configureGuardApp() }

        // The claude.ai / Claude Desktop custom-connector dialog reserves Authorization
        // for its OAuth flow and only lets you add headers from a fixed dropdown, so the
        // key has to be readable from those names too.
        for (header in listOf("X-API-Key", "x-api-key", "Api-Key", "X-Auth-Token", "X-API-Token")) {
            val response = client.get("/api/thing") { header(header, fullKey) }
            assertEquals(HttpStatusCode.OK, response.status, "$header should authenticate")
        }
    }

    @Test
    fun `a bare key without the Bearer prefix is accepted`() = testApplication {
        application { configureGuardApp() }

        val response = client.get("/api/thing") { header(HttpHeaders.Authorization, fullKey) }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `scope is still enforced when the key arrives on an alternate header`() = testApplication {
        application { configureGuardApp() }

        val response = client.post("/api/thing") { header("X-API-Key", readKey) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("api_key_read_only", payload.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun `alternate headers are not a new way to present a session token`() = testApplication {
        application { configureGuardApp() }

        // Only tday_ values are read from these headers; anything else is ignored rather
        // than being tried as a JWE session token.
        val response = client.get("/api/thing") { header("X-API-Key", "some.jwe.session.token") }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `full key is allowed on a mutating request`() = testApplication {
        application { configureGuardApp() }

        val response = client.post("/api/thing") {
            header(HttpHeaders.Authorization, "Bearer $fullKey")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun Application.configureGuardApp() {
        val config = testAppConfig()
        val authUserCache = AuthUserCache().apply {
            put(
                "user_123",
                AuthCachedUser(
                    role = "USER",
                    approvalStatus = "APPROVED",
                    tokenVersion = 1,
                    timeZone = "UTC",
                ),
            )
        }
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<JwtService> { JwtServiceImpl(config) }
                    single { authUserCache }
                    single<SecurityEventLogger> { NoOpSecurityEventLogger() }
                    single<UserApiKeyService> { ScopedFakeApiKeyService() }
                },
            )
        }
        configureSerialization()
        configureSecurity()
        routing {
            route("/api") {
                get("/thing") {
                    call.withAuth { mapOf("ok" to true).right() }
                }
                post("/thing") {
                    call.withAuth { mapOf("ok" to true).right() }
                }
                post("/mcp") {
                    call.withAuth { mapOf("ok" to true).right() }
                }
            }
            post("/mcp") {
                call.withAuth { mapOf("ok" to true).right() }
            }
            post("/mcp/tools") {
                call.withAuth { mapOf("ok" to true).right() }
            }
            post("/mcpx") {
                call.withAuth { mapOf("ok" to true).right() }
            }
        }
    }

    private inner class ScopedFakeApiKeyService : UserApiKeyService {
        override suspend fun generate(
            userId: String,
            label: String?,
            scope: ApiKeyScope,
            expiresInDays: Long?,
        ): Either<AppError, GeneratedApiKey> = AppError.Internal("unused").let { Either.Left(it) }

        override suspend fun list(userId: String) = emptyList<com.ohmz.tday.services.ApiKeyInfo>().right()

        override suspend fun revokeKey(userId: String, keyId: String) = Unit.right()

        override suspend fun revoke(userId: String) = Unit.right()

        override suspend fun resolveKey(rawKey: String): ResolvedApiKey? = when (rawKey) {
            readKey -> ResolvedApiKey(userId = "user_123", scope = ApiKeyScope.READ)
            fullKey -> ResolvedApiKey(userId = "user_123", scope = ApiKeyScope.FULL)
            else -> null
        }
    }

    private class NoOpSecurityEventLogger : SecurityEventLogger {
        override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
    }
}
