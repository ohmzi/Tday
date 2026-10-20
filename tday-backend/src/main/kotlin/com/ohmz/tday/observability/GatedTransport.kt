package com.ohmz.tday.observability

import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter

/**
 * Builds the SDK's real transport and wraps it so that nothing leaves the process while the
 * [gate] is closed.
 *
 * This is the transport-level backstop behind the `beforeSend` hooks: logs, check-ins and any
 * other envelope that never passes through them still stop here.
 *
 * The SDK's own `transportGate` option is the wrong tool. It is a connectivity check, and when it
 * reports "not connected" the SDK caches envelopes and sends them later. A closed gate has to
 * mean the report is gone, not postponed. Hence a wrapper that drops.
 */
class GatedTransportFactory(
    private val gate: TelemetryGate,
    private val delegate: ITransportFactory,
) : ITransportFactory {
    override fun create(options: SentryOptions, requestDetails: RequestDetails): ITransport =
        GatedTransport(delegate.create(options, requestDetails), gate)
}

/**
 * Every method of [ITransport] is spelled out on purpose. Kotlin's `by` delegation would forward
 * the interface's default `send(envelope)` straight to the delegate, around the gate, and would
 * do the same for any default method a later SDK adds. A test pins that this class declares all
 * of them.
 */
class GatedTransport(
    private val delegate: ITransport,
    private val gate: TelemetryGate,
) : ITransport {
    override fun send(envelope: SentryEnvelope, hint: Hint) {
        if (gate.isOpen) delegate.send(envelope, hint)
    }

    override fun send(envelope: SentryEnvelope) {
        if (gate.isOpen) delegate.send(envelope)
    }

    override fun isHealthy(): Boolean = delegate.isHealthy

    /** Nothing is queued while closed, so there is nothing to wait for. */
    override fun flush(timeoutMillis: Long) {
        if (gate.isOpen) delegate.flush(timeoutMillis)
    }

    override fun getRateLimiter(): RateLimiter? = delegate.rateLimiter

    /** Always released, whatever the gate says: this frees the sender thread at shutdown. */
    override fun close(isRestarting: Boolean) {
        delegate.close(isRestarting)
    }

    override fun close() {
        delegate.close()
    }
}
