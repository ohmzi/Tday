package com.ohmz.tday.routes

import arrow.core.right
import com.ohmz.tday.domain.requireApprovedAuthUser
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SecurityEnforcementTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `pending approval session is rejected on private REST route`() = testApplication {
        application {
            configureSerialization()
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.attributes.getOrNull(AuthUserKey) == null) {
                    call.attributes.put(
                        AuthUserKey,
                        JwtUserClaims(
                            id = "user_pending",
                            email = "pending@example.com",
                            role = "USER",
                            approvalStatus = "PENDING",
                            timeZone = "UTC",
                        ),
                    )
                }
            }
            routing {
                route("/api") {
                    get("/private") {
                        call.withAuth {
                            mapOf("ok" to true).right()
                        }
                    }
                }
            }
        }

        val response = client.get("/api/private")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(
            "your account is awaiting admin approval",
            payload.getValue("message").jsonPrimitive.content,
        )
    }

    @Test
    fun `pending approval session is rejected on websocket handshake`() = testApplication {
        application {
            configureSerialization()
            install(ServerWebSockets)
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.attributes.getOrNull(AuthUserKey) == null) {
                    call.attributes.put(
                        AuthUserKey,
                        JwtUserClaims(
                            id = "user_pending",
                            email = "pending@example.com",
                            role = "USER",
                            approvalStatus = "PENDING",
                            timeZone = "UTC",
                        ),
                    )
                }
            }
            routing {
                webSocket("/ws") {
                    when (val authResult = call.requireApprovedAuthUser()) {
                        is arrow.core.Either.Left -> {
                            return@webSocket close(
                                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Pending approval"),
                            )
                        }

                        is arrow.core.Either.Right -> Unit
                    }
                }
            }
        }

        val wsClient = createClient {
            install(WebSockets)
        }

        val session = wsClient.webSocketSession("/ws")
        val closeReason = assertNotNull(session.closeReason.await())

        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.code)
        assertEquals("Pending approval", closeReason.message)
    }
}
