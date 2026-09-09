package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [decideSectionHeaderToggleAction] and [shouldShowTodayEarlierExpandedCelebration]
 * were written for Today's Earlier section ([TodayEarlierEmptyStateGatingTest]
 * pins that), but neither one actually looks at the mode: every parameter is
 * a plain boolean the caller derives however its own scope needs to. Extending
 * empty-state/Earlier parity to Scheduled/Priority/All/List means
 * [TodoListScreen] now feeds these same two functions its generalized
 * `scopeHasEarlierItems`/`scopeItemsEmpty`/`showEarlierIllustration` instead
 * of Today-only values (see `nonEarlierSectionsEmpty`) -- these tests pin
 * that the reuse is sound by exercising the identical functions under an
 * explicitly non-Today framing, so a future change that quietly re-couples
 * either one to Today alone would fail here even if every Today test still
 * passed.
 */
class EarlierSectionSequencingParityTest {

    @Test
    fun `Priority -- the first tap on the illustrated collapsed Earlier header defers the expand`() {
        // Priority's own `scopeHasEarlierItems`/`scopeItemsEmpty` are what
        // fed `showEarlierIllustration` true here in the real composable --
        // this function has no way to tell that apart from Today's.
        assertEquals(
            SectionHeaderToggleAction.DEFER_EARLIER_EXPAND,
            decideSectionHeaderToggleAction(
                key = EARLIER_SECTION_KEY,
                wasCollapsed = true,
                showTodayEarlierIllustration = true,
                earlierExpandPending = false,
            ),
        )
    }

    @Test
    fun `All -- collapsing an already-expanded Earlier is still an immediate toggle`() {
        assertEquals(
            SectionHeaderToggleAction.IMMEDIATE_TOGGLE,
            decideSectionHeaderToggleAction(
                key = EARLIER_SECTION_KEY,
                wasCollapsed = false,
                showTodayEarlierIllustration = false,
                earlierExpandPending = false,
            ),
        )
    }

    @Test
    fun `List -- completing the last non-overdue task while Earlier is already expanded still celebrates`() {
        // The scenario `shouldShowTodayEarlierExpandedCelebration` exists
        // for: the illustration never shows collapsed-Earlier's slot because
        // Earlier is not collapsed, so without this the completion that
        // emptied a custom list's own tasks -- with Earlier already open --
        // would draw no confetti at all.
        assertTrue(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                isLoading = false,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    @Test
    fun `Scheduled -- with no Earlier concept at all, the expanded-celebration gap never applies`() {
        // Scheduled never has an Earlier section (see
        // `NonEarlierSectionsEmptyTest`/`TodoTimelineSectionsTest`), so its
        // `scopeHasEarlierItems` is always false and this returns false
        // immediately -- Scheduled falls through to the same plain
        // full-screen empty-state overlay it always has.
        assertEquals(
            false,
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = false,
                itemsEmpty = true,
                isLoading = false,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }
}
