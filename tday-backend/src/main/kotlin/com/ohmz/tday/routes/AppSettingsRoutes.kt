package com.ohmz.tday.routes

import com.ohmz.tday.domain.withAuth
import com.ohmz.tday.services.AppConfigService
import io.ktor.server.routing.*
import com.ohmz.tday.di.inject

fun Route.appSettingsRoutes() {
    val appConfigService by inject<AppConfigService>()

    route("/app-settings") {
        get {
            call.withAuth { _ ->
                appConfigService.getGlobalConfig().map { mapOf("aiSummaryEnabled" to it.aiSummaryEnabled) }
            }
        }
    }
}
