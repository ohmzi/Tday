package com.ohmz.tday.routes

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.security.ProbeEncryption
import com.ohmz.tday.shared.model.MobileProbeResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.mobileProbeRoutes(config: AppConfig) {
    val probeEncryption = config.probeEncryptionKey?.let { key ->
        runCatching { ProbeEncryption(key) }.getOrNull()
    }

    route("/mobile/probe") {
        get {
            val encrypted = if (probeEncryption != null && config.probeAppVersion != null) {
                val payload = """{"appVersion":"${config.probeAppVersion}","updateRequired":${config.probeUpdateRequired}}"""
                runCatching { probeEncryption.encrypt(payload) }.getOrNull()
            } else {
                null
            }

            call.respond(
                HttpStatusCode.OK,
                MobileProbeResponse(
                    service = "tday",
                    probe = "ok",
                    version = "1",
                    serverTime = Instant.now().toString(),
                    encryptedCompatibility = encrypted,
                ),
            )
        }
    }
}
