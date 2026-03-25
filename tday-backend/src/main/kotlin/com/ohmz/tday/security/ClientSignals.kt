package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import io.ktor.server.request.ApplicationRequest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ClientSignals {
    private val hashSecret: String by lazy {
        val secret = AppConfig.authSecret
        if (secret.length >= 16) secret
        else {
            System.err.println("[security] auth_secret_missing using fallback hash key")
            java.security.SecureRandom().let { r ->
                ByteArray(32).also { r.nextBytes(it) }.toHex()
            }
        }
    }

    fun getClientIp(request: ApplicationRequest): String {
        request.headers["cf-connecting-ip"]?.trim()?.ifEmpty { null }?.let { return it }

        request.headers["x-forwarded-for"]?.let { header ->
            header.split(",").map { it.trim() }.firstOrNull { it.isNotEmpty() }?.let { return it }
        }

        request.headers["x-real-ip"]?.trim()?.ifEmpty { null }?.let { return it }

        return request.local.remoteAddress
    }

    fun getDeviceHint(request: ApplicationRequest): String? {
        val deviceId = request.headers["x-tday-device-id"]?.trim()?.ifEmpty { null } ?: return null
        return deviceId.take(128)
    }

    fun normalizeIdentifier(value: String?): String? {
        val normalized = value?.trim()?.lowercase()
        return normalized?.ifEmpty { null }
    }

    fun hashSecurityValue(raw: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hashSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(raw.toByteArray(Charsets.UTF_8)).toHex()
    }
}
