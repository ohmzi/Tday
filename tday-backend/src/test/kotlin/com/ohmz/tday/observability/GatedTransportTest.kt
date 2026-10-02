package com.ohmz.tday.observability

import io.sentry.Hint
import io.sentry.ITransportFactory
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GatedTransportTest {
    private val gate = TelemetryGate()
    private val recording = RecordingTransport()
    private val transport = GatedTransport(recording, gate)

    @Test
    fun `drops every envelope while the gate is closed`() {
        transport.send(envelope(), Hint())
        transport.send(envelope())

        assertTrue(recording.sent.isEmpty())
    }

    @Test
    fun `forwards envelopes while the gate is open`() {
        gate.set(true)
        val first = envelope()
        val second = envelope()

        transport.send(first, Hint())
        transport.send(second)

        assertEquals(listOf(first, second), recording.sent)
    }

    @Test
    fun `closing the gate stops forwarding at once and opening it resumes`() {
        gate.set(true)
        transport.send(envelope())
        gate.set(false)
        transport.send(envelope())
        transport.send(envelope(), Hint())
        gate.set(true)
        transport.send(envelope())

        assertEquals(2, recording.sent.size)
    }

    @Test
    fun `a closed gate never reaches the delegate through the one argument send`() {
        // The interface's default send(envelope) calls send(envelope, Hint()) on whatever it
        // runs on. If the wrapper only overrode the abstract method and the SDK later added a
        // default that went around it, the delegate would be reachable while closed.
        transport.send(envelope())

        assertEquals(0, recording.sendCalls)
    }

    @Test
    fun `flush only waits when the gate is open`() {
        transport.flush(100)
        assertEquals(0, recording.flushed.size)

        gate.set(true)
        transport.flush(250)
        assertEquals(listOf(250L), recording.flushed)
    }

    @Test
    fun `close always releases the delegate whatever the gate says`() {
        transport.close(true)
        transport.close(false)
        transport.close()

        assertEquals(listOf(true, false, false), recording.closedWith)
    }

    @Test
    fun `health and the rate limiter come from the delegate`() {
        recording.healthy = false

        assertEquals(false, transport.isHealthy())
        assertSame(recording.limiter, transport.getRateLimiter())
    }

    @Test
    fun `declares every method of ITransport itself`() {
        // Kotlin's `by` delegation would forward the default methods without a gate check, and a
        // method added by a future SDK would slip through unnoticed. Forcing an explicit
        // declaration here turns that into a failing test on the upgrade.
        val declared = GatedTransport::class.java.declaredMethods
            .map { it.name to it.parameterTypes.toList() }
            .toSet()
        val missing = ITransport::class.java.methods
            .filterNot { it.isSynthetic }
            .map { it.name to it.parameterTypes.toList() }
            .filterNot { it in declared }

        assertTrue(missing.isEmpty(), "GatedTransport does not declare: $missing")
    }

    @Test
    fun `the factory wraps whatever transport the delegate factory builds`() {
        val built = mutableListOf<RecordingTransport>()
        val delegateFactory = ITransportFactory { _, _ -> RecordingTransport().also(built::add) }
        val factory = GatedTransportFactory(gate, delegateFactory)

        val created = factory.create(SentryOptions(), RequestDetails("https://o1.ingest.sentry.io/api/1/envelope/", emptyMap()))

        created.send(envelope())
        assertTrue(built.single().sent.isEmpty())

        gate.set(true)
        created.send(envelope())
        assertEquals(1, built.single().sent.size)
    }

    private fun envelope() = SentryEnvelope(SentryEnvelopeHeader(), emptyList())

    private class RecordingTransport : ITransport {
        val sent = mutableListOf<SentryEnvelope>()
        val flushed = mutableListOf<Long>()
        val closedWith = mutableListOf<Boolean>()
        val limiter = RateLimiter(SentryOptions())
        var healthy = true
        var sendCalls = 0

        override fun send(envelope: SentryEnvelope, hint: Hint) {
            sendCalls++
            sent += envelope
        }

        override fun isHealthy() = healthy

        override fun flush(timeoutMillis: Long) {
            flushed += timeoutMillis
        }

        override fun getRateLimiter(): RateLimiter = limiter

        override fun close(isRestarting: Boolean) {
            closedWith += isRestarting
        }

        override fun close() = close(false)
    }
}
