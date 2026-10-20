package com.ohmz.tday.compose.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The root dock's fold point, driven the way a finger drives it: one answer at a time,
 * each one fed the one before it.
 *
 * The defect this pins is not a wrong distance. Both screens folded the dock on a single
 * `offset > 44.dp` comparison, and a single comparison has no memory, so the pixel either
 * side of 44 dp is the pixel either side of a 64 dp pill and a 236 dp capsule. A list
 * that settles a dp back and forth under its own fling — or a finger resting there — made
 * the dock strobe. Endpoint assertions cannot see that: 20 dp of scroll leaves the dock
 * open and 60 dp folds it, before this change and after it. The third case below is the
 * whole test. At 30 dp, having already folded, the dock must STAY folded; the symmetric
 * comparison answers that it opens.
 *
 * Expectations are pinned as literals rather than re-derived from
 * [RootFeedDockCollapse.CollapseThreshold] and [RootFeedDockCollapse.ExpandThreshold] —
 * see `EarlierIllustrationMotionTest` for the argument. A test that computes its inputs
 * from the constants it is testing agrees with them whatever they become, and these two
 * are shared with iOS and web, which is exactly the drift worth failing over.
 */
class RootFeedDockCollapseTest {

    /** One px to the dp, so every number below reads as the dp a finger travelled. */
    private val density = Density(1f)

    private val collapsePx = 44
    private val expandPx = 24

    private fun px(value: Dp): Int = with(density) { value.roundToPx() }

    /** A feed still on its first item, scrolled [travelled] into it. */
    private fun scrolledBy(travelled: Dp) = LazyListState(0, px(travelled))

    private fun fold(previous: Boolean, state: LazyListState): Boolean =
        RootFeedDockCollapse.next(
            previous = previous,
            firstVisibleItemIndex = state.firstVisibleItemIndex,
            scrollOffsetPx = state.firstVisibleItemScrollOffset,
            collapsePx = collapsePx,
            expandPx = expandPx,
        )

    @Test
    fun `the two thresholds are the distances the other clients fold at`() {
        assertEquals(collapsePx, px(RootFeedDockCollapse.CollapseThreshold))
        assertEquals(expandPx, px(RootFeedDockCollapse.ExpandThreshold))
    }

    @Test
    fun `a fold survives a scroll back that an open dock would not have folded on`() {
        val openApproaching = fold(previous = false, state = scrolledBy(40.dp))
        val folded = fold(previous = openApproaching, state = scrolledBy(46.dp))
        val stillFolded = fold(previous = folded, state = scrolledBy(30.dp))
        val openAgain = fold(previous = stillFolded, state = scrolledBy(20.dp))

        assertFalse(openApproaching)
        assertTrue(folded)
        assertTrue(stillFolded)
        assertFalse(openAgain)
    }

    @Test
    fun `a feed scrolled off its first item is folded whatever its offset says`() {
        assertTrue(fold(previous = false, state = LazyListState(1, 0)))
        assertTrue(fold(previous = false, state = LazyListState(7, px(2.dp))))
    }

    @Test
    fun `an offset above the top of the feed is no travel at all`() {
        // A `LazyListState` hands back whatever offset it was built with, negative
        // included. The claim here is about the answer rather than about the clamp
        // that produces it — with both edges positive a negative offset compares false
        // either way. What it would catch is the offset being read as a position, and
        // the dock folding on anything that is not exactly zero.
        val bounced = LazyListState(0, -120)

        assertFalse(fold(previous = false, state = bounced))
        assertFalse(fold(previous = true, state = bounced))
    }
}
