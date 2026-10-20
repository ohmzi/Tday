package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.ui.TdayFeedItemMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This file used to pin `EarlierExpandDeferMillis` -- the `delay()`
 * `onTimelineSectionHeaderToggle` ran before it would expand Earlier -- to the
 * exact length of the inline scene's own exit, on the argument that "Earlier's
 * rows must never start animating in while the scene is still visibly fading
 * and shrinking away". That argument was sound while the scene sat ABOVE
 * Earlier's header and the two wanted one slot. With the scene below Earlier's
 * rows the rows arrive between the header and the scene, and the scene's fade
 * and its downward travel are what the arrival is made of rather than something
 * it has to wait for. The wait went out; see [shouldShowEarlierScene].
 *
 * What survives is the other half of the pairing, and it is the half the number
 * was always about: a hold measured against a real motion, so that whoever
 * retimes the motion is made to notice the hold. Both remaining pins are that
 * shape.
 */
class EarlierIllustrationMotionTest {

    @Test
    fun `the celebration hold matches the travel it stands in front of`() {
        // The scene's burst is held back by exactly as long as the scene's own
        // placement takes, because the placement is what precedes it: the feed
        // re-laying itself around an item that has just appeared (or that
        // Earlier's rows have just pushed down). A hardcoded 320L rather than
        // `assertEquals(TdayFeedItemMotion.PlacementMillis.toLong(), ...)`:
        // `CelebrationStartDelayMillis` is DEFINED as `PlacementMillis`, so
        // comparing it back against its own definition would pass whatever
        // either one became. Pinning the literal means widening `Placement`
        // fails here and forces whoever widened it to look at the burst that is
        // timed against it.
        assertEquals(320L, TdayFeedItemMotion.CelebrationStartDelayMillis)
        assertEquals(
            TdayFeedItemMotion.PlacementMillis.toLong(),
            TdayFeedItemMotion.CelebrationStartDelayMillis,
        )
    }

    @Test
    fun `the scene's exit is shorter than its arrival, which is what a hand-off wants`() {
        // The first idiom rule at this one site: the scene leaves on
        // `FadeOutMillis` and arrives on `FadeInMillis`, and an absence should
        // not linger. Pinned here because both numbers are now read directly by
        // the `AnimatedVisibility` around the scene and by nothing else in this
        // hand-off -- there is no third constant derived from them any more to
        // notice a change on their behalf.
        assertEquals(190, TdayFeedItemMotion.FadeInMillis)
        assertEquals(150, TdayFeedItemMotion.FadeOutMillis)
        assertTrue(TdayFeedItemMotion.FadeOutMillis < TdayFeedItemMotion.FadeInMillis)
    }

    // --- reduced motion ----------------------------------------------------

    @Test
    fun `motion off draws the finished state and keeps no wait`() {
        // The fifth idiom rule at this hand-off, and the reason it is satisfiable
        // here at all: with the expand beat retired there is no wait left to
        // zero, so "motion off" is purely a question of whether the scene
        // animates its own arrival and departure. It does not.
        assertFalse(
            earlierSceneAnimatesHandoff(timelineAnimationsEnabled = true, motionEnabled = false),
        )
    }

    @Test
    fun `the feed's first-frame guard is not the preference, and neither substitutes`() {
        // `timelineAnimationsEnabled` only says the list has settled enough to
        // animate at all; the scene consulted it alone for a long time, which
        // left a user who had asked for less motion watching a third of a screen
        // fade and expand anyway. Both have to be true, and each has to be able
        // to close the gate on its own.
        assertFalse(
            earlierSceneAnimatesHandoff(timelineAnimationsEnabled = false, motionEnabled = true),
        )
        assertFalse(
            earlierSceneAnimatesHandoff(timelineAnimationsEnabled = false, motionEnabled = false),
        )
        assertTrue(
            earlierSceneAnimatesHandoff(timelineAnimationsEnabled = true, motionEnabled = true),
        )
    }
}
