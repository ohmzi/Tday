package com.ohmz.tday.routes

import com.ohmz.tday.db.TestDatabase
import com.ohmz.tday.db.tables.InstanceSettings
import com.ohmz.tday.observability.TelemetryGate
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import com.ohmz.tday.security.AbuseGuard
import com.ohmz.tday.security.FakeAbuseGuard
import com.ohmz.tday.security.FakeSecurityAlertService
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.services.AdminService
import com.ohmz.tday.services.InstanceSettingsService
import com.ohmz.tday.services.InstanceSettingsServiceImpl
import com.ohmz.tday.services.SecurityAlertService
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The admin's switch for the server's own error reports: `GET`/`PATCH /api/admin/telemetry`. */
class AdminTelemetryRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val db: Database = TestDatabase.fresh()
    private val gate = TelemetryGate()

    @AfterEach
    fun tearDown() {
        TestDatabase.close(db)
    }

    @Test
    fun `reports off with no update time on a fresh instance`() = testApplication {
        application { configureTelemetryApp(dsn = DSN) }

        val response = client.get("/api/admin/telemetry")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(true, body.getValue("dsnConfigured").jsonPrimitive.boolean)
        assertEquals(false, body.getValue("enabled").jsonPrimitive.boolean)
        assertEquals(JsonNull, body.getValue("updatedAt"))
    }

    @Test
    fun `patch round trips and opens then closes the gate`() = testApplication {
        application { configureTelemetryApp(dsn = DSN) }

        val enabled = client.patchEnabled(true)
        assertEquals(HttpStatusCode.OK, enabled.status)
        val enabledBody = json.parseToJsonElement(enabled.bodyAsText()).jsonObject
        assertEquals(true, enabledBody.getValue("enabled").jsonPrimitive.boolean)
        Instant.parse(enabledBody.getValue("updatedAt").jsonPrimitive.content)
        assertTrue(gate.isOpen)

        val read = json.parseToJsonElement(client.get("/api/admin/telemetry").bodyAsText()).jsonObject
        assertEquals(enabledBody, read)

        val disabled = client.patchEnabled(false)
        assertEquals(HttpStatusCode.OK, disabled.status)
        assertEquals(
            false,
            json.parseToJsonElement(disabled.bodyAsText()).jsonObject.getValue("enabled").jsonPrimitive.boolean,
        )
        assertFalse(gate.isOpen)
    }

    @Test
    fun `without a dsn the flag is still stored and the response says so`() = testApplication {
        application { configureTelemetryApp(dsn = null) }

        val response = client.patchEnabled(true)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(false, body.getValue("dsnConfigured").jsonPrimitive.boolean)
        assertEquals(true, body.getValue("enabled").jsonPrimitive.boolean)
    }

    @Test
    fun `a non-admin is refused and nothing changes`() = testApplication {
        application { configureTelemetryApp(dsn = DSN, authUser = approvedUser(role = "USER")) }

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/telemetry").status)
        assertEquals(HttpStatusCode.Forbidden, client.patchEnabled(true).status)
        assertFalse(gate.isOpen)
        transaction(db) { assertEquals(0, InstanceSettings.selectAll().count()) }
    }

    @Test
    fun `an unauthenticated caller gets nothing`() = testApplication {
        application { configureTelemetryApp(dsn = DSN, authUser = null) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/telemetry").status)
        assertEquals(HttpStatusCode.Unauthorized, client.patchEnabled(true).status)
        assertFalse(gate.isOpen)
    }

    @Test
    fun `a body without the flag is a bad request`() = testApplication {
        application { configureTelemetryApp(dsn = DSN) }

        val response = client.patch("/api/admin/telemetry") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertFalse(gate.isOpen)
    }

    private suspend fun io.ktor.client.HttpClient.patchEnabled(enabled: Boolean) =
        patch("/api/admin/telemetry") {
            contentType(ContentType.Application.Json)
            setBody("""{"enabled":$enabled}""")
        }

    private fun approvedUser(role: String) = JwtUserClaims(
        id = "user_1",
        name = "Owner",
        username = "owner",
        role = role,
        approvalStatus = "APPROVED",
    )

    private fun Application.configureTelemetryApp(
        dsn: String?,
        authUser: JwtUserClaims? = approvedUser(role = "ADMIN"),
    ) {
        val service = InstanceSettingsServiceImpl(
            config = testAppConfig().copy(sentryDsn = dsn),
            telemetryGate = gate,
            securityEventLogger = NoOpSecurityEventLogger(),
        )
        install(Koin) {
            modules(
                module {
                    single<InstanceSettingsService> { service }
                    // Never resolved by these routes; present so the shared adminRoutes() block
                    // has everything it declares.
                    single<AdminService> { error("AdminService should not be used here") }
                    single<AbuseGuard> { FakeAbuseGuard() }
                    single<SecurityAlertService> { FakeSecurityAlertService() }
                },
            )
        }
        configureSerialization()
        configureStatusPages()
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

    private class NoOpSecurityEventLogger : SecurityEventLogger {
        override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
    }

    private companion object {
        const val DSN = "https://publickey@o1.ingest.sentry.io/1"
    }
}
