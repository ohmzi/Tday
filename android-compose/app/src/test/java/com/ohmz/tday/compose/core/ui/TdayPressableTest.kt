package com.ohmz.tday.compose.core.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a press is, checked without a device.
 *
 * `Modifier.tdayPressable` is a composable factory and no JVM test can reach it,
 * so what is pinned here is the set of decisions it reads: that a pressed
 * surface goes down rather than up, that the depth is always one of the three
 * press tokens and never a number this file holds, and that a platform with
 * animations off gets a surface that is already all the way down.
 *
 * The scale assertions run against [TdayMotionTokens.PressScales] rather than
 * against 0.94/0.97/0.985 restated, for the reason [TdayDragLiftTest] gives
 * about its own derivation: a test that repeats the value it is checking passes
 * whatever either end changes to. What matters about the three is the ordering
 * — smaller surfaces travel further — and that is what is checked.
 */
class TdayPressableTest {

    @Test
    fun `a pressed surface goes down, by the same distance iOS drops one`() {
        // 2 dp, matching `TdayPressEffectModifier`'s 2 pt. The sign is the part
        // worth a gate: the modifier adds this to y, and a negative here would
        // make every press in the app a lift.
        assertTrue(TdayPress.SinkOffset > 0.dp)
        assertEquals(2.dp, TdayPress.SinkOffset)
    }

    @Test
    fun `smaller surfaces squash further, and nothing grows under a finger`() {
        // The whole point of three tokens rather than one: the same absolute
        // travel reads as a bigger gesture on a bar button than on a full-width
        // row. Collapse the ordering and the classes stop meaning anything, and
        // any of them at or above 1f is a press that pops.
        val bar = TdayMotionTokens.PressScales.Bar
        val card = TdayMotionTokens.PressScales.Card
        val row = TdayMotionTokens.PressScales.Row
        assertTrue(bar < card)
        assertTrue(card < row)
        assertTrue(row < 1f)
        assertTrue(bar > 0f)
    }

    @Test
    fun `motion off means already pressed, not pressed more slowly`() {
        // Reduced motion removes the trip and never the destination. The
        // animated value is deliberately nothing like the target here: if the
        // switch ever reverted to reading the animation, this is the case that
        // would show it, because a surface halfway down is exactly what a
        // shortened tween would leave behind.
        assertEquals(
            TdayMotionTokens.PressScales.Card,
            TdayPress.shown(animated = 0.999f, target = TdayMotionTokens.PressScales.Card, motionEnabled = false),
            0f,
        )
        assertEquals(
            TdayPress.SinkOffset,
            TdayPress.shown(animated = 0.dp, target = TdayPress.SinkOffset, motionEnabled = false),
        )
    }

    @Test
    fun `motion off releases instantly too, and motion on watches the animation`() {
        // A press that arrived instantly and then eased back out would be worse
        // than either choice made consistently, so the release is pinned from
        // the same function as the arrival.
        assertEquals(1f, TdayPress.shown(animated = 0.95f, target = 1f, motionEnabled = false), 0f)
        assertEquals(0.dp, TdayPress.shown(animated = 1.dp, target = 0.dp, motionEnabled = false))

        val animated = Any()
        assertSame(animated, TdayPress.shown(animated = animated, target = Any(), motionEnabled = true))
    }
}
