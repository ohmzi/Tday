package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.ui.FeedAnswer
import com.ohmz.tday.compose.core.ui.feedAnswer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The replacement for `EarlierHeaderPlacementSpecTest`, which pinned
 * `earlierHeaderSkipsPlacementSpec` -- the rule that Earlier's header must not
 * chase a `placementSpec` while the inline empty-state scene resized itself
 * directly ABOVE it. That rule described a screen this repo no longer draws.
 * The scene is emitted below Earlier's rows now, nothing above the header
 * changes height, and a test left green over a premise that has stopped being
 * true is worse than no test at all: it reports that the screen is pinned while
 * pinning nothing.
 *
 * So the argument is rewritten rather than deleted, onto the two decisions that
 * replaced it. [earlierSceneFollowsSection] is the whole of the ordering claim
 * -- hero, Earlier's header, Earlier's rows, then the scene -- and
 * [shouldShowEarlierScene] is the whole of the sequencing: one boolean whose
 * single flip both releases the rows and takes the scene away, in that one
 * frame, in both directions.
 *
 * Neither can assert the pixel outcome. This module has no Compose UI harness
 * and no device, which is exactly why both decisions were written as pure
 * functions in the first place: a layout claim that lives only in the position
 * of an `item {}` call is a claim nothing can check, and this file is the
 * strongest form of proof available here.
 */
class EarlierSceneOrderTest {

    // --- earlierSceneFollowsSection: where the scene lands -----------------

    @Test
    fun `the scene is emitted after Earlier's own rows`() {
        assertTrue(earlierSceneFollowsSection(EARLIER_SECTION_KEY))
    }

    @Test
    fun `no other section carries the scene`() {
        // Today's own three time-of-day buckets and the day buckets
        // All/Priority/List build. Every one of these is filtered to nothing
        // while the scene is up (see TodoTimelineSectionsTest's "surfaces just
        // Earlier" cases) -- except during a live reschedule drag, which
        // restores them. That is the case this assertion is really for: the
        // scene is emitted from inside the section loop, so a drag can never
        // strand it below a run of restored empty headers the way an emission
        // after the loop would.
        for (key in listOf(
            "today-morning",
            "today-afternoon",
            "today-tonight",
            "day-2026-09-11",
            "day-2026-09-12",
        )) {
            assertFalse("$key must not carry the scene", earlierSceneFollowsSection(key))
        }
    }

    // --- shouldShowEarlierScene: the hand-off, both directions -------------

    @Test
    fun `collapsed Earlier over an empty scope shows the scene`() {
        assertTrue(showEarlierScene(earlierCollapsed = true))
    }

    @Test
    fun `expanding Earlier takes the scene away on the same frame the rows arrive`() {
        // The entire sequencing argument, and the reason the retired
        // exit-before-expand beat is not merely unused but impossible: the flag
        // that releases Earlier's rows is `collapsedSectionKeys` losing
        // "earlier", and it is the same flag this function reads. There is no
        // intermediate state -- no `earlierExpandPending` to hold the section
        // shut for one exit's length -- so the rows' entrance and the scene's
        // exit begin on one frame and run together, which is what "the image
        // goes down and fades away AS the overdue list expands" means.
        assertFalse(showEarlierScene(earlierCollapsed = false))
    }

    @Test
    fun `collapsing Earlier again brings the scene back, with no beat of its own`() {
        // The same pair read backwards. A collapse used to be the one direction
        // that had no hand-off at all; now both directions are the same single
        // boolean, so neither can acquire a wait the other does not have.
        assertTrue(showEarlierScene(earlierCollapsed = true))
        assertFalse(showEarlierScene(earlierCollapsed = false))
    }

    @Test
    fun `every other gate still closes it independently of the collapse state`() {
        assertFalse(
            "no overdue tasks means no Earlier section to sit under",
            showEarlierScene(earlierCollapsed = true, scopeHasEarlierItems = false),
        )
        assertFalse(
            "a scope with pending work of its own is not empty",
            showEarlierScene(earlierCollapsed = true, scopeItemsEmpty = false),
        )
        assertFalse(
            "a scope with no answer yet has not finished saying what it holds",
            showEarlierScene(earlierCollapsed = true, answer = FeedAnswer.AwaitingFirst),
        )
        assertFalse(
            "Today's first paint suppresses the timeline entirely",
            showEarlierScene(earlierCollapsed = true, suppressInitialTimeline = true),
        )
        assertFalse(
            "a live query owns the body; its own no-results scene says what was searched",
            showEarlierScene(earlierCollapsed = true, scopedSearchActive = true),
        )
    }

    @Test
    fun `a refresh cannot withdraw Earlier's scene either, and a first load still can`() {
        // The same term swap as on the Anytime home, pinned here because this is
        // [shouldShowEarlierScene]'s own file. `!isLoading` used to stand where
        // `answer` does, and `isLoading` is raised by nothing but a pull or a
        // Retry -- so the gesture asking the app to re-check its answer was the
        // term that withdrew it.
        assertTrue(
            "a settled, answered, empty scope keeps its scene through a refresh",
            showEarlierScene(
                earlierCollapsed = true,
                answer = feedAnswer(
                    storeRead = true,
                    rowsEmpty = true,
                    firstAnswerLanded = true,
                ),
            ),
        )
        // ...and the overshoot, which is the half that is easy to lose: a fresh
        // install hydrates instantly and hydrates empty, so the scene must still
        // be withheld until the first answer actually lands.
        assertFalse(
            "a scope whose first answer has not arrived has nothing to illustrate",
            showEarlierScene(
                earlierCollapsed = true,
                answer = feedAnswer(
                    storeRead = true,
                    rowsEmpty = true,
                    firstAnswerLanded = false,
                ),
            ),
        )
        // Local Mode used to be restated here as a third assertion with
        // `firstAnswerLanded = true` written in by hand, which is the first
        // assertion above with a different sentence over it. The claim is about
        // how that term is PRODUCED, so it is made in `FirstAnswerSignalTest`.
    }

    private fun showEarlierScene(
        earlierCollapsed: Boolean,
        scopeHasEarlierItems: Boolean = true,
        scopeItemsEmpty: Boolean = true,
        answer: FeedAnswer = FeedAnswer.Empty,
        suppressInitialTimeline: Boolean = false,
        scopedSearchActive: Boolean = false,
    ): Boolean = shouldShowEarlierScene(
        scopeHasEarlierItems = scopeHasEarlierItems,
        scopeItemsEmpty = scopeItemsEmpty,
        answer = answer,
        suppressInitialTimeline = suppressInitialTimeline,
        scopedSearchActive = scopedSearchActive,
        earlierCollapsed = earlierCollapsed,
    )
}
