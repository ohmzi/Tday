package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.CalendarFeedService
import com.ohmz.tday.services.CalendarFeedStatus
import com.ohmz.tday.services.CalendarFeedToken
import io.ktor.client.request.get
import io.ktor.client.request.post
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
import kotlin.test.assertTrue

class CalendarFeedRoutesTest {
    @Test
    fun `valid token returns an ICS body with the calendar content type`() = testApplication {
        val service = FakeCalendarFeedService()
        application { configureFeedApp(service) }

        val response = client.get("/calendar/tok123_secret.ics")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.headers["Content-Type"]?.startsWith("text/calendar") == true,
            "expected text/calendar, got ${response.headers["Content-Type"]}",
        )
        assertTrue(response.bodyAsText().startsWith("BEGIN:VCALENDAR"))
        // The `.ics` suffix is stripped before resolution.
        assertEquals("tok123_secret", service.lastRenderedToken)
    }

    @Test
    fun `unknown token returns 404`() = testApplication {
        val service = FakeCalendarFeedService()
        application { configureFeedApp(service) }

        val response = client.get("/calendar/nope_bad.ics")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `management create returns the feed token once`() = testApplication {
        val service = FakeCalendarFeedService()
        application {
            install(Koin) {
                modules(module { single<CalendarFeedService> { service } })
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
            routing { route("/api") { userRoutes() } }
        }

        val response = client.post("/api/user/calendar-feed")

        assertEquals(HttpStatusCode.OK, response.status)
        val token = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["feed"]!!.jsonObject["token"]!!.jsonPrimitive.content
        assertEquals("id_secret", token)
    }

    private fun Application.configureFeedApp(service: CalendarFeedService) {
        install(Koin) {
            modules(module { single<CalendarFeedService> { service } })
        }
        configureSerialization()
        routing {
            calendarFeedRoutes()
        }
    }

    private class FakeCalendarFeedService : CalendarFeedService {
        var lastRenderedToken: String? = null

        override suspend fun generate(userId: String): Either<AppError, CalendarFeedToken> =
            CalendarFeedToken(token = "id_secret", tokenPreview = "cret", createdAt = "2026-01-01T00:00:00").right()

        override suspend fun status(userId: String): Either<AppError, CalendarFeedStatus> =
            CalendarFeedStatus(enabled = false).right()

        override suspend fun revoke(userId: String): Either<AppError, Unit> = Unit.right()

        override suspend fun renderIcs(rawToken: String): String? {
            lastRenderedToken = rawToken
            return if (rawToken == "tok123_secret") "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n" else null
        }
    }
}
