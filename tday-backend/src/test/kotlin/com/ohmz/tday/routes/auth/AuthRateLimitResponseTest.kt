package com.ohmz.tday.routes.auth

import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.AbuseGuard
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.FailureOutcome
import com.ohmz.tday.security.FakeAbuseGuard
import com.ohmz.tday.security.FakeSecurityAlertService
import com.ohmz.tday.security.PasswordProof
import com.ohmz.tday.security.PasswordProofChallengePayload
import com.ohmz.tday.security.ThrottleAction
import com.ohmz.tday.security.ThrottleResult
import com.ohmz.tday.services.SecurityAlertService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
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

/**
 * Every throttled auth route must answer a blocked request with a real 429.
 *
 * Regression guard: these handlers used to build the body as
 * `mapOf("message" to String, ..., "retryAfterSeconds" to Int)`, which Kotlin infers as
 * `Map<String, Any>`. kotlinx.serialization has no serializer for `Any`, so `respond` threw,
 * StatusPages caught it, and the caller got a 500 with no Retry-After — silently turning
 * every lockout into an unhandled exception (and a Sentry event) per blocked attempt.
 *
 * StatusPages is installed here on purpose: without it a serialization failure surfaces as a
 * test-harness exception rather than the 500 a real client would have seen.
 */
class AuthRateLimitResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `login callback returns 429 with retry-after when locked out`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 42, reasonCode = "auth_lockout") }

        val response = client.post("/api/auth/callback/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"someone","password":"wrong"}""")
        }

        assertRateLimited(response, expectedReason = "auth_lockout", expectedRetryAfter = "42")
    }

    @Test
    fun `login challenge returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 11) }

        val response = client.post("/api/auth/login-challenge") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"someone"}""")
        }

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "11")
    }

    @Test
    fun `register returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 33) }

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"fname":"A","lname":"B","username":"someone","password":"Passw0rd!"}""")
        }

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "33")
    }

    @Test
    fun `csrf returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 5) }

        val response = client.get("/api/auth/csrf")

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "5")
    }

    @Test
    fun `security questions lookup returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 8) }

        val response = client.get("/api/auth/security-questions?username=someone")

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "8")
    }

    @Test
    fun `verify security answers returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 13) }

        val response = client.post("/api/auth/verify-security-answers") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"someone","answers":[]}""")
        }

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "13")
    }

    @Test
    fun `self service reset returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 17) }

        val response = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"someone","answers":[],"newPassword":"Passw0rd!"}""")
        }

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "17")
    }

    @Test
    fun `request admin reset returns 429 with retry-after when throttled`() = testApplication {
        application { configureThrottledAuthApp(retryAfterSeconds = 21) }

        val response = client.post("/api/auth/request-admin-reset") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"someone"}""")
        }

        assertRateLimited(response, expectedReason = "auth_limit", expectedRetryAfter = "21")
    }

    private suspend fun assertRateLimited(
        response: HttpResponse,
        expectedReason: String,
        expectedRetryAfter: String,
    ) {
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(expectedRetryAfter, response.headers["Retry-After"])
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(expectedReason, payload.getValue("reason").jsonPrimitive.content)
        assertEquals(expectedRetryAfter, payload.getValue("retryAfterSeconds").jsonPrimitive.content)
    }

    /**
     * Registers only what a *blocked* request actually resolves. The throttle short-circuits
     * before any service is touched, and [com.ohmz.tday.di.inject] is lazy, so UserService and
     * friends are never instantiated. PasswordProof is the exception: login-challenge
     * normalizes the username before consulting the throttle.
     */
    private fun Application.configureThrottledAuthApp(
        retryAfterSeconds: Int,
        reasonCode: String = "auth_limit",
    ) {
        install(Koin) {
            modules(
                module {
                    single<AuthThrottle> {
                        BlockingAuthThrottle(
                            ThrottleResult(
                                allowed = false,
                                reasonCode = reasonCode,
                                retryAfterSeconds = retryAfterSeconds,
                            ),
                        )
                    }
                    single<PasswordProof> { PassThroughPasswordProof() }
                    single<AbuseGuard> { FakeAbuseGuard() }
                    single<SecurityAlertService> { FakeSecurityAlertService() }
                },
            )
        }
        configureSerialization()
        configureStatusPages()
        routing {
            route("/api") {
                route("/auth") {
                    credentialsCallbackRoutes()
                    loginChallengeRoutes()
                    registerRoutes()
                    csrfRoutes()
                    securityQuestionRoutes()
                }
            }
        }
    }

    private class BlockingAuthThrottle(private val result: ThrottleResult) : AuthThrottle {
        override suspend fun enforceRateLimit(
            action: ThrottleAction,
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ): ThrottleResult = result

        override suspend fun recordFailure(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ): FailureOutcome = FailureOutcome()

        override suspend fun clearFailures(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override suspend fun recordSuccessSignal(
            request: io.ktor.server.request.ApplicationRequest,
            identifier: String?,
        ) = Unit

        override fun formatRetryWait(seconds: Int): String = "${seconds}s"
    }

    private class PassThroughPasswordProof : PasswordProof {
        override fun normalizeUsername(value: String?): String? = value?.trim()?.lowercase()

        override fun issueChallenge(username: String, storedPasswordHash: String?): PasswordProofChallengePayload =
            throw UnsupportedOperationException("blocked before challenge issuance")

        override fun verify(
            username: String,
            challengeId: String,
            proofHex: String,
            proofVersion: String?,
            storedPasswordHash: String?,
        ): Boolean = false

        override fun consume(challengeId: String) = Unit
    }
}
