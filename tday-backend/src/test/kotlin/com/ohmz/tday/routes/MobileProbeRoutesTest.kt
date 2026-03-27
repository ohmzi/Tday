package com.ohmz.tday.routes

import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.shared.model.MobileProbeResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileProbeRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `probe returns shared mobile contract`() = testApplication {
        application {
            configureSerialization()

            routing {
                route("/api") {
                    mobileProbeRoutes()
                }
            }
        }

        val response = client.get("/api/mobile/probe")

        assertEquals(HttpStatusCode.OK, response.status)

        val payload = json.decodeFromString<MobileProbeResponse>(response.bodyAsText())
        assertEquals("tday", payload.service)
        assertEquals("ok", payload.probe)
        assertEquals("1", payload.version)
        assertTrue(payload.serverTime.isNotBlank())
    }
}
