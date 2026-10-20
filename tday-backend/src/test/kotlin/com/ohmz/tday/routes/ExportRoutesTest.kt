package com.ohmz.tday.routes

import arrow.core.Either
import arrow.core.right
import com.ohmz.tday.domain.AppError
import com.ohmz.tday.plugins.AuthUserKey
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.security.JwtUserClaims
import com.ohmz.tday.services.ExportService
import com.ohmz.tday.shared.model.ExportedTodoDto
import com.ohmz.tday.shared.model.ImportCounts
import com.ohmz.tday.shared.model.ImportRequest
import com.ohmz.tday.shared.model.ImportResponse
import com.ohmz.tday.shared.model.TdayExport
import com.ohmz.tday.shared.model.TodoDto
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `export returns the callers bundle`() = testApplication {
        val service = FakeExportService()
        application { configureExportTestApp(service) }

        val response = client.get("/api/export")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["todos"]!!.jsonArray.size)
        assertEquals("user_123", service.lastExportUserId)
    }

    @Test
    fun `import dry run previews counts without writing`() = testApplication {
        val service = FakeExportService()
        application { configureExportTestApp(service) }

        val response = client.post("/api/import") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"export":{"schemaVersion":1,"floaters":[{"id":"f1","title":"x"}]},"dryRun":true}""",
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["dryRun"]!!.jsonPrimitive.boolean)
        assertEquals(1, body["imported"]!!.jsonObject["floaters"]!!.jsonPrimitive.int)
        assertTrue(service.lastImportRequest!!.dryRun)
        assertEquals(1, service.lastImportRequest!!.export.floaters.size)
    }

    @Test
    fun `import forwards the bundle to the service for a real run`() = testApplication {
        val service = FakeExportService()
        application { configureExportTestApp(service) }

        val response = client.post("/api/import") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"export":{"schemaVersion":1,"floaters":[{"id":"f1","title":"a"},{"id":"f2","title":"b"}]},"dryRun":false}""",
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertFalse(body["dryRun"]!!.jsonPrimitive.boolean)
        assertEquals("user_123", service.lastImportUserId)
        assertEquals(2, service.lastImportRequest!!.export.floaters.size)
    }

    private fun Application.configureExportTestApp(exportService: ExportService) {
        install(Koin) {
            modules(
                module {
                    single<ExportService> { exportService }
                },
            )
        }
        configureSerialization()
        intercept(ApplicationCallPipeline.Plugins) {
            if (call.attributes.getOrNull(AuthUserKey) == null) {
                call.attributes.put(
                    AuthUserKey,
                    JwtUserClaims(
                        id = "user_123",
                        name = "Test User",
                        username = "testuser",
                        role = "ADMIN",
                        approvalStatus = "APPROVED",
                        timeZone = "UTC",
                    ),
                )
            }
        }
        routing {
            route("/api") {
                exportRoutes()
            }
        }
    }

    private class FakeExportService : ExportService {
        var lastExportUserId: String? = null
        var lastImportUserId: String? = null
        var lastImportRequest: ImportRequest? = null

        override suspend fun exportAll(userId: String): Either<AppError, TdayExport> {
            lastExportUserId = userId
            return TdayExport(
                todos = listOf(
                    ExportedTodoDto(todo = TodoDto(id = "t1", due = "2026-01-01T00:00:00")),
                ),
            ).right()
        }

        override suspend fun import(userId: String, request: ImportRequest): Either<AppError, ImportResponse> {
            lastImportUserId = userId
            lastImportRequest = request
            return ImportResponse(
                dryRun = request.dryRun,
                imported = ImportCounts(floaters = request.export.floaters.size),
            ).right()
        }
    }
}
