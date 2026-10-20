package com.ohmz.tday.routes

import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.TodoSummaryService
import com.ohmz.tday.shared.model.AppSettingsResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reports ai unconfigured when ollama is absent`() = testApplication {
        application { configureApp(FakeSummaryService(configured = false, healthy = false)) }

        val response = client.get("/api/app-settings")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<AppSettingsResponse>(response.bodyAsText())
        assertFalse(body.aiSummaryConfigured)
        assertFalse(body.aiSummaryHealthy)
    }

    @Test
    fun `reports configured but down when model is unhealthy`() = testApplication {
        application { configureApp(FakeSummaryService(configured = true, healthy = false)) }

        val body = json.decodeFromString<AppSettingsResponse>(client.get("/api/app-settings").bodyAsText())
        assertTrue(body.aiSummaryConfigured)
        assertFalse(body.aiSummaryHealthy)
    }

    @Test
    fun `reports configured and healthy when model is live`() = testApplication {
        application { configureApp(FakeSummaryService(configured = true, healthy = true)) }

        val body = json.decodeFromString<AppSettingsResponse>(client.get("/api/app-settings").bodyAsText())
        assertTrue(body.aiSummaryConfigured)
        assertTrue(body.aiSummaryHealthy)
    }

    private fun Application.configureApp(summaryService: TodoSummaryService) {
        install(Koin) {
            modules(module { single<TodoSummaryService> { summaryService } })
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
                        role = "USER",
                        approvalStatus = "APPROVED",
                        timeZone = "UTC",
                    ),
                )
            }
        }
        routing {
            route("/api") {
                appSettingsRoutes()
            }
        }
    }

    private class FakeSummaryService(
        private val configured: Boolean,
        private val healthy: Boolean,
    ) : TodoSummaryService {
        override suspend fun generateSummary(prompt: String): String? = null
        override suspend fun isHealthy(): Boolean = healthy
        override suspend fun warmUp() = Unit
        override fun isConfigured(): Boolean = configured
        override suspend fun healthyCached(): Boolean = configured && healthy
    }
}
