package com.ohmz.tday

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.di.configModule
import com.ohmz.tday.di.securityModule
import com.ohmz.tday.di.serviceModule
import com.ohmz.tday.plugins.SentryRequestPlugin
import com.ohmz.tday.plugins.configureCallLogging
import com.ohmz.tday.plugins.configureCors
import com.ohmz.tday.plugins.configureRateLimiting
import com.ohmz.tday.plugins.configureRouting
import com.ohmz.tday.plugins.configureSecurity
import com.ohmz.tday.plugins.configureSecurityHeaders
import com.ohmz.tday.plugins.configureSerialization
import com.ohmz.tday.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.sentry.Sentry
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("com.ohmz.tday.Application")

fun main() {
    val config = AppConfig.load()

    Sentry.init { options ->
        options.dsn = config.sentryDsn.orEmpty()
        options.environment = if (config.isProduction) "production" else "development"
        options.release = "tday-backend@${config.backendVersion}"
        options.isSendDefaultPii = false
        options.serverName = "tday-backend"
        options.tracesSampleRate = config.sentryTracesSampleRate
        options.setBeforeSend { event, _ ->
            event.user?.ipAddress = null
            event.request?.url = event.request?.url?.let(com.ohmz.tday.observability.TdayObservability::sanitizePath)
            event.request?.queryString = null
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
    warmUpSummaryModel()
    logger.info("Tday backend started successfully")
}

private fun Application.warmUpSummaryModel() {
    val todoSummaryService by inject<com.ohmz.tday.services.TodoSummaryService>()
    launch {
        runCatching { todoSummaryService.warmUp() }
            .onFailure { logger.warn("Summary model warm-up skipped: ${it.message}") }
    }
}

private fun logStartupSecurityWarnings(config: AppConfig) {
    if (!config.isProduction) return

    if (config.captchaSecret.isNullOrBlank()) {
        logger.warn("AUTH_CAPTCHA_SECRET is unset in production; adaptive CAPTCHA will fail closed once triggered")
    }

    if (config.credentialsPrivateKeyPem.isNullOrBlank()) {
        logger.warn("AUTH_CREDENTIALS_PRIVATE_KEY is unset in production; credential envelope encryption will use an ephemeral key")
    }

    if (config.appleTeamId.isNullOrBlank()) {
        logger.warn("APPLE_TEAM_ID is unset in production; iOS webcredentials association will be incomplete")
    }

    if (config.androidSha256CertFingerprints.isEmpty()) {
        logger.warn("ANDROID_SHA256_CERT_FINGERPRINTS is unset in production; Android web credential sharing will be incomplete")
    }
}
