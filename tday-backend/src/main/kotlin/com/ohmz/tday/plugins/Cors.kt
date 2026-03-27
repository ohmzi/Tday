package com.ohmz.tday.plugins

import com.ohmz.tday.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.CORSConfig
import io.ktor.server.plugins.cors.routing.CORS
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.net.URI

private val logger = LoggerFactory.getLogger("com.ohmz.tday.plugins.Cors")

fun Application.configureCors() {
    val config by inject<AppConfig>()

    install(CORS) {
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Origin)
        allowCredentials = true
        allowNonSimpleContentTypes = true

        config.corsAllowedOrigins.forEach { origin ->
            allowConfiguredOrigin(origin)
        }
    }
}

private fun CORSConfig.allowConfiguredOrigin(origin: String) {
    val uri = runCatching { URI(origin) }.getOrNull()
    val scheme = uri?.scheme?.takeIf { it == "http" || it == "https" }
    val host = uri?.host
    if (scheme == null || host.isNullOrBlank()) {
        logger.warn("Skipping invalid CORS origin configuration: {}", origin)
        return
    }

    val hostWithPort = if (uri.port > 0) "$host:${uri.port}" else host
    allowHost(hostWithPort, schemes = listOf(scheme))
}
