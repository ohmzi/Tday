package com.ohmz.tday.routes

import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.testAppConfig
import com.ohmz.tday.shared.model.MobileProbeResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobileProbeRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `probe returns shared mobile contract`() = testApplication {
        application {
            configureSerialization()

            routing {
                route("/api") {
                    mobileProbeRoutes(testAppConfig())
                }
            }
        }

        val response = client.get("/api/mobile/probe")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", response.headers[HttpHeaders.Pragma])

        val payload = json.decodeFromString<MobileProbeResponse>(response.bodyAsText())
        assertEquals("tday", payload.service)
        assertEquals("ok", payload.probe)
        assertEquals("1", payload.version)
        assertTrue(payload.serverTime.isNotBlank())
        assertNull(payload.appVersion)
        assertNull(payload.encryptedCompatibility)
    }

    @Test
    fun `probe returns plain app version when configured without encryption`() = testApplication {
        application {
            configureSerialization()

            routing {
                route("/api") {
                    mobileProbeRoutes(
                        testAppConfig(probeAppVersion = "1.44.0"),
                    )
                }
            }
        }

        val response = client.get("/api/mobile/probe")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<MobileProbeResponse>(response.bodyAsText())
        assertEquals("1.44.0", payload.appVersion)
        assertNull(payload.encryptedCompatibility)
    }

    @Test
    fun `probe returns encrypted compatibility payload when version enforcement is configured`() = testApplication {
        application {
            configureSerialization()

            routing {
                route("/api") {
                    mobileProbeRoutes(
                        testAppConfig(
                            probeAppVersion = "1.44.0",
                            probeUpdateRequired = true,
                            probeEncryptionKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        ),
                    )
                }
            }
        }

        val response = client.get("/api/mobile/probe")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<MobileProbeResponse>(response.bodyAsText())
        assertEquals("1.44.0", payload.appVersion)
        assertTrue(payload.encryptedCompatibility?.isNotBlank() == true)
    }
}
