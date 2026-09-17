package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.models.response.PreferencesResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.PreferencesService
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PREFERENCES_PATH = "/api/preferences"

class PreferencesRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `patch preferences rejects invalid group by`() = testApplication {
        val preferencesService = RecordingPreferencesService()

        application {
            configurePreferencesRoutesTestApp(preferencesService)
        }

        val response = client.patch(PREFERENCES_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"groupBy":"missing"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("groupBy is invalid", payload.getValue("message").jsonPrimitive.content)
        assertEquals("groupBy", payload.getValue("field").jsonPrimitive.content)
        assertEquals(null, preferencesService.lastGroupBy)
    }

    @Test
    fun `patch preferences rejects invalid default home screen`() = testApplication {
        val preferencesService = RecordingPreferencesService()

        application {
            configurePreferencesRoutesTestApp(preferencesService)
        }

        val response = client.patch(PREFERENCES_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"defaultHomeScreen":"missing"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("defaultHomeScreen is invalid", payload.getValue("message").jsonPrimitive.content)
        assertEquals("defaultHomeScreen", payload.getValue("field").jsonPrimitive.content)
        assertEquals(null, preferencesService.lastDefaultHomeScreen)
    }

    @Test
    fun `patch preferences accepts valid default home screen`() = testApplication {
        val preferencesService = RecordingPreferencesService()

        application {
            configurePreferencesRoutesTestApp(preferencesService)
        }

        val response = client.patch(PREFERENCES_PATH) {
            contentType(ContentType.Application.Json)
            setBody("""{"defaultHomeScreen":"floater"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("floater", preferencesService.lastDefaultHomeScreen)

        // The response has to CARRY the stored preferences, not just acknowledge the write.
        // Web renders the "Default home screen" thumb from this body and Android mirrors
        // `defaultHomeScreen` straight into its launch cache, so a message-only body made both
        // of them fall back to their own Scheduled default the moment the write succeeded.
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("floater", payload.getValue("defaultHomeScreen").jsonPrimitive.content)
        assertTrue(payload.getValue("aiSummaryEnabled").jsonPrimitive.boolean)
    }

    private fun Application.configurePreferencesRoutesTestApp(
        preferencesService: PreferencesService,
    ) {
        install(Koin) {
            modules(
                module {
                    single<PreferencesService> { preferencesService }
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
                preferencesRoutes()
            }
        }
    }

    private class RecordingPreferencesService : PreferencesService {
        var lastGroupBy: String? = null

        override suspend fun get(userId: String): Either<AppError, PreferencesResponse> =
            PreferencesResponse().right()

        var lastAiSummaryEnabled: Boolean? = null
        var lastDefaultHomeScreen: String? = null

        override suspend fun update(
            userId: String,
            sortBy: String?,
            groupBy: String?,
            direction: String?,
            aiSummaryEnabled: Boolean?,
            defaultHomeScreen: String?,
        ): Either<AppError, PreferencesResponse> {
            lastGroupBy = groupBy
            lastAiSummaryEnabled = aiSummaryEnabled
            lastDefaultHomeScreen = defaultHomeScreen
            // Mirrors the real service: the patch is applied and the stored row is read back.
            return PreferencesResponse(
                defaultHomeScreen = defaultHomeScreen ?: "scheduled",
                aiSummaryEnabled = aiSummaryEnabled ?: true,
            ).right()
        }
    }
}
