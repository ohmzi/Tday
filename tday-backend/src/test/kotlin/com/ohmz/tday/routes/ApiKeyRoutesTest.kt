package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.ApiKeyInfo
import com.ohmz.tday.services.ApiKeyScope
import com.ohmz.tday.services.GeneratedApiKey
import com.ohmz.tday.services.UserApiKeyService
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
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
import kotlin.test.assertNull

class ApiKeyRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `list returns every key for the caller`() = testApplication {
        val service = FakeUserApiKeyService()
        service.stored += ApiKeyInfo(
            id = "k1",
            label = "Homarr",
            scope = "READ",
            keyPreview = "ab12",
            createdAt = "2026-01-01T00:00:00",
        )
        application { configureApiKeyTestApp(service) }

        val response = client.get("/api/user/api-key")

        assertEquals(HttpStatusCode.OK, response.status)
        val keys = json.parseToJsonElement(response.bodyAsText()).jsonObject["keys"]!!.jsonArray
        assertEquals(1, keys.size)
        assertEquals("Homarr", keys[0].jsonObject["label"]!!.jsonPrimitive.content)
        assertEquals("READ", keys[0].jsonObject["scope"]!!.jsonPrimitive.content)
    }

    @Test
    fun `create forwards label and scope to the service`() = testApplication {
        val service = FakeUserApiKeyService()
        application { configureApiKeyTestApp(service) }

        val response = client.post("/api/user/api-key") {
            contentType(ContentType.Application.Json)
            setBody("""{"label":"Dashboard","scope":"READ","expiresInDays":30}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Dashboard", service.lastLabel)
        assertEquals(ApiKeyScope.READ, service.lastScope)
        assertEquals(30L, service.lastExpiresInDays)
    }

    @Test
    fun `create with empty body defaults to a FULL key`() = testApplication {
        val service = FakeUserApiKeyService()
        application { configureApiKeyTestApp(service) }

        val response = client.post("/api/user/api-key") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ApiKeyScope.FULL, service.lastScope)
        assertNull(service.lastLabel)
        assertNull(service.lastExpiresInDays)
    }

    @Test
    fun `delete revokes a single key by id`() = testApplication {
        val service = FakeUserApiKeyService()
        application { configureApiKeyTestApp(service) }

        val response = client.delete("/api/user/api-key/k1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("k1", service.lastRevokedKeyId)
    }

    @Test
    fun `delete on an unknown key surfaces not found`() = testApplication {
        val service = FakeUserApiKeyService(revokeResult = AppError.NotFound("api key not found").left())
        application { configureApiKeyTestApp(service) }

        val response = client.delete("/api/user/api-key/missing")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private fun Application.configureApiKeyTestApp(service: UserApiKeyService) {
        install(Koin) {
            modules(module { single<UserApiKeyService> { service } })
        }
        configureSerialization()
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.attributes.getOrNull(AuthUserKey) == null) {
                call.attributes.put(
                    AuthUserKey,
                    JwtUserClaims(
                        id = "user_123",
                        username = "testuser",
                        role = "USER",
                        approvalStatus = "APPROVED",
                        timeZone = "UTC",
                    ),
                )
            }
        }
        routing {
            route("/api") {
                userRoutes()
            }
        }
    }

    private class FakeUserApiKeyService(
        private val revokeResult: Either<AppError, Unit> = Unit.right(),
    ) : UserApiKeyService {
        val stored = mutableListOf<ApiKeyInfo>()
        var lastLabel: String? = null
        var lastScope: ApiKeyScope? = null
        var lastExpiresInDays: Long? = null
        var lastRevokedKeyId: String? = null

        override suspend fun generate(
            userId: String,
            label: String?,
            scope: ApiKeyScope,
            expiresInDays: Long?,
        ): Either<AppError, GeneratedApiKey> {
            lastLabel = label
            lastScope = scope
            lastExpiresInDays = expiresInDays
            return GeneratedApiKey(
                id = "new",
                key = "tday_new_secret",
                keyPreview = "cret",
                label = label,
                scope = scope.name,
                createdAt = "2026-01-01T00:00:00",
                expiresAt = null,
            ).right()
        }

        override suspend fun list(userId: String): Either<AppError, List<ApiKeyInfo>> = stored.right()

        override suspend fun revokeKey(userId: String, keyId: String): Either<AppError, Unit> {
            lastRevokedKeyId = keyId
            return revokeResult
        }

        override suspend fun revoke(userId: String): Either<AppError, Unit> = Unit.right()

        override suspend fun resolveKey(rawKey: String) = null
    }
}
