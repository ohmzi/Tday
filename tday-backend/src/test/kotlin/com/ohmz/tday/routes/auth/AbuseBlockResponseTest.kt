package com.ohmz.tday.routes.auth

import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.AbuseBlockVerdict
import com.ohmz.tday.security.AbuseGuard
import com.ohmz.tday.security.AbuseScope
import com.ohmz.tday.security.AuthThrottle
import com.ohmz.tday.security.FailureOutcome
import com.ohmz.tday.security.FakeAbuseGuard
import com.ohmz.tday.security.FakeSecurityAlertService
import com.ohmz.tday.security.PasswordProof
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
import io.ktor.server.request.ApplicationRequest
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a blocked caller actually receives.
 *
 * Two contracts are asserted: a real 429 with Retry-After (built through respondRateLimit, not a
 * hand-rolled `Map<String, Any>` — that pattern used to throw and produce a 500), and a response
 * that gives nothing away. Same status, same `auth_limit` reason and same wording an ordinary
 * rate limit produces, so probing cannot tell a block apart from a busy window.
 */
class AbuseBlockResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a blocked register attempt is refused before the body is even read`() = testApplication {
        val guard = FakeAbuseGuard(verdict = AbuseBlockVerdict(blocked = true, retryAfterSeconds = 3600))
        application { configureAbuseBlockedApp(guard) }

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("not even valid json")
        }

        assertBlocked(response, expectedRetryAfter = "3600")
        assertEquals(listOf(AbuseScope.register), guard.checkedScopes)
    }

    @Test
    fun `a blocked sign-in never reaches password verification`() = testApplication {
        val guard = FakeAbuseGuard(verdict = AbuseBlockVerdict(blocked = true, retryAfterSeconds = 86400))
        application { configureAbuseBlockedApp(guard) }

        val response = client.post("/api/auth/callback/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"owner","password":"whatever"}""")
        }

        assertBlocked(response, expectedRetryAfter = "86400")
        assertEquals(listOf(AbuseScope.auth), guard.checkedScopes)
    }

    @Test
    fun `the reset lookup is blocked under the auth scope, not the register one`() = testApplication {
        val guard = FakeAbuseGuard(verdict = AbuseBlockVerdict(blocked = true, retryAfterSeconds = 60))
        application { configureAbuseBlockedApp(guard) }

        val response = client.get("/api/auth/security-questions?username=owner")

        assertBlocked(response, expectedRetryAfter = "60")
        assertEquals(listOf(AbuseScope.auth), guard.checkedScopes)
    }

    @Test
    fun `an unblocked caller passes straight through to the throttle`() = testApplication {
        val guard = FakeAbuseGuard()
        application { configureAbuseBlockedApp(guard) }

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"fname":"A","lname":"B","username":"someone","password":"Passw0rd!"}""")
        }

        // The throttle below the guard rejects it; the point is that the guard did not.
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("9", response.headers["Retry-After"])
        // A rejected request is itself a register-path violation signal.
        assertTrue(guard.signals.any { it.first == AbuseScope.register })
    }

    private suspend fun assertBlocked(response: HttpResponse, expectedRetryAfter: String) {
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals(expectedRetryAfter, response.headers["Retry-After"])
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        // Indistinguishable from an ordinary rate limit: no reason code of its own.
        assertEquals("auth_limit", payload.getValue("reason").jsonPrimitive.content)
        val message = payload.getValue("message").jsonPrimitive.content
        assertFalse(message.contains("block", ignoreCase = true), "message leaked the block: $message")
    }

    private fun Application.configureAbuseBlockedApp(guard: AbuseGuard) {
        install(Koin) {
            modules(
                module {
                    single<AbuseGuard> { guard }
                    single<AuthThrottle> { AlwaysThrottledAuthThrottle() }
                    single<SecurityAlertService> { FakeSecurityAlertService() }
                    single<PasswordProof> { error("password proof must not be reached") }
                },
            )
        }
        configureSerialization()
        configureStatusPages()
        routing {
            route("/api") {
                route("/auth") {
                    registerRoutes()
                    credentialsCallbackRoutes()
                    securityQuestionRoutes()
                }
            }
        }
    }

    /** Rejects everything, so any request that gets past the guard is visible as a 429 with 9s. */
    private class AlwaysThrottledAuthThrottle : AuthThrottle {
        override suspend fun enforceRateLimit(
            action: ThrottleAction,
            request: ApplicationRequest,
            identifier: String?,
        ): ThrottleResult = ThrottleResult(allowed = false, reasonCode = "auth_limit", retryAfterSeconds = 9)

        override suspend fun recordFailure(request: ApplicationRequest, identifier: String?): FailureOutcome =
            FailureOutcome()

        override suspend fun clearFailures(request: ApplicationRequest, identifier: String?) = Unit

        override suspend fun recordSuccessSignal(request: ApplicationRequest, identifier: String?) = Unit

        override fun formatRetryWait(seconds: Int): String = "${seconds}s"
    }
}
