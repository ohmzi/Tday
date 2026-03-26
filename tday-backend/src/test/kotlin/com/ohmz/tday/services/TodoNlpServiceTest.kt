package com.ohmz.tday.services

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoNlpServiceTest {
    private val service = TodoNlpServiceImpl()

    @Test
    fun `blank text returns empty parse result`() {
        val result = service.parse("   ")

        assertEquals("", result.cleanTitle)
        assertNull(result.matchedText)
        assertNull(result.startEpochMs)
        assertNull(result.dueEpochMs)
    }

    @Test
    fun `parser extracts due range from natural language text`() {
        val result = service.parse(
            text = "Plan launch tomorrow at 9am",
            referenceEpochMs = 1_711_000_000_000,
            timezoneOffsetMinutes = -240,
            defaultDurationMinutes = 90,
        )

        assertTrue(result.cleanTitle.contains("Plan launch"))
        assertNotNull(result.matchedText)
        assertNotNull(result.startEpochMs)
        assertNotNull(result.dueEpochMs)
        assertTrue(result.dueEpochMs > result.startEpochMs)
    }

    @Test
    fun `invalid default duration falls back to service default`() {
        val result = service.parse(
            text = "Review backlog Friday 3pm",
            referenceEpochMs = 1_711_000_000_000,
            defaultDurationMinutes = -25,
        )

        assertNotNull(result.startEpochMs)
        assertNotNull(result.dueEpochMs)
        assertEquals(180 * 60_000L, result.dueEpochMs!! - result.startEpochMs!!)
    }
}
