package com.ohmz.tday.plugins

import com.ohmz.tday.routes.mobileProbeRoutes
import com.ohmz.tday.security.AuthUserCache
import com.ohmz.tday.security.JwtService
import com.ohmz.tday.security.JwtServiceImpl
import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.security.testAppConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.get
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

class MobileVersionGateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `older mobile app is rejected before private api handler`() = testApplication {
        application {
            configureVersionGateHarness()
        }

        val response = client.get("/api/private") {
            header("X-Tday-Client", "android-compose")
            header("X-Tday-App-Version", "1.43.0")
        }

        assertEquals(HttpStatusCode(426, "Upgrade Required"), response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("app_update_required", payload.getValue("reason").jsonPrimitive.content)
        assertEquals("1.43.0", payload.getValue("appVersion").jsonPrimitive.content)
        assertEquals("1.44.0", payload.getValue("requiredVersion").jsonPrimitive.content)
    }

    @Test
    fun `newer mobile app is rejected as server update required`() = testApplication {
        application {
            configureVersionGateHarness()
        }

        val response = client.get("/api/private") {
            header("X-Tday-Client", "ios")
            header("X-Tday-App-Version", "1.45.0")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("server_update_required", payload.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun `web and mobile probe requests are not version gated`() = testApplication {
        application {
            configureVersionGateHarness()
        }

        val webResponse = client.get("/api/private")
        val probeResponse = client.get("/api/mobile/probe") {
            header("X-Tday-Client", "android-compose")
            header("X-Tday-App-Version", "1.43.0")
        }

        assertEquals(HttpStatusCode.OK, webResponse.status)
        assertEquals(HttpStatusCode.OK, probeResponse.status)
    }

    private fun Application.configureVersionGateHarness() {
        val config = testAppConfig(
            probeAppVersion = "1.44.0",
            probeUpdateRequired = true,
            probeCompatibilityMode = "exact",
        )
        install(Koin) {
            modules(
                module {
                    single { config }
                    single<JwtService> { JwtServiceImpl(config) }
                    single { AuthUserCache() }
                    single<SecurityEventLogger> { NoOpSecurityEventLogger() }
                },
            )
        }
        configureSerialization()
        configureSecurity()
        routing {
            route("/api") {
                get("/private") {
                    call.respond(mapOf("ok" to true))
                }
                mobileProbeRoutes(config)
            }
        }
    }

    private class NoOpSecurityEventLogger : SecurityEventLogger {
        override suspend fun log(reasonCode: String, details: Map<String, Any?>) = Unit
    }
}
