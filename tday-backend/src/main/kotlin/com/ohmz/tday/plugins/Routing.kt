package com.ohmz.tday.plugins

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.domain.requireApprovedAuthUser
import com.ohmz.tday.routes.*
import com.ohmz.tday.routes.auth.*
import com.ohmz.tday.services.RealtimeService
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.response.header
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

        appleAppSiteAssociationRoutes(config)

        route("/api") {
            todoRoutes()
            floaterRoutes()
            listRoutes()
            floaterListRoutes()
            listShareRoutes()
            userRoutes()
            preferencesRoutes()
            completedTodoRoutes()
            completedFloaterRoutes()
            timezoneRoutes()
            appSettingsRoutes()
            adminRoutes()
            notificationRoutes()
            mobileProbeRoutes(config)

            route("/auth") {
                csrfRoutes()
                registerRoutes()
                loginChallengeRoutes()
                credentialsKeyRoutes()
                credentialsCallbackRoutes()
                sessionRoutes()
                securityQuestionRoutes()
                logoutRoutes()
            }
        }

        webSocket("/ws") {
            val user = when (val authResult = call.requireApprovedAuthUser()) {
                is arrow.core.Either.Left -> {
                    val reasonText = if (authResult.value is com.ohmz.tday.domain.AppError.Unauthorized) {
                        "Unauthorized"
                    } else {
                        "Pending approval"
                    }
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, reasonText))
                }
                is arrow.core.Either.Right -> authResult.value
            }
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
                        call.response.header(HttpHeaders.CacheControl, cacheControlFor(relPath))
                        call.respondFile(candidate)
                    } else {
                        val index: java.io.File = java.io.File(dir, "index.html")
                        if (index.isFile) {
                            // SPA shell for any unknown route: must always revalidate.
                            call.response.header(HttpHeaders.CacheControl, NO_STORE)
                            call.respondFile(index)
                        }
                    }
                }
            }
        }
    }
}

private const val NO_STORE = "no-cache, no-store, must-revalidate"
private const val IMMUTABLE = "public, max-age=31536000, immutable"
private const val DEFAULT_STATIC = "public, max-age=3600"

/**
 * Cache-Control for a served static file by its relative path.
 * - version.json and the HTML shell: always revalidate (so new builds are picked up).
 * - content-hashed files under the assets dir: immutable for a year (filename changes per build).
 * - everything else (icons, manifest, locales): a modest TTL.
 */
private fun cacheControlFor(relPath: String): String = when {
    relPath == "version.json" -> NO_STORE
    relPath.startsWith("assets/") -> IMMUTABLE
    relPath.isEmpty() || relPath.endsWith(".html") -> NO_STORE
    else -> DEFAULT_STATIC
}
