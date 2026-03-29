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
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("com.ohmz.tday.Application")

fun main() {
    val config = AppConfig.load()
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

    configureCallLogging()
    configureSerialization()
    configureCors()
    configureSecurityHeaders()
    configureStatusPages()
    configureSecurity()
    configureRouting()
    logger.info("Tday backend started successfully")
}
