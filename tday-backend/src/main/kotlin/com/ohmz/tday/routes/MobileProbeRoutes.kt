package com.ohmz.tday.routes

import com.ohmz.tday.shared.model.MobileProbeResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.mobileProbeRoutes() {
    route("/mobile/probe") {
        get {
            call.respond(
                HttpStatusCode.OK,
                MobileProbeResponse(
                    service = "tday",
                    probe = "ok",
                    version = "1",
                    serverTime = Instant.now().toString(),
                ),
            )
        }
    }
}
