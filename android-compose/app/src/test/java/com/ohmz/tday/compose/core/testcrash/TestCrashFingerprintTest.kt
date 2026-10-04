package com.ohmz.tday.compose.core.testcrash

import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The harness's own grouping, learned on a device: Sentry groups by exception type and stack, so a
 * trigger whose lambda index shifts between builds opened a second issue (`TC-NEW-TASK` had two).
 * Each trigger now carries the same `["test-crash", <ID>]` keys the other clients use.
 */
class TestCrashFingerprintTest {
    private fun event(message: String? = null, vararg values: String?): SentryEvent =
        SentryEvent().apply {
            this.message = message?.let { text -> Message().apply { this.message = text } }
            if (values.isNotEmpty()) {
                exceptions = values.map { value -> SentryException().apply { this.value = value } }
            }
        }

    private fun fingerprint(event: SentryEvent): List<String>? =
        TestCrash.applyTestCrashFingerprint(event).fingerprints

    @Test
    fun `a trigger is fingerprinted by the id in its message`() {
        val scheduled = event(null, "TEST-CRASH TC-FEED-SCHED: scheduled home feed")
        assertEquals(listOf("test-crash", "TC-FEED-SCHED"), fingerprint(scheduled))
    }

    @Test
    fun `the id comes from the marker, not from the exception type`() {
        val done = event("TEST-CRASH TC-BUILTIN-DONE: built-in Completed list")
        val listAny = event(null, "TEST-CRASH TC-LIST-ANY: user anytime list")
        assertEquals(listOf("test-crash", "TC-BUILTIN-DONE"), fingerprint(done))
        assertEquals(listOf("test-crash", "TC-LIST-ANY"), fingerprint(listAny))
    }

    @Test
    fun `every trigger maps to its own fingerprint`() {
        val fingerprints = TestCrashId.entries.map { trigger -> fingerprint(event(trigger.message)) }
        assertEquals("two triggers share a fingerprint: $fingerprints", TestCrashId.entries.size, fingerprints.toSet().size)
        TestCrashId.entries.forEach { trigger ->
            assertEquals(listOf("test-crash", trigger.id), fingerprint(event(trigger.message)))
        }
    }

    @Test
    fun `a real failure is left for Sentry to group`() {
        assertNull(fingerprint(event(null, "java.lang.IllegalStateException: something broke")))
    }
}
