package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.CreatedWebhook
import com.ohmz.tday.services.WebhookInfo
import com.ohmz.tday.services.WebhookService
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class WebhookRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `list returns the caller subscriptions`() = testApplication {
        val service = FakeWebhookService()
        service.stored += WebhookInfo(
            id = "w1",
            url = "https://example.com/hook",
            events = listOf("todo.changed"),
            enabled = true,
            consecutiveFailures = 0,
            createdAt = "2026-01-01T00:00:00",
        )
        application { configureApp(service) }

        val response = client.get("/api/webhook")

        assertEquals(HttpStatusCode.OK, response.status)
        val hooks = json.parseToJsonElement(response.bodyAsText()).jsonObject["webhooks"]!!.jsonArray
        assertEquals(1, hooks.size)
        assertEquals("https://example.com/hook", hooks[0].jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `create forwards url and events and returns the secret once`() = testApplication {
        val service = FakeWebhookService()
        application { configureApp(service) }

        val response = client.post("/api/webhook") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"https://example.com/hook","events":["todo.changed","list.changed"]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("https://example.com/hook", service.lastUrl)
        assertEquals(listOf("todo.changed", "list.changed"), service.lastEvents)
        val secret = json.parseToJsonElement(response.bodyAsText())
            .jsonObject["webhook"]!!.jsonObject["secret"]!!.jsonPrimitive.content
        assertEquals("whsec_test", secret)
    }

    @Test
    fun `create rejects a non-http url`() = testApplication {
        val service = FakeWebhookService(createResult = AppError.BadRequest("url must be an http(s) URL").left())
        application { configureApp(service) }

        val response = client.post("/api/webhook") {
            contentType(ContentType.Application.Json)
            setBody("""{"url":"ftp://nope"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete removes a subscription by id`() = testApplication {
        val service = FakeWebhookService()
        application { configureApp(service) }

        val response = client.delete("/api/webhook/w1")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("w1", service.lastDeletedId)
    }

    @Test
    fun `delete of an unknown id surfaces not found`() = testApplication {
        val service = FakeWebhookService(deleteResult = AppError.NotFound("webhook not found").left())
        application { configureApp(service) }

        val response = client.delete("/api/webhook/missing")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `event-types lists the valid filter values`() = testApplication {
        val service = FakeWebhookService()
        application { configureApp(service) }

        val response = client.get("/api/webhook/event-types")

        assertEquals(HttpStatusCode.OK, response.status)
        val types = json.parseToJsonElement(response.bodyAsText()).jsonObject["eventTypes"]!!.jsonArray
        assertEquals(6, types.size)
    }

    private fun Application.configureApp(service: WebhookService) {
        install(Koin) {
            modules(module { single<WebhookService> { service } })
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
                webhookRoutes()
            }
        }
    }

    private class FakeWebhookService(
        private val createResult: Either<AppError, CreatedWebhook>? = null,
        private val deleteResult: Either<AppError, Unit> = Unit.right(),
    ) : WebhookService {
        val stored = mutableListOf<WebhookInfo>()
        var lastUrl: String? = null
        var lastEvents: List<String>? = null
        var lastDeletedId: String? = null

        override suspend fun list(userId: String): Either<AppError, List<WebhookInfo>> = stored.right()

        override suspend fun create(
            userId: String,
            url: String,
            events: List<String>,
        ): Either<AppError, CreatedWebhook> {
            lastUrl = url
            lastEvents = events
            return createResult ?: CreatedWebhook(
                id = "new",
                url = url,
                events = events,
                secret = "whsec_test",
                createdAt = "2026-01-01T00:00:00",
            ).right()
        }

        override suspend fun delete(userId: String, id: String): Either<AppError, Unit> {
            lastDeletedId = id
            return deleteResult
        }
    }
}
