package com.ohmz.tday.routes

import arrow.core.right
import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.TodoSummaryService
import com.ohmz.tday.shared.model.AppSettingsResponse
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.appSettingsRoutes() {
    val todoSummaryService by inject<TodoSummaryService>()

    route("/app-settings") {
        get {
            call.withAuth { _ ->
                AppSettingsResponse(
                    aiSummaryConfigured = todoSummaryService.isConfigured(),
                    aiSummaryHealthy = todoSummaryService.healthyCached(),
                ).right()
            }
        }
    }
}
