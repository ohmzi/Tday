package com.ohmz.tday.routes

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimezoneRoutesTest {
    @Test
    fun `client timezone resolves native header`() {
        assertEquals(
            "America/New_York",
            resolveClientTimeZone(
                queryTimeZone = null,
                xTimeZone = null,
                xUserTimeZone = "America/New_York",
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
                xUserTimeZone = "America/New_York",
            ),
        )
    }

    @Test
    fun `timezone validator rejects invalid zone`() {
        assertTrue(isValidTimeZone("America/New_York"))
        assertFalse(isValidTimeZone("not-a-zone"))
    }
}
