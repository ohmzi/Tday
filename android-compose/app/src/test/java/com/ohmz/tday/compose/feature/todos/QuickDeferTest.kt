package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QuickDeferTest {

    private val zone = ZoneId.of("America/Toronto")

    @Test
    fun `computes the four instants from a morning reference`() {
        // Wednesday 2026-07-08 10:15 local.
        val now = ZonedDateTime.of(2026, 7, 8, 10, 15, 0, 0, zone)
        val options = quickDeferOptions(now).associate { it.choice to it.dueEpochMs }

        assertEquals(
            ZonedDateTime.of(2026, 7, 8, 13, 15, 0, 0, zone).toInstant().toEpochMilli(),
            options.getValue(QuickDeferChoice.LATER_TODAY),
        )
        assertEquals(
            ZonedDateTime.of(2026, 7, 8, 19, 0, 0, 0, zone).toInstant().toEpochMilli(),
            options.getValue(QuickDeferChoice.TONIGHT),
        )
        assertEquals(
            ZonedDateTime.of(2026, 7, 9, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            options.getValue(QuickDeferChoice.TOMORROW),
        )
        // Next Monday after a Wednesday is 2026-07-13.
        assertEquals(
            ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            options.getValue(QuickDeferChoice.NEXT_WEEK),
        )
    }

    @Test
    fun `hides Tonight once the evening cutoff has passed`() {
        val evening = ZonedDateTime.of(2026, 7, 8, 19, 45, 0, 0, zone)
        val choices = quickDeferOptions(evening).map { it.choice }
        assertFalse(choices.contains(QuickDeferChoice.TONIGHT))
        assertEquals(
            listOf(
                QuickDeferChoice.LATER_TODAY,
                QuickDeferChoice.TOMORROW,
                QuickDeferChoice.NEXT_WEEK,
            ),
            choices,
        )
    }
}
