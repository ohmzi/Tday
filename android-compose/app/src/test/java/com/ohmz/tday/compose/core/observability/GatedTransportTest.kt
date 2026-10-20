package com.ohmz.tday.compose.core.observability

import io.mockk.mockk
import io.sentry.Hint
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.hints.DiskFlushNotification
import io.sentry.protocol.SentryId
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import io.sentry.util.HintUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is the layer that holds even if the SDK is still running when the user says no, so
 * what is pinned here is every way an envelope can reach the wrapped transport: both `send`
 * overloads, and `flush`. Kotlin `by` delegation would have forwarded the default
 * `send(envelope)` straight to the delegate, which is why none of it is delegated implicitly.
 */
class GatedTransportTest {
    private val gate = TelemetryGate()
    private val recorder = RecordingTransport()
    private val transport: ITransport = GatedTransport(gate, recorder)
    private val envelope = SentryEnvelope(SentryId.EMPTY_ID, null, emptyList())

    @Test
    fun `a closed gate lets nothing through either send overload`() {
        transport.send(envelope, Hint())
        transport.send(envelope)

        assertEquals(0, recorder.sends)
    }

    @Test
    fun `a closed gate does not flush`() {
        transport.flush(5_000L)

        assertEquals(0, recorder.flushes)
    }

    @Test
    fun `an open gate forwards sends and flushes`() {
        gate.open()

        transport.send(envelope, Hint())
        transport.send(envelope)
        transport.flush(5_000L)

        assertEquals(2, recorder.sends)
        assertEquals(1, recorder.flushes)
    }

    @Test
    fun `the gate is read on every call, so shutting it mid-run takes effect at once`() {
        gate.open()
        transport.send(envelope, Hint())

        gate.close()
        transport.send(envelope, Hint())
        transport.send(envelope)

        assertEquals(1, recorder.sends)
    }

    @Test
    fun `a dropped crash report does not leave the dying thread waiting for a disk flush`() {
        val hint = Hint()
        val flushable = RecordingFlushHint()
        HintUtils.setTypeCheckHint(hint, flushable)

        transport.send(envelope, hint)

        assertTrue(flushable.flushed)
    }

    @Test
    fun `close and the rate limiter always reach the wrapped transport`() {
        transport.close()
        transport.close(true)

        assertEquals(listOf(false, true), recorder.closes)
        assertSame(recorder.limiter, transport.rateLimiter)
        assertTrue(transport.isHealthy)
    }

    @Test
    fun `the factory wraps whatever transport the delegate factory makes`() {
        val created = GatedTransportFactory(gate) { _, _ -> recorder }
            .create(SentryOptions(), RequestDetails("https://example.invalid", emptyMap()))

        assertTrue(created is GatedTransport)
        created.send(envelope)
        assertEquals(0, recorder.sends)
        gate.open()
        created.send(envelope)
        assertEquals(1, recorder.sends)
    }

    private class RecordingFlushHint : DiskFlushNotification {
        var flushed = false

        override fun markFlushed() {
            flushed = true
        }

        override fun isFlushable(eventId: SentryId?): Boolean = true

        override fun setFlushable(eventId: SentryId) = Unit
    }

    private class RecordingTransport : ITransport {
        var sends = 0
        var flushes = 0
        val closes = mutableListOf<Boolean>()
        val limiter: RateLimiter = mockk(relaxed = true)

        override fun send(envelope: SentryEnvelope, hint: Hint) {
            sends++
        }

        override fun flush(timeoutMillis: Long) {
            flushes++
        }

        override fun getRateLimiter(): RateLimiter? = limiter

        override fun close(isRestarting: Boolean) {
            closes += isRestarting
        }

        override fun close() {
            closes += false
        }
    }
}
