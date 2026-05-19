package com.ohmz.tday.routes

import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.testAppConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleAppSiteAssociationRoutesTest {
    @Test
    fun `well-known AASA route returns webcredentials payload`() = testApplication {
        application {
            configureSerialization()
            routing {
                appleAppSiteAssociationRoutes(testAppConfig())
            }
        }

        val response = client.get("/.well-known/apple-app-site-association")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"]?.startsWith(ContentType.Application.Json.toString()) == true)
        assertEquals(
            """{"webcredentials":{"apps":["TEAM123456.com.ohmz.tday.ios"]}}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `root AASA route returns same payload before catch-all fallback`() = testApplication {
        application {
            configureSerialization()
            routing {
                appleAppSiteAssociationRoutes(testAppConfig())
                get("{path...}") {
                    call.respondText("index fallback")
                }
            }
        }

        val response = client.get("/apple-app-site-association")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """{"webcredentials":{"apps":["TEAM123456.com.ohmz.tday.ios"]}}""",
            response.bodyAsText(),
        )
    }
}
