package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the skeleton can get wrong that reading it cannot catch.
 *
 * Two kinds of claim, written two different ways round on purpose.
 *
 * The MOTION claims compare against named [TdayMotionTokens.Durations] constants. A literal
 * 200 or 260 here would go red on a regeneration that legitimately moved the whole ladder,
 * and stay green on the one thing that is actually a defect: somebody retyping a rung at a
 * call site. This is [TdaySheetMotionTest]'s argument and it applies unchanged.
 *
 * The GEOMETRY claims do the opposite — they pin the literals the task row shipped. Asserting
 * `TdayTaskRowMetrics.CheckTargetMinSize == TdayTaskRowMetrics.CheckTargetMinSize` by way of
 * the skeleton would pass no matter what either one changed to, which is the trap
 * `EarlierIllustrationMotionTest` names in as many words when it pins a literal `150L` rather
 * than re-deriving it. The row and the skeleton already share one symbol each, so drift
 * between them is impossible; what is still possible, and is what these pin, is the shared
 * symbol quietly being given a different value than the row was drawn at.
 */
class TdayTaskRowSkeletonTest {

    @Test
    fun `the hand-over is on the Enter rung`() {
        assertEquals(TdayMotionTokens.Durations.Enter, TdayTaskRowSkeleton.HandoffMillis)
    }

    @Test
    fun `the hand-over does not reach for the feed's own arrival literal`() {
        // 190 is a hand-written Android number that docs/motion.md settled against the ladder
        // in favour of 200, and that both other clients' feed-motion mirrors name in writing
        // as the value that owes the move. A skeleton wired to TdayFeedItemMotion.FadeInMillis
        // would compile, animate, look right, and make that literal a little harder to retire
        // — so the wiring is what is asserted, not the appearance.
        assertTrue(
            "the hand-over must name a rung, not TdayFeedItemMotion.FadeInMillis " +
                "(${TdayFeedItemMotion.FadeInMillis} ms)",
            TdayTaskRowSkeleton.HandoffMillis != TdayFeedItemMotion.FadeInMillis,
        )
    }

    @Test
    fun `the pulse is on the Change rung`() {
        assertEquals(
            TdayMotionTokens.Durations.Change,
            (TdayTaskRowSkeleton.pulse().animation as TweenSpec<Float>).durationMillis,
        )
    }

    @Test
    fun `the constants and the factories cannot drift apart`() {
        // Both constants are public and both factories are what call sites actually pass, so
        // the two halves are separately editable and a factory rebuilt against a different
        // rung would be invisible from either end.
        assertEquals(
            TdayTaskRowSkeleton.HandoffMillis,
            TdayTaskRowSkeleton.handoff<Float>().durationMillis,
        )
        assertEquals(
            TdayTaskRowSkeleton.PulseMillis,
            (TdayTaskRowSkeleton.pulse().animation as TweenSpec<Float>).durationMillis,
        )
    }

    @Test
    fun `the hand-over runs both halves off one curve`() {
        // Standard, not Enter and not Exit: the skeleton leaving and the feed arriving are one
        // motion on one clock, and a decelerate or an accelerate would be describing only half
        // of it. Identity rather than sampling, because Easings.Standard is the shared val the
        // call site is meant to be naming.
        assertSame(TdayMotionTokens.Easings.Standard, TdayTaskRowSkeleton.handoff<Float>().easing)
    }

    @Test
    fun `the pulse reverses rather than restarting`() {
        // A Restart pulse returns from dim to bright on a single frame, once every 260 ms.
        // That is a flicker with a period, and it is exactly what a placeholder must not do.
        assertEquals(RepeatMode.Reverse, TdayTaskRowSkeleton.pulse().repeatMode)
    }

    @Test
    fun `a stopped pulse is frozen at the legible end`() {
        // Compose does not leave a stopped infinite transition where it stopped it. At
        // animator duration scale 0, `InfiniteTransition` calls `skipToEnd()` on every
        // animation it owns, and that assigns the `TargetBasedAnimation`'s TARGET — so the
        // value left on screen is the far end of the breath, 45%, for the whole load. A
        // placeholder that cannot animate and cannot be read is the fifth idiom rule broken
        // twice, and it is what a device with "Remove animations" on used to get.
        //
        // Pinned against the end it must NOT resolve to as well as the one it must, because
        // the defect and the fix differ by one identifier: `frozenAlpha` returning
        // `DimmedAlpha` would still be a live gate and still be the whole bug.
        assertEquals(TdayTaskRowSkeleton.RestingAlpha, TdayTaskRowSkeleton.frozenAlpha(0f)!!, 0f)
        assertTrue(TdayTaskRowSkeleton.frozenAlpha(0f) != TdayTaskRowSkeleton.DimmedAlpha)
        assertEquals(1f, TdayTaskRowSkeleton.RestingAlpha, 0f)
        assertTrue(TdayTaskRowSkeleton.DimmedAlpha > 0f)
        assertTrue(TdayTaskRowSkeleton.RestingAlpha > TdayTaskRowSkeleton.DimmedAlpha)
    }

    @Test
    fun `a pulse that is still running is left alone`() {
        // `null` and not `RestingAlpha`: a frozen alpha that answered for every scale would
        // pin the bars bright on every device, which is the pulse deleted rather than
        // repaired — and `docs/motion/LEDGER.md` exempts skeleton pulses from the
        // reduced-motion floor precisely so that they keep running.
        assertNull(TdayTaskRowSkeleton.frozenAlpha(1f))
        assertNull(TdayTaskRowSkeleton.frozenAlpha(0.5f))
        assertNull(TdayTaskRowSkeleton.frozenAlpha(10f))
    }

    @Test
    fun `the placeholder is drawn at the task row's own dimensions`() {
        // The numbers TodayTodoRow shipped, written out. The row and the skeleton read one
        // symbol each for these, so they cannot disagree with each other — what they can do is
        // agree on a value nobody meant, which is what a re-space of the skeleton alone would
        // look like from inside TdayTaskRowMetrics.
        assertEquals(48.dp, TdayTaskRowMetrics.CheckTargetMinSize)
        assertEquals(24.dp, TdayTaskRowMetrics.CheckGlyphSize)
        assertEquals(10.dp, TdayTaskRowMetrics.TextColumnStartPadding)
        assertEquals(6.dp, TdayTaskRowMetrics.RowSpacing)
        assertEquals(4.dp, TdayTaskRowMetrics.RowVerticalPadding)
        assertEquals(1.dp, TdayTaskRowMetrics.DividerThickness)
        assertEquals(0.58f, TdayTaskRowMetrics.DividerAlpha, 0f)
    }

    @Test
    fun `the check target is a touch target and the glyph inside it is not`() {
        // Android's minimum is 48 dp and the glyph is half that. A skeleton drawn at the glyph
        // size would be a row two-thirds the height of the one it stands in for, which is the
        // whole jump this component exists to remove.
        assertTrue(TdayTaskRowMetrics.CheckGlyphSize < TdayTaskRowMetrics.CheckTargetMinSize)
        assertTrue(TdayTaskRowMetrics.CheckTargetMinSize >= 48.dp)
    }
}
