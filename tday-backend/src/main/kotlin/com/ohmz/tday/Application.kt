package com.ohmz.tday

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.config.DatabaseConfig
import com.ohmz.tday.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.ohmz.tday.Application")

fun main() {
    logger.info("Starting Tday backend on port ${AppConfig.port}")
    embeddedServer(Netty, port = AppConfig.port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    DatabaseConfig.init()
    configureSerialization()
    configureSecurityHeaders()
    configureStatusPages()
    configureSecurity()
    configureRouting()
    logger.info("Tday backend started successfully")
}
