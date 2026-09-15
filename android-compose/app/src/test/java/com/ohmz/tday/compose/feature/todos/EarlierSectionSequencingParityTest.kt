package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.ui.FeedAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Earlier hand-off was written for Today ([TodayEarlierEmptyStateGatingTest]
 * pins that), but none of the functions it is made of looks at the mode: every
 * parameter is a plain boolean the caller derives however its own scope needs
 * to. Extending empty-state/Earlier parity to Scheduled/Priority/All/List means
 * [TodoListScreen] feeds those same functions its generalized
 * `scopeHasEarlierItems`/`scopeItemsEmpty` instead of Today-only values (see
 * [nonEarlierSectionsEmpty]). These tests exercise them under an explicitly
 * non-Today framing, so a change that quietly re-couples one of them to Today
 * alone fails here even if every Today test still passes.
 *
 * The first two cases used to assert that a Priority tap on an illustrated,
 * collapsed Earlier header returned `DEFER_EARLIER_EXPAND` -- the
 * exit-before-expand beat. That beat is retired, and the cases are rewritten
 * rather than dropped, because what they were really protecting is still worth
 * protecting: that Earlier's rows and the empty-state scene never occupy the
 * screen at the same time in a way that reads as two states at once. The beat
 * bought that by serialising them. The order buys it instead -- the scene sits
 * below Earlier's rows, so the rows growing downward out of a header that does
 * not move and the scene sinking and fading beneath them ARE one motion, and a
 * single boolean starts both. See [shouldShowEarlierScene] and
 * [earlierSceneFollowsSection].
 *
 * Worth recording here, since this file's name promises parity: Android is now
 * the only client without the beat, deliberately. Web's
 * `useEarlierExpandHandoff` and iOS's
 * `toggleEarlierSectionWithIllustrationHandoff` both still sequence, because on
 * both of those clients the scene is drawn OVER the list rather than in it and
 * rows expanding in would arrive underneath a scene still painting. Android's
 * scene is a lazy item that shares no pixels with the rows once it is below
 * them. The requirement is the same on all three; only Android's geometry stopped
 * needing a beat to meet it.
 */
class EarlierSectionSequencingParityTest {

    @Test
    fun `Priority -- the tap that expands an illustrated Earlier needs no beat in front of it`() {
        // Priority's own `scopeHasEarlierItems`/`scopeItemsEmpty` are what fed
        // the scene true here in the real composable; these functions have no
        // way to tell that apart from Today's. Before: this tap returned
        // DEFER_EARLIER_EXPAND and nothing happened for 150 ms. Now the same tap
        // flips one boolean and both halves move together.
        assertTrue(showEarlierScene(collapsed = true))
        assertFalse(showEarlierScene(collapsed = false))
    }

    @Test
    fun `All -- collapsing an already-expanded Earlier runs the same pair backwards`() {
        // The direction that never had a hand-off at all. It gets the same one
        // the expand gets, for free, because there is only one decision left.
        assertEquals(showEarlierScene(collapsed = true), !showEarlierScene(collapsed = false))
    }

    @Test
    fun `List -- the scene is emitted under Earlier's rows, never under a day bucket`() {
        // A custom list builds Earlier first and its day buckets after it
        // (`placesEarlierBeforeToday`), so "after the sections" and "after
        // Earlier" are different places here the moment a live drag restores
        // those buckets. The scene follows the section, not the loop.
        assertTrue(earlierSceneFollowsSection(EARLIER_SECTION_KEY))
        assertFalse(earlierSceneFollowsSection("day-2026-09-12"))
    }

    @Test
    fun `List -- completing the last non-overdue task while Earlier is already expanded still celebrates`() {
        // The scenario `shouldShowTodayEarlierExpandedCelebration` exists for:
        // the collapsed-Earlier scene never shows, because Earlier is not
        // collapsed, so without this the completion that emptied a custom
        // list's own tasks -- with Earlier already open -- would draw no
        // confetti at all. Unchanged by the reorder except in where the burst
        // lands: at the foot of the open overdue list rather than above it,
        // which is the one cost the new order charges and is argued at the
        // scene's own call site.
        assertTrue(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                answer = FeedAnswer.Empty,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    @Test
    fun `Scheduled -- with no Earlier concept at all, none of this applies`() {
        // Scheduled never has an Earlier section (see
        // `NonEarlierSectionsEmptyTest`/`TodoTimelineSectionsTest`), so its
        // `scopeHasEarlierItems` is always false: no scene to place, and the
        // expanded-celebration gap never opens. It falls through to the same
        // plain full-screen empty-state overlay it always has.
        assertFalse(showEarlierScene(collapsed = true, scopeHasEarlierItems = false))
        assertEquals(
            false,
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = false,
                itemsEmpty = true,
                answer = FeedAnswer.Empty,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    private fun showEarlierScene(
        collapsed: Boolean,
        scopeHasEarlierItems: Boolean = true,
    ): Boolean = shouldShowEarlierScene(
        scopeHasEarlierItems = scopeHasEarlierItems,
        scopeItemsEmpty = true,
        answer = FeedAnswer.Empty,
        suppressInitialTimeline = false,
        scopedSearchActive = false,
        earlierCollapsed = collapsed,
    )
}
