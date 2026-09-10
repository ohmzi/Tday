package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.ui.TdayFeedItemMotion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [EarlierExpandDeferMillis] -- the `delay()` `onTimelineSectionHeaderToggle`
 * runs before actually expanding Earlier ([SectionHeaderToggleAction.DEFER_EARLIER_EXPAND])
 * -- to the exact duration the inline illustration's own exit animation
 * (`fadeOut` + `shrinkVertically`, both [TdayFeedItemMotion.FadeOutMillis], on
 * the `AnimatedVisibility` in [TodoListScreen] around `TdayEmptyState`) takes
 * to finish.
 *
 * That pairing is the entire ordering guarantee: Earlier's rows must never
 * start animating in while the scene is still visibly fading and shrinking
 * away, or the hand-off both this test and [TodayEarlierEmptyStateGatingTest]
 * exist for shows both states on screen at once. A hardcoded expectation
 * (150L, not a re-derivation of [TdayFeedItemMotion.FadeOutMillis]) rather
 * than `assertEquals(TdayFeedItemMotion.FadeOutMillis.toLong(),
 * EarlierExpandDeferMillis)`: [EarlierExpandDeferMillis] is *defined* in
 * terms of [TdayFeedItemMotion.FadeOutMillis], so comparing it back against
 * its own definition would pass no matter what either one changed to. Pinning
 * the literal instead means a change to [TdayFeedItemMotion.FadeOutMillis]
 * -- widening the illustration's own exit, say -- fails this test and forces
 * whoever made that change to notice the delay it is paired with needs the
 * same update, rather than silently drifting the two apart the way an
 * un-animated toggle's instant (0ms) duration could hide for as long as
 * nothing actually animated.
 */
class EarlierIllustrationMotionTest {

    @Test
    fun `the expand-defer delay matches the illustration's own exit duration`() {
        assertEquals(150L, EarlierExpandDeferMillis)
        assertEquals(TdayFeedItemMotion.FadeOutMillis.toLong(), EarlierExpandDeferMillis)
    }
}
