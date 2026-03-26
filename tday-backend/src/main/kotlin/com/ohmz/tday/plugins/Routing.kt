package com.ohmz.tday.plugins

import com.ohmz.tday.routes.*
import com.ohmz.tday.routes.auth.*
import com.ohmz.tday.services.RealtimeService
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val realtimeService by inject<RealtimeService>()

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api") {
            todoRoutes()
            listRoutes()
            noteRoutes()
            userRoutes()
            preferencesRoutes()
            completedTodoRoutes()
            timezoneRoutes()
            appSettingsRoutes()
            adminRoutes()
            mobileProbeRoutes()

            route("/auth") {
                csrfRoutes()
                registerRoutes()
                loginChallengeRoutes()
                credentialsKeyRoutes()
                credentialsCallbackRoutes()
            }
        }

        webSocket("/ws") {
            val user = call.authUser() ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            realtimeService.channelFor(user.id).collect { event ->
                val json = Json.encodeToString(com.ohmz.tday.domain.DomainEvent.serializer(), event)
                send(io.ktor.websocket.Frame.Text(json))
            }
        }
    }
}
