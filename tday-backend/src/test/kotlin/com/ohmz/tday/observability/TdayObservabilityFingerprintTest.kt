package com.ohmz.tday.observability

import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.RateLimiter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the cross-check needs from the helper: the keys a failure declares reach the event Sentry
 * groups by. Without them two failures on one route share an issue and the second is invisible.
 *
 * The SDK is pointed at an in-memory transport, so this needs no network, no DSN and no database.
 */
/** The repeated values the cases below share: one failure, and the two operation labels. */
private const val FAILURE_MESSAGE = "boom"
private const val TEST_CRASH_OPERATION = "test_crash"
private const val UNHANDLED_OPERATION = "api.unhandled"

class TdayObservabilityFingerprintTest {
    private val captured = mutableListOf<SentryEvent>()

    private fun startSentry() {
        val serializer = SentryOptions().serializer
        val options = SentryOptions().apply {
            dsn = "https://public@example.invalid/1"
            setTransportFactory(
                ITransportFactory { _, _ ->
                    object : ITransport {
                        override fun send(envelope: SentryEnvelope, hint: Hint) {
                            envelope.items.forEach { item ->
                                item.getEvent(serializer)?.let { captured += it }
                            }
                        }

                        override fun flush(timeoutMillis: Long) = Unit

                        override fun getRateLimiter(): RateLimiter? = null

                        override fun close(graceful: Boolean) = Unit

                        override fun close() = Unit
                    }
                },
            )
        }
        Sentry.init(options)
    }

    @AfterTest
    fun closeSentry() {
        Sentry.close()
    }

    @Test
    fun `the keys a failure declares reach the event`() {
        startSentry()
        TdayObservability.captureException(
            IllegalStateException(FAILURE_MESSAGE),
            operation = TEST_CRASH_OPERATION,
            fingerprint = listOf("test-crash", "TC-BACKEND-CRASH"),
        )
        Sentry.flush(2_000)

        assertEquals(1, captured.size, "expected exactly one captured event")
        assertEquals(listOf("test-crash", "TC-BACKEND-CRASH"), captured.single().fingerprints)
    }

    @Test
    fun `a failure without keys is left for Sentry to group`() {
        startSentry()
        TdayObservability.captureException(IllegalStateException(FAILURE_MESSAGE), operation = UNHANDLED_OPERATION)
        Sentry.flush(2_000)

        assertEquals(1, captured.size)
        assertTrue(captured.single().fingerprints.isNullOrEmpty(), "no fingerprint expected")
    }

    @Test
    fun `the operation travels as a tag, as every other report does`() {
        startSentry()
        TdayObservability.captureException(IllegalStateException(FAILURE_MESSAGE), operation = TEST_CRASH_OPERATION)
        Sentry.flush(2_000)

        val event = captured.single()
        assertEquals(TEST_CRASH_OPERATION, event.getTag("tday.operation"))
        val exception = event.exceptions?.firstOrNull()
        assertEquals("IllegalStateException", exception?.type)
        assertEquals(FAILURE_MESSAGE, exception?.value)
    }

    /** The helper must not put user text on the event on the way through. */
    @Test
    fun `sensitive data keys are redacted before they are attached`() {
        startSentry()
        TdayObservability.captureException(
            IllegalStateException(FAILURE_MESSAGE),
            operation = UNHANDLED_OPERATION,
            data = mapOf("token" to "secret-value", "route" to "/api/list"),
        )
        Sentry.flush(2_000)

        val extras = captured.single().extras
        assertEquals("redacted", extras?.get("token"))
        assertEquals("/api/list", extras?.get("route"))
    }
}
