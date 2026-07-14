package com.ohmz.tday.routes

import com.ohmz.tday.di.inject
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.ExportService
import com.ohmz.tday.shared.model.ImportRequest
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Portable data export/import. Both endpoints are owner-scoped through
 * `withAuth`; the bundle carries only the caller's own rows.
 *
 * - `GET /api/export` streams the full [com.ohmz.tday.shared.model.TdayExport].
 * - `POST /api/import` restores/merges a bundle; `dryRun: true` returns the
 *   counts that would be written without touching the database.
 */
fun Route.exportRoutes() {
    val exportService by inject<ExportService>()

    get("/export") {
        call.withAuth { user ->
            exportService.exportAll(user.id)
        }
    }

    post("/import") {
        call.withAuth { user ->
            val request = call.receive<ImportRequest>()
            exportService.import(user.id, request)
        }
    }
}
