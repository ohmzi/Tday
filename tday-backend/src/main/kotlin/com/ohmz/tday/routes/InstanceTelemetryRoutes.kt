package com.ohmz.tday.routes

import com.ohmz.tday.services.InstanceSettingsService
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * The instance-wide crash-report answer, for a browser that has to decide whether to start the
 * Sentry SDK before it has a session.
 *
 * Public on purpose: it is one answer the admin gives for everyone, and it carries no DSN state and
 * no personal data. The service fails closed, so an unreadable setting answers "off".
 */
fun Route.instanceTelemetryRoutes() {
    val instanceSettingsService by inject<InstanceSettingsService>()

    route("/instance/telemetry") {
        get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header(HttpHeaders.Pragma, "no-cache")
            call.respond(instanceSettingsService.instanceTelemetry())
        }
    }
}
