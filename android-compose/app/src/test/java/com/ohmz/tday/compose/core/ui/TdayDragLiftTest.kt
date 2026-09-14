package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a lift is, checked without a device.
 *
 * The two drag previews are private composables inside two 3,000-line screens
 * and neither is reachable from a unit test, so what is pinned here is the
 * decision they both read: that a card rises rather than fades, that it rises by
 * the press scale read backwards instead of by a number of its own, and that a
 * platform with animations off gets a card that is already up.
 *
 * [LiftedScale] is asserted against the token it is derived from rather than
 * against 1.03, for the reason `EarlierIllustrationMotionTest` gives about its
 * own literal read the other way round: comparing a derivation back to its own
 * arithmetic would pass whatever either end changed to. What matters is that it
 * is the card press scale mirrored, so this checks the mirror.
 */
class TdayDragLiftTest {

    @Test
    fun `a lifted card rises by exactly as much as a pressed one sinks`() {
        // The press token is how far a card travels under a finger; a lift is the
        // same card going the other way under the same finger. Written as the
        // distance either side of rest so the assertion says the relationship
        // rather than restating the subtraction in the source.
        val sink = 1f - TdayMotionTokens.PressScales.Card
        val rise = TdayDragLift.LiftedScale - 1f
        assertEquals(sink, rise, 1e-6f)
    }

    @Test
    fun `a lift is elevation and scale, and both of them move`() {
        // The defect this replaced was a preview composed straight into its final
        // size and elevation: correct at rest, and never a pick-up. Either end
        // collapsing onto the other would put it back.
        assertTrue(TdayDragLift.LiftedScale > 1f)
        assertTrue(TdayDragLift.LiftedElevation > TdayDragLift.RestingElevation)
        assertEquals(0.dp, TdayDragLift.RestingElevation)
    }

    @Test
    fun `the rise ends where the card is meant to sit`() {
        assertEquals(1f, TdayDragLift.scaleAt(0f), 1e-6f)
        assertEquals(TdayDragLift.LiftedScale, TdayDragLift.scaleAt(1f), 1e-6f)
        assertEquals(TdayDragLift.RestingElevation, TdayDragLift.elevationAt(0f))
        assertEquals(TdayDragLift.LiftedElevation, TdayDragLift.elevationAt(1f))
    }

    @Test
    fun `the row left behind dims rather than the card the finger is holding`() {
        // Transparency has one job on these screens and this is it. A preview at
        // partial alpha reads as a task the user may not have, which is the
        // opposite of what a lift says; the hole the card came out of is the
        // thing that should look less than present.
        assertTrue(TdayDragLift.VacatedAlpha < 1f)
        assertTrue(TdayDragLift.VacatedAlpha > 0f)
    }

    @Test
    fun `the pick-up takes Emphasis, and no time at all when motion is off`() {
        // Emphasis by the geometry rule — the card changes how big it is — and
        // `snap` rather than a shortened tween under reduced motion, because the
        // trip is what goes and the destination is what stays.
        val spec = TdayDragLift.spec(motionEnabled = true)
        assertEquals(
            TdayMotionTokens.Durations.Emphasis,
            (spec as TweenSpec<Float>).durationMillis,
        )
        assertEquals(TdayMotionTokens.Easings.Enter, spec.easing)
        assertTrue(TdayDragLift.spec(motionEnabled = false) is SnapSpec<Float>)
    }
}
