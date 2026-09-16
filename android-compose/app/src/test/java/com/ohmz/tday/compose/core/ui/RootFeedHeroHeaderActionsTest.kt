package com.ohmz.tday.compose.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the root feeds' trailing cluster costs the title beside it.
 *
 * The Anytime and Scheduled homes share [RootFeedHeroHeader], and its bar holds
 * exactly two round buttons: create-list, and an ellipsis that goes straight to
 * Settings. The obvious place to answer "the Anytime feed needs a way to reach
 * Completed" is therefore a third button — and it is the wrong place, for a
 * reason that is arithmetic rather than taste. Completed is a tile in the feed
 * on all three clients because of the numbers below.
 *
 * Plain JUnit, no Robolectric, which is why [RootFeedHeroHeaderMetrics.dockedTitleRoom]
 * is a `Dp`-in/`Dp`-out function on the metrics object rather than something read
 * out of a composition — the same bargain [TdayBarTitleReserveTest] makes for the
 * other bar. There is no emulator in this module, so a layout claim can only be
 * proven as arithmetic. A `Text` can be asked to draw; it cannot be asked what it
 * decided.
 *
 * ## Why an overrun here is invisible rather than ugly
 *
 * The docked title is `maxLines = 1` with `TextOverflow.Clip`, drawn through a
 * `graphicsLayer` scale that the metrics floor at
 * [RootFeedHeroHeaderMetrics.MinTitleScale]. Put those together and a title that
 * does not fit has no way to say so: it will not ellipsise, and once it is at half
 * size it will not shrink further. It simply keeps going, underneath the search
 * button. Nothing about the header's resting state shows it, which is why the
 * budget is asserted here instead of being left to review.
 *
 * ## Title widths
 *
 * Deliberately absent. Every claim below is a statement about a THRESHOLD — the
 * title width past which the scale floors — rather than about any particular
 * string's measured advance. That keeps the test honest: the real widths arrive at
 * runtime from a `TextMeasurer` against the device's own face, in nine locales,
 * and this module cannot see any of them. What it can prove is what the bar has
 * left to give, which is the half of the inequality the bar controls.
 */
class RootFeedHeroHeaderActionsTest {

    private val metrics = RootFeedHeroHeaderMetrics

    // Full screen widths: `width` in the header is the `maxWidth` of a
    // `fillMaxWidth()` BoxWithConstraints, so these are device widths, not a
    // padded content box. 360 is the long-standing Android baseline and the
    // width the squeeze lives at; 390 is the common modern handset; 412 is the
    // phone the earlier title-reserve bug was reported from.
    private val width360 = 360.dp
    private val width390 = 390.dp
    private val width412 = 412.dp

    private fun room(width: Dp, actions: Int): Float =
        metrics.dockedTitleRoom(width, actions).value

    @Test
    fun `the search reserve is solved for exactly the number of buttons the bar renders`() {
        // Not "two" typed twice. The inset is the reserve and the cluster is what
        // sits in it; the invariant worth holding is that they agree, so the
        // reserve is derived from the count and the count is what this asserts.
        assertEquals(2, RootFeedHeroHeaderMetrics.TrailingActionCount)

        val expected = metrics.HorizontalPadding +
            (metrics.BarButtonSize * RootFeedHeroHeaderMetrics.TrailingActionCount) +
            (metrics.BarButtonSpacing * RootFeedHeroHeaderMetrics.TrailingActionCount)
        assertEquals(expected.value, metrics.SearchTrailingInset.value, 0.001f)
        assertEquals(146f, metrics.SearchTrailingInset.value, 0.001f)
    }

    @Test
    fun `the docked title's room is the width less a fixed 268dp of furniture`() {
        // The cluster, the folded capsule, the docked mark and the two gaps are
        // all fixed, so room is linear in width with a constant of 268dp. Pinning
        // the constant rather than three sampled widths means a change to ANY of
        // the four terms lands here, not just a change that happens to bite at 360.
        assertEquals(92f, room(width360, 2), 0.001f)
        assertEquals(122f, room(width390, 2), 0.001f)
        assertEquals(144f, room(width412, 2), 0.001f)

        assertEquals(
            "room is no longer width − 268dp; one of the four terms moved",
            268f,
            width412.value - room(width412, 2),
            0.001f,
        )
    }

    @Test
    fun `a third bar control costs one circle and its gap, and 360dp cannot pay it`() {
        assertEquals(28f, room(width360, 3), 0.001f)
        assertEquals(58f, room(width390, 3), 0.001f)

        val cost = room(width360, 2) - room(width360, 3)
        assertEquals(
            (metrics.BarButtonSize + metrics.BarButtonSpacing).value,
            cost,
            0.001f,
        )
        assertEquals(64f, cost, 0.001f)

        // The vivid form of the same fact, and the one worth quoting in review:
        // with three buttons the TITLE gets less width than the 30dp leaf docked
        // beside it. A bar whose title is narrower than its own glyph has stopped
        // being a bar with a title in it.
        assertTrue(
            "a third button leaves the title narrower than the docked mark",
            room(width360, 3) < metrics.CompactMarkBox.value,
        )
    }

    @Test
    fun `a third button drops the clipping threshold below any real title`() {
        // Below `room / MinTitleScale` the scale floors and the text overruns.
        // That threshold is the whole budget, and it is what the third button
        // spends.
        val withTwo = room(width360, 2) / RootFeedHeroHeaderMetrics.MinTitleScale
        val withThree = room(width360, 3) / RootFeedHeroHeaderMetrics.MinTitleScale

        assertEquals(184f, withTwo, 0.001f)
        assertEquals(56f, withThree, 0.001f)

        // 56dp is the point of the test. The hero title is 40sp ExtraBold; 56dp is
        // on the order of two of its glyphs, so at 360dp with three buttons there
        // is no title in any locale that does not clip — not a long one, ALL of
        // them. At two buttons the threshold is 184dp, which is a real budget that
        // real titles sit inside.
        assertTrue(
            "a third button must not push the threshold under two glyphs of a 40sp title",
            withThree < metrics.HeroTitleSize.value * 2f,
        )
        assertTrue("two buttons must leave a threshold a title can live in", withTwo > 150f)
    }

    @Test
    fun `titleScales spends exactly the room dockedTitleRoom reports`() {
        // The two must not be able to drift: `titleScales` is the caller and
        // `dockedTitleRoom` is the budget, and the refactor that separated them is
        // only safe while the compact scale is still that budget over the title.
        val titleWidth = 150.dp
        val (_, compact) = metrics.titleScales(titleWidth, width360)

        assertEquals(room(width360, 2) / titleWidth.value, compact, 0.001f)
    }

    @Test
    fun `an over-long title floors rather than ellipsising, at the width it floors at`() {
        // The failure mode itself, asserted so that it is documented rather than
        // discovered. A 200dp title at 360dp is past the 184dp threshold: the
        // scale stops at MinTitleScale and the text draws 100dp wide into 92dp of
        // room, running 8dp under the search button with no ellipsis to mark it.
        val titleWidth = 200.dp
        val (_, compact) = metrics.titleScales(titleWidth, width360)

        assertEquals(RootFeedHeroHeaderMetrics.MinTitleScale, compact, 0.001f)

        val drawnWidth = titleWidth.value * compact
        assertTrue(
            "a floored title is expected to overrun its room; if it no longer does, " +
                "the threshold arithmetic above has moved",
            drawnWidth > room(width360, 2),
        )
        assertEquals(8f, drawnWidth - room(width360, 2), 0.001f)
    }

    @Test
    fun `the primitives are still the iOS numbers this header promises to match`() {
        // The object's own doc says "these are the iOS numbers ... keep the two
        // platforms in step", and the cross-client half of that is checked from
        // `tday-web/tests/guardrails/root-feed-header-actions.test.ts`, which reads
        // both this file and the Swift. Restating them here means a drift on this
        // side fails the Android build too, rather than only failing in a vitest
        // run an Android change might not think to make.
        assertEquals(18f, metrics.HorizontalPadding.value, 0.001f)
        assertEquals(56f, metrics.BarButtonSize.value, 0.001f)
        assertEquals(8f, metrics.BarButtonSpacing.value, 0.001f)
        assertEquals(20f, metrics.MarkLeading.value, 0.001f)
        assertEquals(30f, metrics.CompactMarkBox.value, 0.001f)
        assertEquals(8f, metrics.TitleGap.value, 0.001f)
        assertEquals(0.5f, RootFeedHeroHeaderMetrics.MinTitleScale, 0.001f)
    }
}
