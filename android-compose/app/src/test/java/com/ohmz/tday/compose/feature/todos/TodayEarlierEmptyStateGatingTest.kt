package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two review findings against Today's "Earlier" empty-state handling, each
 * pinned as a unit test against the pure decision function it named:
 *
 *  - Completing the last pending-today task while Earlier is *expanded*
 *    (not collapsed) drew neither the overlay (`todayHasEarlierItems` defers
 *    it) nor the inline scene (`showTodayEarlierIllustration` requires
 *    Earlier collapsed) -- no illustration and no confetti at all. See
 *    [shouldShowTodayEarlierExpandedCelebration].
 *  - A second tap on Earlier's collapsed header, landing inside the ~150ms
 *    exit-before-expand beat a first tap scheduled, used to read
 *    `showTodayEarlierIllustration` as already false (it flips the moment
 *    `earlierExpandPending` is set, well before the beat's own coroutine
 *    actually collapses the section) and fall through to an immediate
 *    expand, racing Earlier's rows in underneath the scene's still-playing
 *    exit. See [decideSectionHeaderToggleAction].
 */
class TodayEarlierEmptyStateGatingTest {

    // --- shouldShowTodayEarlierExpandedCelebration -------------------------

    @Test
    fun `celebrates in Earlier's slot when Earlier is expanded and the tap just emptied Today`() {
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
    fun `stays quiet once the celebration window has closed, handing the slot back to Earlier`() {
        // Same expanded-Earlier, empty-Today shape, but the completion that
        // caused it is no longer recent -- `celebrateEmptyState` has already
        // gone back to false. Nothing should reclaim the slot from Earlier's
        // own rows outside the celebration window.
        assertFalse(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                isLoading = false,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = false,
            ),
        )
    }

    @Test
    fun `defers to the collapsed illustration instead of double-booking the slot`() {
        // Earlier collapsed is `showTodayEarlierIllustration`'s case, not
        // this one -- the two are meant to be mutually exclusive.
        assertFalse(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                isLoading = false,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = true,
                celebrateEmptyState = true,
            ),
        )
    }

    @Test
    fun `never fires while Today still has pending tasks, loading, or a live search`() {
        val base = mapOf(
            "itemsEmpty" to false,
            "isLoading" to true,
            "suppressInitialTodayTimeline" to true,
            "scopedSearchActive" to true,
        )
        for (flag in base.keys) {
            assertFalse(
                "unexpected true with $flag forcing the gate closed",
                shouldShowTodayEarlierExpandedCelebration(
                    todayHasEarlierItems = true,
                    itemsEmpty = flag != "itemsEmpty",
                    isLoading = flag == "isLoading",
                    suppressInitialTodayTimeline = flag == "suppressInitialTodayTimeline",
                    scopedSearchActive = flag == "scopedSearchActive",
                    earlierCollapsed = false,
                    celebrateEmptyState = true,
                ),
            )
        }
    }

    @Test
    fun `never fires when Earlier itself holds nothing`() {
        assertFalse(
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

    // --- decideSectionHeaderToggleAction -------------------------------

    @Test
    fun `a tap while an earlier tap's exit beat is still running is ignored`() {
        // The exact race: `earlierExpandPending` is true (a first tap's beat
        // is running), which -- in the real composable -- has already flipped
        // `showTodayEarlierIllustration` to false on the recomposition that
        // followed. Passing that already-false value in here is the point:
        // IGNORE must win on `earlierExpandPending` alone, not by re-deriving
        // whether the scene is still visible.
        assertEquals(
            SectionHeaderToggleAction.IGNORE,
            decideSectionHeaderToggleAction(
                key = "earlier",
                wasCollapsed = true,
                showTodayEarlierIllustration = false,
                earlierExpandPending = true,
            ),
        )
    }

    @Test
    fun `the first tap on the illustrated collapsed header defers the expand`() {
        assertEquals(
            SectionHeaderToggleAction.DEFER_EARLIER_EXPAND,
            decideSectionHeaderToggleAction(
                key = "earlier",
                wasCollapsed = true,
                showTodayEarlierIllustration = true,
                earlierExpandPending = false,
            ),
        )
    }

    @Test
    fun `collapsing an already-expanded Earlier is an immediate toggle`() {
        assertEquals(
            SectionHeaderToggleAction.IMMEDIATE_TOGGLE,
            decideSectionHeaderToggleAction(
                key = "earlier",
                wasCollapsed = false,
                showTodayEarlierIllustration = false,
                earlierExpandPending = false,
            ),
        )
    }

    @Test
    fun `expanding a collapsed Earlier with no illustration showing is immediate too`() {
        // Earlier can be collapsed without the illustration showing -- Today
        // still has pending tasks, say -- and that ordinary case must not be
        // swept into the defer branch just because the key matches.
        assertEquals(
            SectionHeaderToggleAction.IMMEDIATE_TOGGLE,
            decideSectionHeaderToggleAction(
                key = "earlier",
                wasCollapsed = true,
                showTodayEarlierIllustration = false,
                earlierExpandPending = false,
            ),
        )
    }

    @Test
    fun `other sections are never routed through Earlier's special cases`() {
        assertEquals(
            SectionHeaderToggleAction.IMMEDIATE_TOGGLE,
            decideSectionHeaderToggleAction(
                key = "today-morning",
                wasCollapsed = true,
                showTodayEarlierIllustration = true,
                earlierExpandPending = true,
            ),
        )
    }
}
