package com.ohmz.tday.shared.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietHoursTest {
    private fun min(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun sameDayWindow() {
        // 13:00–14:00
        assertTrue(QuietHours.contains(min(13, 30), min(13), min(14)))
        assertFalse(QuietHours.contains(min(14), min(13), min(14))) // end exclusive
        assertFalse(QuietHours.contains(min(12, 59), min(13), min(14)))
        assertTrue(QuietHours.contains(min(13), min(13), min(14))) // start inclusive
    }

    @Test
    fun midnightSpanningWindow() {
        // 22:00–07:00
        val start = min(22)
        val end = min(7)
        assertTrue(QuietHours.contains(min(23), start, end))
        assertTrue(QuietHours.contains(min(2), start, end))
        assertTrue(QuietHours.contains(min(6, 59), start, end))
        assertFalse(QuietHours.contains(min(7), start, end)) // end exclusive
        assertFalse(QuietHours.contains(min(12), start, end))
        assertTrue(QuietHours.contains(min(22), start, end)) // start inclusive
    }

    @Test
    fun emptyWindowWhenStartEqualsEnd() {
        assertFalse(QuietHours.contains(min(22), min(9), min(9)))
    }

    @Test
    fun shiftToWindowEndSameDay() {
        // Inside 13:00–14:00 at 13:20 → 40 minutes until 14:00.
        assertEquals(40, QuietHours.minutesUntilWindowEnd(min(13, 20), min(13), min(14)))
        // Outside → 0.
        assertEquals(0, QuietHours.minutesUntilWindowEnd(min(15), min(13), min(14)))
    }

    @Test
    fun shiftToWindowEndAcrossMidnight() {
        // 22:00–07:00, reminder at 23:00 → 8h to 07:00.
        assertEquals(8 * 60, QuietHours.minutesUntilWindowEnd(min(23), min(22), min(7)))
        // At 06:00 → 1h to 07:00.
        assertEquals(60, QuietHours.minutesUntilWindowEnd(min(6), min(22), min(7)))
    }
}
