package com.ohmz.tday.shared.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimezoneUtilsTest {
    @Test
    fun `known IANA timezone is valid`() {
        assertTrue(TimezoneUtils.isValidIanaTimeZone("America/Toronto"))
    }

    @Test
    fun `unknown timezone falls back`() {
        assertFalse(TimezoneUtils.isValidIanaTimeZone("Mars/Olympus"))
        assertEquals("UTC", TimezoneUtils.resolveTimeZone("Mars/Olympus"))
    }
}
