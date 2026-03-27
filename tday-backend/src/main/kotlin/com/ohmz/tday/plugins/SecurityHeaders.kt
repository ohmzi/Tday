package com.ohmz.tday.plugins

import com.ohmz.tday.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import org.koin.ktor.ext.inject

fun Application.configureSecurityHeaders() {
    val config by inject<AppConfig>()

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        if (config.isProduction) {
            header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload")
        }
    }
}
