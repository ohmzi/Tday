package com.ohmz.tday.compose.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic [scaledDelay] runs on.
 *
 * Compose already scales the animations themselves against `ANIMATOR_DURATION_SCALE`;
 * `delay()` is the one part of a choreography that does not follow, so these are the
 * numbers that decide whether a wait between two beats keeps step with them or drifts
 * away from them. The 0x case is the one `docs/motion.md`'s fifth idiom rule is about
 * and the only one anybody would think to write; the rest are why the value is a
 * scale and not a switch.
 */
class TdayMotionTest {

    @Test
    fun `animations off means no wait at all`() {
        // Not "a very short wait". The animation this gap was covering finished on
        // the first frame, so the destination is already drawn and every millisecond
        // in front of it is the rule broken from the other side.
        assertEquals(0L, scaledDelayMillis(2300L, 0f))
        assertEquals(0L, scaledDelayMillis(150L, 0f))
    }

    @Test
    fun `an untouched device waits exactly as long as it always did`() {
        // The whole app goes through this function now, so 1x has to be an identity
        // and not merely close to one.
        assertEquals(2300L, scaledDelayMillis(2300L, 1f))
        assertEquals(150L, scaledDelayMillis(150L, 1f))
        assertEquals(300L, scaledDelayMillis(300L, 1f))
    }

    @Test
    fun `the slider's other four settings scale the gap with the motion`() {
        // Android offers six values, not two. A user on 2x has animations that take
        // twice as long; a gap that stayed at 1x would fire with the motion it was
        // meant to lead still half-played.
        assertEquals(75L, scaledDelayMillis(150L, 0.5f))
        assertEquals(300L, scaledDelayMillis(150L, 2f))
        assertEquals(750L, scaledDelayMillis(150L, 5f))
        assertEquals(1500L, scaledDelayMillis(150L, 10f))
    }

    @Test
    fun `a scale nothing sensible wrote cannot strand the user in front of a wait`() {
        // `ANIMATOR_DURATION_SCALE` is a plain Settings.Global float. Nothing in the
        // UI can write a negative or a NaN into it, which is exactly why the app must
        // not fall over if it finds one: both mean "we do not know", and the safe
        // answer to that is the finished state now rather than a wait of unknown
        // length in front of it.
        assertEquals(0L, scaledDelayMillis(2300L, -1f))
        assertEquals(0L, scaledDelayMillis(2300L, Float.NaN))
    }

    @Test
    fun `the in-app switch subtracts motion at any scale`() {
        // The switch is an answer about this app, not about the slider: it has to
        // reduce a 10x device as readily as a 1x one, or somebody who moved the
        // slider up once would find the app's own preference silently inert.
        assertEquals(0f, effectiveMotionScale(1f, reduceInApp = true), 0f)
        assertEquals(0f, effectiveMotionScale(10f, reduceInApp = true), 0f)
        assertEquals(0f, effectiveMotionScale(0.5f, reduceInApp = true), 0f)
    }

    @Test
    fun `the in-app switch cannot hand animations back`() {
        // The direction that matters. A user who told Android to remove animations
        // and left this switch off has asked for nothing extra — not for motion — so
        // 0x survives an off switch. An in-app preference that could raise the
        // platform's answer would be an accessibility setting the app overrules.
        assertEquals(0f, effectiveMotionScale(0f, reduceInApp = false), 0f)
    }

    @Test
    fun `an untouched device with the switch off is left exactly as it was`() {
        // The other three-quarters of the truth table are one line, but this is the
        // one every user is in: neither answer asks for anything, so the scale is
        // the platform's own and the app is not quietly re-timing itself.
        assertEquals(1f, effectiveMotionScale(1f, reduceInApp = false), 0f)
        assertEquals(2f, effectiveMotionScale(2f, reduceInApp = false), 0f)
    }

    @Test
    fun `a fractional result rounds rather than truncating`() {
        // 0.5x of an odd number. Truncating would bias every scaled wait in the app
        // short by up to a millisecond, which is invisible once and a drift when the
        // legs of one choreography each lose their own.
        assertEquals(191L, scaledDelayMillis(381L, 0.5f))
    }
}
