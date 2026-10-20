package com.ohmz.tday.observability

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Whether this server may send its own error reports to Sentry.
 *
 * Closed until an admin turns "Server error reports" on (default off), so a deploy that sets
 * `SENTRY_DSN` still sends nothing until someone chooses to. The SDK is initialised once at
 * process start, before the database is up, and is never closed or re-initialised afterwards:
 * every outgoing item is checked against this gate instead (see [BackendSentry]).
 */
class TelemetryGate {
    private val open = AtomicBoolean(false)

    val isOpen: Boolean get() = open.get()

    fun set(enabled: Boolean) {
        open.set(enabled)
    }
}
