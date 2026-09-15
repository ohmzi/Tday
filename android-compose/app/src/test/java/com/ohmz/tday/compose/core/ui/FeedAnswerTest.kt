package com.ohmz.tday.compose.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The one decision every empty-state gate on this client now defers to, pinned
 * as a pure function for the reason `shouldCelebrateEmptyState` and
 * `nonEarlierSectionsEmpty` are: there is no device on this machine, the rule is
 * about a transition rather than a pixel, and the shape it must NOT have is
 * easier to write by accident than the shape it must.
 *
 * That wrong shape is `!isLoading`, and it was written by accident in seven
 * places. The user's report is what it costs: pull the Anytime home down with no
 * tasks in it and the illustration, the heading and the body all go, the page
 * collapses upward, and the whole block comes back when the refresh returns with
 * nothing new. A flash of absence for a refresh that changed nothing.
 *
 * Both directions are asserted here, and the second is the one to perturb first
 * when reading this file. Making the empty state survive a refresh is easy; the
 * way to overshoot it is to make the empty state unconditional, and telling
 * someone "you have no tasks" while their first sync is still in flight is a
 * worse bug than the one being fixed.
 */
class FeedAnswerTest {

    @Test
    fun `an answer already on screen is not withdrawn, because no input a refresh moves is in it`() {
        // THE REPORTED BUG. There is no refresh parameter to vary -- the signature
        // is the assertion -- so what is stated instead is the consequence: a
        // hydrated, empty, answered feed is EMPTY, and stays EMPTY, because the
        // only values a pull changes on any of these screens (`isLoading`,
        // `isRefreshing`) have nowhere to enter.
        assertEquals(
            FeedAnswer.Empty,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = true),
        )
        // Bound as a typed reference so that re-admitting a loading flag to the
        // signature stops compiling this file rather than quietly passing it.
        val decide: (Boolean, Boolean, Boolean) -> FeedAnswer = ::feedAnswer
        assertEquals(FeedAnswer.Empty, decide(true, true, true))
    }

    @Test
    fun `the empty state is still withheld before the first answer exists`() {
        // The overshoot, in one line. A fresh install's cache read lands
        // immediately and lands empty, so `storeRead` on its own says "hydrated"
        // about a device that has never heard from its workspace.
        assertEquals(
            FeedAnswer.AwaitingFirst,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = false),
        )
        assertNotEquals(
            FeedAnswer.Empty,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = false),
        )
    }

    @Test
    fun `a cache read that has not landed has nothing to say either`() {
        // The other half of AWAITING_FIRST, and the half that predates this fix:
        // `suppressInitialTodayTimeline` in `TodoListScreen` is literally this
        // term for Today, which is what made `hasHydratedSnapshot` the right field
        // to build on rather than a new flag to invent.
        assertEquals(
            FeedAnswer.AwaitingFirst,
            feedAnswer(storeRead = false, rowsEmpty = true, firstAnswerLanded = false),
        )
        assertEquals(
            FeedAnswer.AwaitingFirst,
            feedAnswer(storeRead = false, rowsEmpty = true, firstAnswerLanded = true),
        )
    }

    @Test
    fun `an empty Local Mode workspace is answered from its first frame`() {
        // The regression a sync-stamp-only first-answer term would have caused,
        // and the reason `FirstAnswerSignal` reads `isLocalMode()` first. Local
        // Mode has no server and never records a successful sync, so a stamp used
        // alone is false there forever: an empty local workspace would show a row
        // skeleton that never resolves and never show the empty state at all.
        // `isLocalMode` makes `firstAnswerLanded` true from the first frame, which
        // is this case.
        assertEquals(
            FeedAnswer.Empty,
            feedAnswer(storeRead = true, rowsEmpty = true, firstAnswerLanded = true),
        )
    }

    @Test
    fun `an arrival still takes the empty state away`() {
        // The fix must not be "hold the picture no matter what". `rowsEmpty` is a
        // term, so a task landing moves the answer on the frame it lands, both
        // before a first sync and after one.
        assertEquals(
            FeedAnswer.Populated,
            feedAnswer(storeRead = true, rowsEmpty = false, firstAnswerLanded = true),
        )
        assertEquals(
            FeedAnswer.Populated,
            feedAnswer(storeRead = true, rowsEmpty = false, firstAnswerLanded = false),
        )
    }

    @Test
    fun `every combination resolves to exactly one of the three states`() {
        // A truth table rather than a sampling, because eight rows is small enough
        // to write out and the whole claim of this function is that there are only
        // three answers and no fourth "we are busy" one hiding in the middle.
        val expected = mapOf(
            Triple(false, false, false) to FeedAnswer.AwaitingFirst,
            Triple(false, false, true) to FeedAnswer.AwaitingFirst,
            Triple(false, true, false) to FeedAnswer.AwaitingFirst,
            Triple(false, true, true) to FeedAnswer.AwaitingFirst,
            Triple(true, false, false) to FeedAnswer.Populated,
            Triple(true, false, true) to FeedAnswer.Populated,
            Triple(true, true, false) to FeedAnswer.AwaitingFirst,
            Triple(true, true, true) to FeedAnswer.Empty,
        )
        for ((inputs, answer) in expected) {
            val (storeRead, rowsEmpty, firstAnswerLanded) = inputs
            assertEquals(
                "storeRead=$storeRead rowsEmpty=$rowsEmpty firstAnswerLanded=$firstAnswerLanded",
                answer,
                feedAnswer(
                    storeRead = storeRead,
                    rowsEmpty = rowsEmpty,
                    firstAnswerLanded = firstAnswerLanded,
                ),
            )
        }
    }
}
