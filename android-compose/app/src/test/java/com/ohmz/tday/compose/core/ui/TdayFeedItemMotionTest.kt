package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floater home draws [TdayEmptyState] inline, in the slot the last completed
 * row leaves, so finishing a list moves the rest of that feed down by the height
 * of the scene. These are the numbers that keep that move from reading as a snap
 * under the confetti; they are only correct relative to each other, which is what
 * this pins.
 */
class TdayFeedItemMotionTest {

    @Test
    fun `the celebration cannot begin before the displaced items have landed`() {
        // The list-detail screens draw the empty scene as an overlay: nothing on
        // the page moves, so the burst has the screen to itself and the scene
        // rises through it. A feed that hosts the scene inline reproduces that by
        // holding the whole celebration until its own layout has settled. Let
        // this delay fall below the placement duration and the confetti is once
        // again thrown across a Completed tile that is still sliding.
        assertTrue(
            TdayFeedItemMotion.CelebrationStartDelayMillis >=
                TdayFeedItemMotion.PlacementMillis.toLong(),
        )
    }

    @Test
    fun `feed item specs run for their declared durations`() {
        assertEquals(
            TdayFeedItemMotion.FadeInMillis,
            (TdayFeedItemMotion.FadeIn as TweenSpec<Float>).durationMillis,
        )
        assertEquals(
            TdayFeedItemMotion.PlacementMillis,
            (TdayFeedItemMotion.Placement as TweenSpec<*>).durationMillis,
        )
        assertEquals(
            TdayFeedItemMotion.FadeOutMillis,
            (TdayFeedItemMotion.FadeOut as TweenSpec<Float>).durationMillis,
        )
    }

    @Test
    fun `an item leaves faster than it arrives`() {
        // A row that is gone should stop being drawn before the gap it left
        // finishes closing, or the ghost is still fading while its neighbours
        // have already taken the space.
        assertTrue(TdayFeedItemMotion.FadeOutMillis < TdayFeedItemMotion.FadeInMillis)
        assertTrue(TdayFeedItemMotion.FadeOutMillis < TdayFeedItemMotion.PlacementMillis)
    }
}
