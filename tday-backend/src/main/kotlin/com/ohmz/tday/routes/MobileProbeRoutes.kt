package com.ohmz.tday.routes

import com.ohmz.tday.security.SecurityEventLogger
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.mobileProbeRoutes() {
    route("/mobile/probe") {
        get {
            try {
                call.respond(HttpStatusCode.OK, mapOf(
                    "service" to "tday",
                    "probe" to "ok",
                    "version" to "1",
                    "serverTime" to Instant.now().toString(),
                ))
            } catch (e: Exception) {
                SecurityEventLogger.log("probe_failed_contract", mapOf("error" to e.message))
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Probe unavailable"))
            }
        }
    }
}
