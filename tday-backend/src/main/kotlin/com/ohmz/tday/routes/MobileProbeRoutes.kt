package com.ohmz.tday.routes

import com.ohmz.tday.security.SecurityEventLogger
import com.ohmz.tday.shared.model.MobileProbeResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.Instant

fun Route.mobileProbeRoutes() {
    val eventLogger by inject<SecurityEventLogger>()

    route("/mobile/probe") {
        get {
            try {
                call.respond(
                    HttpStatusCode.OK,
                    MobileProbeResponse(
                        service = "tday",
                        probe = "ok",
                        version = "1",
                        serverTime = Instant.now().toString(),
                    ),
                )
            } catch (e: Exception) {
                eventLogger.log("probe_failed_contract", mapOf("error" to e.message))
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Probe unavailable"))
            }
        }
    }
}
