package com.ohmz.tday.routes

import com.ohmz.tday.plugins.*
import com.ohmz.tday.services.AppConfigService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.appSettingsRoutes() {
    route("/app-settings") {
        get {
            call.requireUser()
            val config = AppConfigService.getGlobalConfig()
            call.respond(HttpStatusCode.OK, mapOf("aiSummaryEnabled" to config.aiSummaryEnabled))
        }
    }
}
