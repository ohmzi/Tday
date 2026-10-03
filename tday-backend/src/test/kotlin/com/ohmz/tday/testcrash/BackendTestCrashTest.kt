package com.ohmz.tday.testcrash

import com.ohmz.tday.observability.FingerprintedFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The one thing the cross-client search depends on: a server report names its trigger the way the
 * mobile and web reports do, so `TEST-CRASH` finds every platform and the id finds one screen.
 */
/** `TEST-CRASH <id>: <what>`, the shape every platform writes. */
private val MESSAGE_SHAPE = Regex("^TEST-CRASH TC-BACKEND-[A-Z]+: \\S.*$")

class BackendTestCrashTest {
    @Test
    fun `every trigger names itself the way the other clients do`() {
        BackendTestCrash.entries.forEach { trigger ->
            assertEquals("TEST-CRASH ${trigger.id}: ${trigger.what}", trigger.message)
            assertTrue(
                trigger.message.matches(MESSAGE_SHAPE),
                "unexpected message shape: ${trigger.message}",
            )
        }
    }

    @Test
    fun `no two triggers share an id`() {
        val ids = BackendTestCrash.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate trigger id in $ids")
    }

    /**
     * Both triggers fail on the same route. Sentry groups by type and stack, so without a fingerprint
     * per trigger the second one would disappear into the first one's issue — which is exactly what
     * the deployed run showed before this.
     */
    @Test
    fun `each trigger asks for an issue of its own`() {
        val fingerprints = BackendTestCrash.entries.map { it.issueFingerprint }
        BackendTestCrash.entries.forEach { trigger ->
            assertEquals(listOf("test-crash", trigger.id), trigger.issueFingerprint)
        }
        assertEquals(
            fingerprints.size,
            fingerprints.toSet().size,
            "two triggers share a fingerprint: $fingerprints",
        )
    }

    /** The failure has to stay the kind of exception the ordinary unhandled path already reports. */
    @Test
    fun `the failure travels the unhandled route and carries its trigger's fingerprint`() {
        BackendTestCrash.entries.forEach { trigger ->
            val failure = TestCrashFailure(trigger)
            assertIs<IllegalStateException>(failure)
            assertEquals(trigger.message, failure.message)
            val fingerprinted = assertIs<FingerprintedFailure>(failure)
            assertEquals(trigger.issueFingerprint, fingerprinted.issueFingerprint)
        }
    }
}
