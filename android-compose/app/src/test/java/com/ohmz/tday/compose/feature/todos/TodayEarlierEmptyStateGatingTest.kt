package com.ohmz.tday.compose.feature.todos

import com.ohmz.tday.compose.core.ui.FeedAnswer
import com.ohmz.tday.compose.core.ui.feedAnswer
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
 *    exit-before-expand beat a first tap scheduled, used to fall through to
 *    an immediate expand and race Earlier's rows in underneath the scene's
 *    still-playing exit. Those cases have moved to [EarlierSceneOrderTest]
 *    along with the finding itself: the beat is retired, because the scene
 *    is emitted BELOW Earlier's rows now and the two no longer contest a
 *    slot, so there is no window for a second tap to land inside. What is
 *    left to pin is that one boolean decides both halves, which is
 *    [shouldShowEarlierScene]'s own file.
 *  - Moving the scene below Earlier's rows then reopened the first finding
 *    from the other end: the flag still turned the scene on, but with Earlier
 *    expanded the item it turns on sits past the fold, a `LazyColumn` does not
 *    compose what it has not reached, and `TdayConfetti`'s flight starts from
 *    a `LaunchedEffect` inside it -- no illustration and no confetti again, for
 *    exactly the backlog the flag was written for. See
 *    [shouldFoldEarlierForCelebration].
 */
class TodayEarlierEmptyStateGatingTest {

    // --- shouldShowTodayEarlierExpandedCelebration -------------------------

    @Test
    fun `celebrates in Earlier's slot when Earlier is expanded and the tap just emptied Today`() {
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
    fun `stays quiet once the celebration window has closed, handing the slot back to Earlier`() {
        // Same expanded-Earlier, empty-Today shape, but the completion that
        // caused it is no longer recent -- `celebrateEmptyState` has already
        // gone back to false. Nothing should reclaim the slot from Earlier's
        // own rows outside the celebration window.
        assertFalse(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                answer = FeedAnswer.Empty,
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
                answer = FeedAnswer.Empty,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = true,
                celebrateEmptyState = true,
            ),
        )
    }

    @Test
    fun `never fires while Today still has pending tasks, no answer yet, or a live search`() {
        val base = listOf(
            "itemsEmpty",
            "awaitingFirstAnswer",
            "suppressInitialTodayTimeline",
            "scopedSearchActive",
        )
        for (flag in base) {
            assertFalse(
                "unexpected true with $flag forcing the gate closed",
                shouldShowTodayEarlierExpandedCelebration(
                    todayHasEarlierItems = true,
                    itemsEmpty = flag != "itemsEmpty",
                    answer = if (flag == "awaitingFirstAnswer") {
                        FeedAnswer.AwaitingFirst
                    } else {
                        FeedAnswer.Empty
                    },
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
                answer = FeedAnswer.Empty,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    // --- the refresh that used to withdraw all of it ------------------------
    //
    // `!isLoading` stood where `answer` does now, in this gate and in five
    // siblings. `isLoading` is raised by nothing but
    // `refreshInternal(showLoading = true)`, reached only from `refresh()`,
    // called only by the pull lambda and the error card's Retry -- so it has
    // always meant "a refresh over an answer already on screen", which is the one
    // condition these scenes must be held THROUGH. Both directions are pinned,
    // because the second is how this fix is overshot rather than missed.

    @Test
    fun `a refresh over an already-celebrating scope changes nothing here`() {
        // THE REPORTED BUG at this gate. "All done" withdrawn mid-pull is the same
        // flash the user photographed on the Anytime home, with a worse message on
        // it. There is no refresh term left to vary -- what is asserted is that
        // the answer a settled empty scope produces is the answer this gate wants,
        // and that a pull cannot produce a different one (see [FeedAnswerTest]).
        assertEquals(
            FeedAnswer.Empty,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = true),
        )
        assertTrue(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                answer = feedAnswer(
                    storeRead = true,
                    rowsEmpty = true,
                    firstAnswerLanded = true,
                ),
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    @Test
    fun `nothing is celebrated over a scope that has never had an answer`() {
        // The overshoot. A fresh install hydrates instantly and hydrates empty;
        // congratulating someone on finishing a day whose tasks have not arrived
        // is worse than the flash this replaced.
        assertFalse(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                answer = FeedAnswer.AwaitingFirst,
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    @Test
    fun `an empty Local Mode workspace still celebrates`() {
        // Local Mode never records a successful sync, so a first-answer term built
        // on the sync stamp alone would have silenced this gate there forever.
        assertTrue(
            shouldShowTodayEarlierExpandedCelebration(
                todayHasEarlierItems = true,
                itemsEmpty = true,
                answer = feedAnswer(
                    storeRead = true,
                    rowsEmpty = true,
                    // `isLocalMode()` is the whole of this term in Local Mode.
                    firstAnswerLanded = true,
                ),
                suppressInitialTodayTimeline = false,
                scopedSearchActive = false,
                earlierCollapsed = false,
                celebrateEmptyState = true,
            ),
        )
    }

    // --- shouldFoldEarlierForCelebration -----------------------------------
    //
    // The second half of the same finding, and the half the flag above cannot
    // answer on its own. Showing the scene and being able to see it stopped
    // being the same thing when the scene moved below Earlier's rows: the
    // `LazyColumn` never composes an item it has not scrolled to, and the burst
    // is started by a `LaunchedEffect` inside that item, so a tall enough
    // expanded Earlier does not delay the celebration, it deletes it. These pin
    // the fold that pays for it -- once per completion, and never against a user
    // who taps the list back open.

    private val Stamp = 12_345L

    @Test
    fun `folds Earlier for a completion that lands while Earlier is open`() {
        assertTrue(
            shouldFoldEarlierForCelebration(
                showEarlierExpandedCelebration = true,
                celebrationStampMs = Stamp,
                foldedForStampMs = 0L,
            ),
        )
    }

    @Test
    fun `folds once per completion, not once per frame the flag is true`() {
        // The fold makes its own trigger false -- a folded Earlier is a
        // collapsed one, and `shouldShowTodayEarlierExpandedCelebration`
        // requires it expanded. The flag therefore flips back to true the
        // moment the user taps Earlier open again, still inside the window.
        // Keyed on the flag this would fold the list shut under their finger
        // for four seconds; keyed on the stamp their tap wins.
        assertFalse(
            shouldFoldEarlierForCelebration(
                showEarlierExpandedCelebration = true,
                celebrationStampMs = Stamp,
                foldedForStampMs = Stamp,
            ),
        )
    }

    @Test
    fun `the next completion folds again`() {
        assertTrue(
            shouldFoldEarlierForCelebration(
                showEarlierExpandedCelebration = true,
                celebrationStampMs = Stamp + 1,
                foldedForStampMs = Stamp,
            ),
        )
    }

    @Test
    fun `never folds a list nobody is celebrating over`() {
        // Every path that leaves the flag false -- Earlier already collapsed,
        // the scope not empty, the window expired, a search open -- is a path
        // that must not touch the user's own collapse state. The flag is the
        // only gate this needs, because it already carries all of them.
        assertFalse(
            shouldFoldEarlierForCelebration(
                showEarlierExpandedCelebration = false,
                celebrationStampMs = Stamp,
                foldedForStampMs = 0L,
            ),
        )
    }

    @Test
    fun `an unset stamp never folds`() {
        // Belt and braces, and named as such. `celebrateEmptyState` already
        // requires a non-zero stamp, so the flag should never be true here --
        // but 0 is both "nothing has completed" and the initial
        // already-folded value, and a sentinel that means two things is worth
        // one explicit line rather than an inference across two functions.
        assertFalse(
            shouldFoldEarlierForCelebration(
                showEarlierExpandedCelebration = true,
                celebrationStampMs = 0L,
                foldedForStampMs = 0L,
            ),
        )
    }
}
