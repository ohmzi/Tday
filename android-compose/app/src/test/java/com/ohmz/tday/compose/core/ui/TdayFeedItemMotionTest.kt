package com.ohmz.tday.compose.core.ui

import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
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
    fun `displaced items finish moving exactly when the empty scene starts rising`() {
        // The burst leads alone, then the scene comes up through it. On a feed
        // that hosts the scene inline, everything the scene pushed aside has to
        // have landed by then — a tile still sliding while the illustration
        // fades in is the snap this whole spec exists to remove. Widen the lead
        // in TdayEmptyState and this placement has to follow it.
        assertEquals(
            TdayEmptyStateCelebrationLeadMillis,
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
        assertEquals(
            true,
            TdayFeedItemMotion.FadeOutMillis < TdayFeedItemMotion.FadeInMillis,
        )
        assertEquals(
            true,
            TdayFeedItemMotion.FadeOutMillis < TdayFeedItemMotion.PlacementMillis,
        )
    }
}
