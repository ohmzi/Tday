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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class PreferencesRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `patch preferences rejects invalid group by`() = testApplication {
        val preferencesService = RecordingPreferencesService()

        application {
            configurePreferencesRoutesTestApp(preferencesService)
        }

        val response = client.patch("/api/preferences") {
            contentType(ContentType.Application.Json)
            setBody("""{"groupBy":"missing"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("groupBy is invalid", payload.getValue("message").jsonPrimitive.content)
        assertEquals("groupBy", payload.getValue("field").jsonPrimitive.content)
        assertEquals(null, preferencesService.lastGroupBy)
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
                preferencesRoutes()
            }
        }
    }

    private class RecordingPreferencesService : PreferencesService {
        var lastGroupBy: String? = null

        override suspend fun get(userId: String): Either<AppError, PreferencesResponse> =
            PreferencesResponse().right()

        override suspend fun update(
            userId: String,
            sortBy: String?,
            groupBy: String?,
            direction: String?,
        ): Either<AppError, Unit> {
            lastGroupBy = groupBy
            return Unit.right()
        }
    }
}
