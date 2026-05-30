package com.ohmz.tday.routes

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimezoneRoutesTest {
    @Test
    fun `client timezone resolves native header`() {
        assertEquals(
            "America/Toronto",
            resolveClientTimeZone(
                queryTimeZone = null,
                xTimeZone = null,
                xUserTimeZone = "America/Toronto",
            ),
        )
    }

    @Test
    fun `client timezone prefers query over headers`() {
        assertEquals(
            "UTC",
            resolveClientTimeZone(
                queryTimeZone = "UTC",
                xTimeZone = "America/New_York",
                xUserTimeZone = "America/Toronto",
            ),
        )
    }

    @Test
    fun `timezone validator rejects invalid zone`() {
        assertTrue(isValidTimeZone("America/Toronto"))
        assertFalse(isValidTimeZone("not-a-zone"))
    }
}
