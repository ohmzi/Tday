package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.PushNotificationService
import com.ohmz.tday.services.TRANSPORT_UNIFIEDPUSH
import com.ohmz.tday.services.TRANSPORT_WEBPUSH
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class NotificationRoutesTest {
    @Test
    fun `unifiedpush subscribe forwards transport with no keys`() = testApplication {
        val service = FakePushService()
        application { configureApp(service) }

        val response = client.post("/api/notifications/subscribe") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"https://ntfy.example/UP123","transport":"unifiedpush"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("https://ntfy.example/UP123", service.lastEndpoint)
        assertEquals(TRANSPORT_UNIFIEDPUSH, service.lastTransport)
        assertEquals("", service.lastP256dh)
    }

    @Test
    fun `web push subscribe defaults the transport`() = testApplication {
        val service = FakePushService()
        application { configureApp(service) }

        val response = client.post("/api/notifications/subscribe") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"https://push.example/abc","p256dh":"k","auth":"a"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(TRANSPORT_WEBPUSH, service.lastTransport)
        assertEquals("k", service.lastP256dh)
    }

    private fun Application.configureApp(service: PushNotificationService) {
        install(Koin) {
            modules(module { single<PushNotificationService> { service } })
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
        routing { route("/api") { notificationRoutes() } }
    }

    private class FakePushService : PushNotificationService {
        var lastEndpoint: String? = null
        var lastP256dh: String? = null
        var lastTransport: String? = null

        override suspend fun subscribe(
            userId: String,
            endpoint: String,
            p256dh: String,
            auth: String,
            transport: String,
        ): Either<AppError, Unit> {
            lastEndpoint = endpoint
            lastP256dh = p256dh
            lastTransport = transport
            return Unit.right()
        }

        override suspend fun unsubscribe(userId: String, endpoint: String): Either<AppError, Unit> = Unit.right()

        override suspend fun sendToUser(
            userId: String,
            title: String,
            body: String,
            url: String?,
            todoId: String?,
        ): Either<AppError, Unit> = Unit.right()

        override fun isConfigured(): Boolean = true

        override fun getVapidPublicKey(): String? = null
    }
}
