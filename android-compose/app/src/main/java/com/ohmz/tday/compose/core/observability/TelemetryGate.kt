package com.ohmz.tday.compose.core.observability

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The in-memory "reports may leave this device right now" switch.
 *
 * It is the part of opt-out that works even while the SDK is still running: saying no shuts it
 * first, so nothing the SDK does in the seconds before `Sentry.close()` has finished can reach the
 * network, whatever it had queued. Closed until the bootstrap opens it, which is why a process
 * that never got consent can never send.
 */
class TelemetryGate {
    private val opened = AtomicBoolean(false)

    val isOpen: Boolean get() = opened.get()

    fun open() = opened.set(true)

    fun close() = opened.set(false)
}
