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

    @Test
    fun `assetlinks route returns Android credential sharing payload`() = testApplication {
        application {
            configureSerialization()
            routing {
                appleAppSiteAssociationRoutes(
                    testAppConfig(
                        androidPackageName = "com.ohmz.tday.compose",
                        androidSha256CertFingerprints = listOf("AA:BB", "CC:DD"),
                    ),
                )
            }
        }

        val response = client.get("/.well-known/assetlinks.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"]?.startsWith(ContentType.Application.Json.toString()) == true)
        assertEquals(
            """[{"relation":["delegate_permission/common.handle_all_urls","delegate_permission/common.get_login_creds"],"target":{"namespace":"android_app","package_name":"com.ohmz.tday.compose","sha256_cert_fingerprints":["AA:BB","CC:DD"]}}]""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `assetlinks route returns empty array when fingerprints are unset`() = testApplication {
        application {
            configureSerialization()
            routing {
                appleAppSiteAssociationRoutes(
                    testAppConfig(androidSha256CertFingerprints = emptyList()),
                )
            }
        }

        val response = client.get("/.well-known/assetlinks.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }
}
