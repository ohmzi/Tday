package com.ohmz.tday.plugins

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.routes.*
import com.ohmz.tday.routes.auth.*
import com.ohmz.tday.services.RealtimeService
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.io.File

fun Application.configureRouting() {
    val realtimeService by inject<RealtimeService>()
    val config by inject<AppConfig>()

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        route("/api") {
            todoRoutes()
            listRoutes()
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
                sessionRoutes()
                logoutRoutes()
            }
        }

        webSocket("/ws") {
            val user = call.authUser() ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            realtimeService.channelFor(user.id).collect { event ->
                val json = Json.encodeToString(com.ohmz.tday.domain.DomainEvent.serializer(), event)
                send(io.ktor.websocket.Frame.Text(json))
            }
        }

        val staticDir: String? = System.getenv("STATIC_FILES_DIR")?.trim()?.ifEmpty { null }
        if (staticDir != null) {
            val dir: java.io.File = java.io.File(staticDir).canonicalFile
            if (dir.isDirectory) {
                get("{path...}") {
                    val relPath = call.parameters.getAll("path")?.joinToString("/") ?: ""
                    if (relPath.startsWith("api/") || relPath.startsWith("ws")) return@get

                    val candidate: java.io.File = java.io.File(dir, relPath).canonicalFile
                    if (candidate.isFile && candidate.path.startsWith(dir.path)) {
                        call.respondFile(candidate)
                    } else {
                        val index: java.io.File = java.io.File(dir, "index.html")
                        if (index.isFile) {
                            call.respondFile(index)
                        }
                    }
                }
            }
        }
    }
}
