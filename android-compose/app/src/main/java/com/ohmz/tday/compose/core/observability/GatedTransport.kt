package com.ohmz.tday.compose.core.observability

import io.sentry.AsyncHttpTransportFactory
import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.hints.DiskFlushNotification
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import io.sentry.util.HintUtils

/**
 * Wraps the SDK's own HTTP transport so an envelope only reaches it while [gate] is open.
 *
 * Built on `AsyncHttpTransportFactory`, which Sentry marks `@ApiStatus.Internal`. That is the
 * price of gating at the last point before the network; `GatedTransportTest` fails if a 8.x bump
 * changes the shape, and the fallback is `beforeSend` alone.
 */
class GatedTransportFactory(
    private val gate: TelemetryGate,
    private val delegate: ITransportFactory = AsyncHttpTransportFactory(),
) : ITransportFactory {
    override fun create(options: SentryOptions, requestDetails: RequestDetails): ITransport =
        GatedTransport(gate, delegate.create(options, requestDetails))
}

/**
 * Every [ITransport] method is written out here on purpose. Kotlin's `by` delegation would also
 * forward the interface's default `send(envelope)` straight to the delegate, a second way in that
 * never looks at the gate.
 */
internal class GatedTransport(
    private val gate: TelemetryGate,
    private val delegate: ITransport,
) : ITransport {

    override fun send(envelope: SentryEnvelope, hint: Hint) {
        if (gate.isOpen) {
            delegate.send(envelope, hint)
        } else {
            release(hint)
        }
    }

    override fun send(envelope: SentryEnvelope) = send(envelope, Hint())

    override fun isHealthy(): Boolean = delegate.isHealthy

    override fun flush(timeoutMillis: Long) {
        if (gate.isOpen) delegate.flush(timeoutMillis)
    }

    override fun getRateLimiter(): RateLimiter? = delegate.rateLimiter

    override fun close() = delegate.close()

    override fun close(isRestarting: Boolean) = delegate.close(isRestarting)

    /**
     * A crash report is sent with a hint the dying thread blocks on, for up to the flush timeout,
     * until the transport says it has written the report to disk. Dropping the envelope without
     * saying so would turn a closed gate into a 15 second freeze on top of the crash.
     */
    private fun release(hint: Hint) {
        HintUtils.runIfHasType(hint, DiskFlushNotification::class.java) { it.markFlushed() }
    }
}
