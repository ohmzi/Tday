package com.ohmz.tday

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.di.configModule
import com.ohmz.tday.di.securityModule
import com.ohmz.tday.di.serviceModule
import com.ohmz.tday.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.sentry.Sentry
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("com.ohmz.tday.Application")

fun main() {
    val config = AppConfig.load()

    Sentry.init { options ->
        options.dsn = config.sentryDsn ?: ""
        options.environment = if (config.isProduction) "production" else "development"
        options.release = "tday-backend@${System.getenv("TDAY_BACKEND_VERSION") ?: "0.0.1"}"
        options.isSendDefaultPii = false
        options.serverName = "tday-backend"
        options.tracesSampleRate = 1.0
        options.setBeforeSend { event, _ ->
            event.user?.ipAddress = null
            event
        }
    }

    logger.info("Starting Tday backend on port ${config.port}")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.load()) {
    install(Koin) {
        slf4jLogger()
        modules(configModule(config), securityModule, serviceModule)
    }

    val dbConfig by inject<DatabaseConfig>()
    dbConfig.init()

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 60.seconds
        maxFrameSize = 64 * 1024L
        masking = false
    }

    install(SentryRequestPlugin)

    configureCallLogging()
    configureSerialization()
    configureCors()
    configureSecurityHeaders()
    configureStatusPages()
    configureSecurity()
    logStartupSecurityWarnings(config)
    configureRateLimiting()
    configureRouting()
    logger.info("Tday backend started successfully")
}

private fun logStartupSecurityWarnings(config: AppConfig) {
    if (!config.isProduction) return

    if (config.captchaSecret.isNullOrBlank()) {
        logger.warn("AUTH_CAPTCHA_SECRET is unset in production; adaptive CAPTCHA will fail closed once triggered")
    }

    if (config.credentialsPrivateKeyPem.isNullOrBlank()) {
        logger.warn("AUTH_CREDENTIALS_PRIVATE_KEY is unset in production; credential envelope encryption will use an ephemeral key")
    }
}
