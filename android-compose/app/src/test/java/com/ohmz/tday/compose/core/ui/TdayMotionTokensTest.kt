package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What [TdayMotionTokens] can actually get wrong.
 *
 * Nothing here compares a token to its own definition — every value in that file
 * is read straight out of [TdayMotionTokensGenerated], so an assertion pointed
 * back at the same constant would pass whatever either one changed to, which is
 * the same trap `EarlierIllustrationMotionTest` argues its way out of. The drift
 * between the shared source and the generated file is already CI's job
 * (`:shared:verifyMotionTokens`). What is left over, and only testable here, is
 * the wrapping: that a curve built from four named floats is the curve those
 * four numbers describe, that a spring factory hands back the parameters it was
 * given, and that the ladder those numbers form still holds the shape the
 * vocabulary claims for it.
 *
 * The control points and the spring parameters are written out as literals in
 * [assertCurve]'s and [assertSpring]'s callers for the same reason: an oracle
 * built from the same constants as its subject can only ever prove the subject
 * equals itself. Pinned here, a regenerated curve or spring fails this test and
 * someone has to look at it.
 */
class TdayMotionTokensTest {

    /** Two frames at 60 Hz — below this, the guardrail cannot tell two rungs apart by eye. */
    private val twoFrameMillis = 2 * 1000.0 / 60.0

    /** Loose enough for a Newton solve, tight enough that a swapped control point shows. */
    private val curveTolerance = 0.001f

    @Test
    fun `each easing is the cubic bezier its control points describe`() {
        // The independent oracle: walk the parametric curve with control points
        // (0,0), (x1,y1), (x2,y2), (1,1) and check the easing maps each x back to
        // that x's y. This is what catches the one mistake the naming cannot —
        // handing CubicBezierEasing (x1, x2, y1, y2), which compiles, produces a
        // perfectly plausible curve, and is a different animation.
        assertCurve(TdayMotionTokens.Easings.Standard, 0.4f, 0f, 0.2f, 1f)
        assertCurve(TdayMotionTokens.Easings.Enter, 0f, 0f, 0.2f, 1f)
        assertCurve(TdayMotionTokens.Easings.Exit, 0.4f, 0f, 1f, 1f)
        assertCurve(TdayMotionTokens.Easings.Scene, 0.05f, 0.7f, 0.1f, 1f)
    }

    @Test
    fun `the three shared easings are Compose's own curves`() {
        // These three are byte-identical to the built-ins, which is why this PR
        // leaves `FastOutSlowInEasing` and friends alone at their call sites: the
        // migration onto a token is a rename, not a retime. If a regeneration
        // ever moved one of them, that claim would quietly stop being true and
        // every un-migrated site would drift away from every migrated one.
        assertSameCurve(FastOutSlowInEasing, TdayMotionTokens.Easings.Standard)
        assertSameCurve(LinearOutSlowInEasing, TdayMotionTokens.Easings.Enter)
        assertSameCurve(FastOutLinearInEasing, TdayMotionTokens.Easings.Exit)
    }

    @Test
    fun `the scene curve decelerates hard rather than easing both ends`() {
        // Material's emphasised decelerate: most of the distance is spent in the
        // first fifth of the time. Standard, at the same point, has barely left.
        val sceneAtOneFifth = TdayMotionTokens.Easings.Scene.transform(0.2f)
        val standardAtOneFifth = TdayMotionTokens.Easings.Standard.transform(0.2f)
        assertTrue(
            "Scene at t=0.2 was $sceneAtOneFifth, which is not an emphasised decelerate",
            sceneAtOneFifth > 0.5f,
        )
        assertTrue(sceneAtOneFifth > standardAtOneFifth * 2f)
    }

    @Test
    fun `the duration ladder climbs, and every rung is distinguishable from the next`() {
        val ladder = listOf(
            "Quick" to TdayMotionTokens.Durations.Quick,
            "Enter" to TdayMotionTokens.Durations.Enter,
            "Change" to TdayMotionTokens.Durations.Change,
            "Emphasis" to TdayMotionTokens.Durations.Emphasis,
            "Scene" to TdayMotionTokens.Durations.Scene,
        )
        ladder.zipWithNext { (lowerName, lower), (upperName, upper) ->
            val gap = upper - lower
            assertTrue(
                "$lowerName ($lower ms) and $upperName ($upper ms) are $gap ms apart, " +
                    "under the ${twoFrameMillis.toInt()} ms two-frame floor: nobody could " +
                    "tell the correct rung from the lazy one",
                gap >= twoFrameMillis,
            )
        }
    }

    @Test
    fun `the placement lead is the emphasis rung, by construction`() {
        // PlacementLead exists to match the placement tween exactly and has no
        // freedom of its own, so this is a real constraint rather than a
        // coincidence. CelebrationLead is deliberately not checked against
        // Emphasis: it equals it today, and welding the two here would turn a
        // later retime of row placement into a silent retime of the confetti.
        assertEquals(
            TdayMotionTokens.Durations.Emphasis,
            TdayMotionTokens.Delays.PlacementLead,
        )
        assertTrue(TdayMotionTokens.Delays.CelebrationLead > 0)
    }

    @Test
    fun `each spring factory carries its own damping and stiffness`() {
        // Catches the transposition the generated names make easy to write and
        // impossible to see: Gesture's numbers in Settle's factory, or damping
        // and stiffness in each other's argument slots. Pinned as literals, like
        // the control points above — dereferencing the same generated constant on
        // both sides would pass whatever that constant moved to, and the ordering
        // test below only notices a drift that reorders the three.
        assertSpring(0.86f, 504f, TdayMotionTokens.Springs.snappy<Float>())
        assertSpring(0.82f, 340f, TdayMotionTokens.Springs.gesture<Float>())
        assertSpring(0.86f, 250f, TdayMotionTokens.Springs.settle<Float>())
    }

    @Test
    fun `the springs stay in the order their names promise`() {
        val snappy = TdayMotionTokens.Springs.snappy<Float>()
        val gesture = TdayMotionTokens.Springs.gesture<Float>()
        val settle = TdayMotionTokens.Springs.settle<Float>()

        // Snappy is the quickest to arrive, Settle the heaviest.
        assertTrue(settle.stiffness < gesture.stiffness)
        assertTrue(gesture.stiffness < snappy.stiffness)

        // Gesture is looser than the other two on purpose: a surface released by
        // a finger should overshoot a little or the release reads as a snap-back.
        assertTrue(gesture.dampingRatio < snappy.dampingRatio)
        assertTrue(gesture.dampingRatio < settle.dampingRatio)

        // All three under critical damping — a spring at 1.0 cannot overshoot at
        // all, which is a tween with extra steps.
        listOf(snappy, gesture, settle).forEach { assertTrue(it.dampingRatio < 1f) }
    }

    @Test
    fun `a spring factory stays generic and passes its visibility threshold through`() {
        // The threshold is in the units being animated, which is why it is a
        // parameter and not a token; call sites that stop a pixel-valued settle
        // depend on it arriving intact.
        val withThreshold = TdayMotionTokens.Springs.settle(visibilityThreshold = 0.5.dp)
        assertEquals(0.5.dp, withThreshold.visibilityThreshold)
        assertNull(TdayMotionTokens.Springs.settle<Dp>().visibilityThreshold)
    }

    @Test
    fun `press scales travel further on smaller surfaces`() {
        assertTrue(TdayMotionTokens.PressScales.Bar < TdayMotionTokens.PressScales.Card)
        assertTrue(TdayMotionTokens.PressScales.Card < TdayMotionTokens.PressScales.Row)
        assertTrue(TdayMotionTokens.PressScales.Row < 1f)
        // A press is a squash, not a shrink: past about a tenth the surface reads
        // as leaving rather than answering.
        assertTrue(TdayMotionTokens.PressScales.Bar > 0.9f)
    }

    /**
     * Asserts [easing] is the cubic bezier through (0,0), (x1,y1), (x2,y2), (1,1),
     * by sampling the parametric curve directly instead of asking the easing what
     * it thinks its own control points were.
     */
    private fun assertCurve(easing: Easing, x1: Float, y1: Float, x2: Float, y2: Float) {
        for (step in 0..20) {
            val t = step / 20f
            val inv = 1f - t
            val x = 3f * inv * inv * t * x1 + 3f * inv * t * t * x2 + t * t * t
            val y = 3f * inv * inv * t * y1 + 3f * inv * t * t * y2 + t * t * t
            assertEquals(
                "at t=$t the curve passes ($x, $y) but the easing maps $x elsewhere",
                y,
                easing.transform(x),
                curveTolerance,
            )
        }
    }

    private fun assertSameCurve(expected: Easing, actual: Easing) {
        for (step in 0..20) {
            val fraction = step / 20f
            assertEquals(
                "curves diverge at $fraction",
                expected.transform(fraction),
                actual.transform(fraction),
                curveTolerance,
            )
        }
    }

    private fun <T> assertSpring(
        expectedDamping: Float,
        expectedStiffness: Float,
        spec: SpringSpec<T>,
    ) {
        assertEquals(expectedDamping, spec.dampingRatio, 0f)
        assertEquals(expectedStiffness, spec.stiffness, 0f)
    }
}
