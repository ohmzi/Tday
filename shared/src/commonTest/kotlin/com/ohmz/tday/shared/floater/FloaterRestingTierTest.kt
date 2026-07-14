package com.ohmz.tday.shared.floater

import kotlin.test.Test
import kotlin.test.assertEquals

class FloaterRestingTierTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test
    fun freshIsActive() {
        assertEquals(FloaterRestingTier.ACTIVE, FloaterResting.tierFor(now - 5 * day, now))
        assertEquals(FloaterRestingTier.ACTIVE, FloaterResting.tierFor(now, now))
    }

    @Test
    fun thirtyDaysFades() {
        assertEquals(FloaterRestingTier.ACTIVE, FloaterResting.tierFor(now - 29 * day, now))
        assertEquals(FloaterRestingTier.FADING, FloaterResting.tierFor(now - 30 * day, now))
        assertEquals(FloaterRestingTier.FADING, FloaterResting.tierFor(now - 89 * day, now))
    }

    @Test
    fun ninetyDaysRests() {
        assertEquals(FloaterRestingTier.RESTING, FloaterResting.tierFor(now - 90 * day, now))
        assertEquals(FloaterRestingTier.RESTING, FloaterResting.tierFor(now - 400 * day, now))
    }

    @Test
    fun missingTimestampIsActive() {
        assertEquals(FloaterRestingTier.ACTIVE, FloaterResting.tierFor(null, now))
        assertEquals(FloaterRestingTier.ACTIVE, FloaterResting.tierFor(0, now))
    }
}
