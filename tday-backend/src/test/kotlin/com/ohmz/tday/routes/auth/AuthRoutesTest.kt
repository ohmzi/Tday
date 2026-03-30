package com.ohmz.tday.routes.auth

import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.CredentialEnvelope
import com.ohmz.tday.security.CredentialEnvelopeInput
import com.ohmz.tday.security.CredentialPublicKeyDescriptor
import com.ohmz.tday.security.DecryptedCredentials
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.ThrottleAction
import com.ohmz.tday.security.ThrottleResult
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class AuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `session returns unauthorized message when auth user is missing`() = testApplication {
        val throttle = RecordingAuthThrottle()
        application {
            configureAuthRoutesTestApp(authThrottle = throttle)
        }

        val response = client.get("/api/auth/session")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Not authenticated", payload.getValue("message").jsonPrimitive.content)
        assertEquals(listOf(ThrottleAction.session), throttle.actions)
    }

    @Test
    fun `session returns authenticated user payload when auth user exists`() = testApplication {
        val throttle = RecordingAuthThrottle()
        application {
            configureAuthRoutesTestApp(
                authThrottle = throttle,
                authUser = JwtUserClaims(
                    id = "user_123",
                    name = "Test User",
                    email = "user@example.com",
                    role = "ADMIN",
                    approvalStatus = "APPROVED",
                    timeZone = "America/Toronto",
                ),
            )
        }

        val response = client.get("/api/auth/session")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val user = payload.getValue("user").jsonObject
        assertEquals("user_123", user.getValue("id").jsonPrimitive.content)
        assertEquals("Test User", user.getValue("name").jsonPrimitive.content)
        assertEquals("user@example.com", user.getValue("email").jsonPrimitive.content)
        assertEquals("ADMIN", user.getValue("role").jsonPrimitive.content)
        assertEquals("APPROVED", user.getValue("approvalStatus").jsonPrimitive.content)
        assertEquals("America/Toronto", user.getValue("timeZone").jsonPrimitive.content)
        assertEquals(listOf(ThrottleAction.session), throttle.actions)
    }

    @Test
    fun `credentials key returns descriptor when request is allowed`() = testApplication {
        val throttle = RecordingAuthThrottle()
        application {
            configureAuthRoutesTestApp(
                authThrottle = throttle,
                credentialEnvelope = FakeCredentialEnvelope(
                    descriptor = CredentialPublicKeyDescriptor(
                        version = "1",
                        algorithm = "RSA-OAEP-256+A256GCM",
                        keyId = "key_123",
                        publicKey = "pub_key_value",
                    ),
                ),
            )
        }

        val response = client.get("/api/auth/credentials-key")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<CredentialPublicKeyDescriptor>(response.bodyAsText())
        assertEquals("1", payload.version)
        assertEquals("RSA-OAEP-256+A256GCM", payload.algorithm)
        assertEquals("key_123", payload.keyId)
        assertEquals("pub_key_value", payload.publicKey)
        assertEquals(listOf(ThrottleAction.session), throttle.actions)
    }

    private fun Application.configureAuthRoutesTestApp(
        authThrottle: AuthThrottle,
        credentialEnvelope: CredentialEnvelope = FakeCredentialEnvelope(),
        authUser: JwtUserClaims? = null,
    ) {
        install(Koin) {
            modules(
                module {
                    single<AuthThrottle> { authThrottle }
                    single<CredentialEnvelope> { credentialEnvelope }
                },
            )
        }
        configureSerialization()
        if (authUser != null) {
            intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                if (call.attributes.getOrNull(AuthUserKey) == null) {
                    call.attributes.put(AuthUserKey, authUser)
                }
            }
        }
        routing {
            route("/api") {
                route("/auth") {
                    sessionRoutes()
                    credentialsKeyRoutes()
                }
            }
        }
    }

    private class RecordingAuthThrottle(
        private val result: ThrottleResult = ThrottleResult(allowed = true),
    ) : AuthThrottle {
        val actions = mutableListOf<ThrottleAction>()

        override suspend fun enforceRateLimit(
            action: ThrottleAction,
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ): ThrottleResult {
            actions += action
            return result
        }

        override suspend fun recordFailure(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override suspend fun clearFailures(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override suspend fun requiresCaptcha(
            action: ThrottleAction,
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ): Boolean = false

        override suspend fun recordSuccessSignal(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override fun formatRetryWait(seconds: Int): String = "${seconds}s"
    }

    private class FakeCredentialEnvelope(
        private val descriptor: CredentialPublicKeyDescriptor = CredentialPublicKeyDescriptor(
            version = "1",
            algorithm = "RSA-OAEP-256+A256GCM",
            keyId = "test-key",
            publicKey = "test-public-key",
        ),
    ) : CredentialEnvelope {
        override fun getPublicKeyDescriptor(): CredentialPublicKeyDescriptor = descriptor

        override fun decrypt(envelope: CredentialEnvelopeInput): DecryptedCredentials {
            error("decrypt should not be called in credentials key route tests")
        }
    }
}
