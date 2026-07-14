package com.ohmz.tday.services

import com.ohmz.tday.security.toHex
import kotlinx.coroutines.delay
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Outcome of a (possibly retried) webhook delivery attempt. */
data class WebhookDeliveryResult(
    val delivered: Boolean,
    val lastStatus: Int?,
    val attempts: Int,
)

/** HMAC-SHA256 hex signature of a payload under a subscription secret. */
fun webhookSignature(secret: String, body: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(body.toByteArray(Charsets.UTF_8)).toHex()
}

/**
 * Retry-with-exponential-backoff driver, decoupled from HTTP and the DB so the retry
 * behaviour is unit-testable. [send] returns the HTTP status (or null when the request
 * threw); a 2xx stops early. Backoff is applied *before* each retry (not the first try).
 */
suspend fun deliverWithRetry(
    maxAttempts: Int,
    baseBackoffMs: Long,
    send: suspend (attempt: Int) -> Int?,
): WebhookDeliveryResult {
    var lastStatus: Int? = null
    var attempts = 0
    for (attempt in 0 until maxAttempts) {
        if (attempt > 0 && baseBackoffMs > 0) delay(baseBackoffMs shl (attempt - 1))
        attempts = attempt + 1
        val status = send(attempt)
        lastStatus = status
        if (status != null && status in 200..299) {
            return WebhookDeliveryResult(delivered = true, lastStatus = status, attempts = attempts)
        }
    }
    return WebhookDeliveryResult(delivered = false, lastStatus = lastStatus, attempts = attempts)
}
