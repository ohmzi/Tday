package com.ohmz.tday.testcrash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one thing the cross-client search depends on: a server report names its trigger the way the
 * mobile and web reports do, so `TEST-CRASH` finds every platform and the id finds one screen.
 */
class BackendTestCrashTest {
    @Test
    fun `every trigger names itself the way the other clients do`() {
        BackendTestCrash.entries.forEach { trigger ->
            assertEquals("TEST-CRASH ${trigger.id}: ${trigger.what}", trigger.message)
            assertTrue(
                trigger.message.matches(Regex("^TEST-CRASH TC-BACKEND-[A-Z]+: \\S.*$")),
                "unexpected message shape: ${trigger.message}",
            )
        }
    }

    @Test
    fun `no two triggers share an id`() {
        val ids = BackendTestCrash.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate trigger id in $ids")
    }
}
