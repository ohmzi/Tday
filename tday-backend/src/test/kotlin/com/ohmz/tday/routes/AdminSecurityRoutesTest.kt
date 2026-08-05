package com.ohmz.tday.routes

import com.ohmz.tday.models.response.AbuseBlockResponse
import com.ohmz.tday.models.response.SecurityAlertResponse
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.AbuseGuard
import com.ohmz.tday.security.FakeAbuseGuard
import com.ohmz.tday.security.FakeSecurityAlertService
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.AdminService
import com.ohmz.tday.services.SecurityAlertService
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertTrue

/** The admin's view of the abuse blocks and the alert history, plus the manual unblock. */
class AdminSecurityRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val sampleBlock = AbuseBlockResponse(
        id = "block_1",
        subject = "a1b2c3d4e5f6",
        scope = "register",
        reason = "register_pending_flood",
        strikes = 2,
        blockedUntil = "2026-08-06T12:00",
        createdAt = "2026-08-05T12:00",
        updatedAt = "2026-08-05T12:00",
    )

    private val sampleAlert = SecurityAlertResponse(
        id = "alert_1",
        type = "abuse_block_applied",
        detail = "Blocked the register path for one source. — 12 further attempts suppressed",
        suppressedCount = 12,
        pushed = true,
        createdAt = "2026-08-05T12:00",
    )

    @Test
    fun `blocks lists what is currently blocked`() = testApplication {
        val guard = FakeAbuseGuard(blocks = mutableListOf(sampleBlock))
        application { configureAdminSecurityTestApp(guard = guard) }

        val response = client.get("/api/admin/security/blocks")

        assertEquals(HttpStatusCode.OK, response.status)
        val blocks = json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("blocks").jsonArray
        assertEquals(1, blocks.size)
        assertEquals("register", blocks[0].jsonObject.getValue("scope").jsonPrimitive.content)
        assertEquals("block_1", blocks[0].jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `alerts lists the dispatch history with its suppressed counts`() = testApplication {
        val alertService = FakeSecurityAlertService(alerts = mutableListOf(sampleAlert))
        application { configureAdminSecurityTestApp(alertService = alertService) }

        val response = client.get("/api/admin/security/alerts")

        assertEquals(HttpStatusCode.OK, response.status)
        val alerts = json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("alerts").jsonArray
        assertEquals(1, alerts.size)
        assertEquals("abuse_block_applied", alerts[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("12", alerts[0].jsonObject.getValue("suppressedCount").jsonPrimitive.content)
    }

    @Test
    fun `clear lifts the named block`() = testApplication {
        val guard = FakeAbuseGuard()
        application { configureAdminSecurityTestApp(guard = guard) }

        val response = client.post("/api/admin/security/blocks/block_1/clear")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("block_1"), guard.clearedBlockIds)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("block cleared", payload.getValue("message").jsonPrimitive.content)
    }

    @Test
    fun `a non-admin cannot see or lift blocks`() = testApplication {
        val guard = FakeAbuseGuard(blocks = mutableListOf(sampleBlock))
        application {
            configureAdminSecurityTestApp(
                guard = guard,
                authUser = approvedUser(role = "USER"),
            )
        }

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/security/blocks").status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/admin/security/blocks/block_1/clear").status)
        assertTrue(guard.clearedBlockIds.isEmpty())
    }

    @Test
    fun `an unauthenticated caller gets nothing`() = testApplication {
        application { configureAdminSecurityTestApp(authUser = null) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/security/alerts").status)
    }

    private fun approvedUser(role: String) = JwtUserClaims(
        id = "user_1",
        name = "Owner",
        username = "owner",
        role = role,
        approvalStatus = "APPROVED",
    )

    private fun Application.configureAdminSecurityTestApp(
        guard: AbuseGuard = FakeAbuseGuard(),
        alertService: SecurityAlertService = FakeSecurityAlertService(),
        authUser: JwtUserClaims? = approvedUser(role = "ADMIN"),
    ) {
        install(Koin) {
            modules(
                module {
                    single<AbuseGuard> { guard }
                    single<SecurityAlertService> { alertService }
                    // Never resolved by these routes; present so the shared adminRoutes() block
                    // has everything it declares.
                    single<AdminService> { error("AdminService should not be used here") }
                },
            )
        }
        configureSerialization()
        if (authUser != null) {
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.attributes.getOrNull(AuthUserKey) == null) {
                    call.attributes.put(AuthUserKey, authUser)
                }
            }
        }
        routing {
            route("/api") {
                adminRoutes()
            }
        }
    }
}
