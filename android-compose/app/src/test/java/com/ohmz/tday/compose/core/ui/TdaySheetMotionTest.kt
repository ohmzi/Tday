package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Easing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What [TdaySheetMotion] can get wrong that reading it cannot catch.
 *
 * Two kinds of claim are pinned here, and neither is "this constant equals itself".
 *
 * The first is that each spec is still *on* the rung its doc names. Written the wrong way
 * round — asserting against a literal 200 or 320 — the test would go red on a regeneration
 * that moved the whole ladder, which is not a defect, and stay green on the one thing that
 * is: somebody quietly retyping a rung at a call site. So every assertion below compares a
 * spec to a named [TdayMotionTokens.Durations] constant, and a hand-written number in this
 * file would defeat the point of it.
 *
 * The second is the relation between the four, which is where the argument actually lives:
 * an exit is never longer than the enter it undoes, and a direction is visible in the curve
 * as well as in the length. Those are the two things the doc comment asserts in prose and
 * nothing enforced until this file existed.
 */
class TdaySheetMotionTest {

    @Test
    fun `each spec runs for the rung its name claims`() {
        assertEquals(
            TdayMotionTokens.Durations.Enter,
            TdaySheetMotion.scrimIn<Float>().durationMillis,
        )
        assertEquals(
            TdayMotionTokens.Durations.Enter,
            TdaySheetMotion.scrimOut<Float>().durationMillis,
        )
        assertEquals(
            TdayMotionTokens.Durations.Emphasis,
            TdaySheetMotion.cardIn<Float>().durationMillis,
        )
        assertEquals(
            TdayMotionTokens.Durations.Change,
            TdaySheetMotion.cardOut<Float>().durationMillis,
        )
    }

    @Test
    fun `the constants and the factories cannot drift apart`() {
        // The four Millis constants are public and the four factories are what call sites
        // actually pass, so the two are separately editable. A factory rebuilt against a
        // different rung than the constant above it would be invisible from either end —
        // and the relational tests below all read the constants, so a drift there would
        // quietly move what they are proving.
        assertEquals(TdaySheetMotion.ScrimInMillis, TdaySheetMotion.scrimIn<Float>().durationMillis)
        assertEquals(
            TdaySheetMotion.ScrimOutMillis,
            TdaySheetMotion.scrimOut<Float>().durationMillis,
        )
        assertEquals(TdaySheetMotion.CardInMillis, TdaySheetMotion.cardIn<Float>().durationMillis)
        assertEquals(TdaySheetMotion.CardOutMillis, TdaySheetMotion.cardOut<Float>().durationMillis)
    }

    @Test
    fun `the card leaves faster than it arrived`() {
        // Rule 1 of the idiom: an exit is never longer than its own enter. Asserted rather
        // than commented, because the two are chosen from different rungs and nothing else
        // in the tree would notice if a later edit put the exit back on Emphasis.
        assertTrue(
            "cardOut (${TdaySheetMotion.CardOutMillis} ms) must be shorter than " +
                "cardIn (${TdaySheetMotion.CardInMillis} ms)",
            TdaySheetMotion.CardOutMillis < TdaySheetMotion.CardInMillis,
        )
    }

    @Test
    fun `the scrim never outlasts the card it dims for`() {
        // One-sided on purpose, because the mismatch is one-sided: a dim still standing
        // over a card that has already landed is the defect this object was written for,
        // and it is what the Dialog-clocked scrim did. A dim that finishes inside the
        // card's travel is not — it is what ships, scrimOut ending 60 ms before cardOut,
        // the same relation iOS draws with 0.20 under 0.24. Equal lengths pass as well.
        assertTrue(TdaySheetMotion.ScrimInMillis <= TdaySheetMotion.CardInMillis)
        assertTrue(TdaySheetMotion.ScrimOutMillis <= TdaySheetMotion.CardOutMillis)
    }

    @Test
    fun `in and out are not the same curve`() {
        // The tree ran both halves of both surfaces on Standard. A decelerate arriving and
        // an accelerate leaving is the split; sampling the curves catches an edit that put
        // them back on one easing while leaving the durations alone.
        assertDifferentCurve(
            TdaySheetMotion.cardIn<Float>().easing,
            TdaySheetMotion.cardOut<Float>().easing,
        )
        assertDifferentCurve(
            TdaySheetMotion.scrimIn<Float>().easing,
            TdaySheetMotion.scrimOut<Float>().easing,
        )
    }

    @Test
    fun `an arrival decelerates and a departure accelerates`() {
        // Which way round, not merely that they differ: at the halfway point a decelerate
        // has covered more than half the distance and an accelerate less. This is what
        // would catch enter and exit being handed each other's curve — two specs that are
        // still different, still on the right rungs, and read backwards on screen.
        assertTrue(TdaySheetMotion.cardIn<Float>().easing.transform(0.5f) > 0.5f)
        assertTrue(TdaySheetMotion.cardOut<Float>().easing.transform(0.5f) < 0.5f)
        assertTrue(TdaySheetMotion.scrimIn<Float>().easing.transform(0.5f) > 0.5f)
        assertTrue(TdaySheetMotion.scrimOut<Float>().easing.transform(0.5f) < 0.5f)
    }

    private fun assertDifferentCurve(one: Easing, other: Easing) {
        val differs = (1..19).any { step ->
            val fraction = step / 20f
            one.transform(fraction) != other.transform(fraction)
        }
        assertTrue("the two specs carry the same easing", differs)
    }
}
