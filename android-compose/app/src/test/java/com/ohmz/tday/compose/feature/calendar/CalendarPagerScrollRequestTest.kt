package com.ohmz.tday.compose.feature.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The calendar header disables both chevrons while a scroll request is outstanding, and the only
 * thing that ever clears one is the handled callback. That makes this callback a control, not a
 * notification: miss it once and the user is left looking at two arrows that no longer respond,
 * with no gesture anywhere in the app that brings them back.
 *
 * There is no Robolectric and no Compose test harness on this source set, so these tests drive the
 * request through the same function the pager's `LaunchedEffect` calls, with the pager's scroll
 * supplied as a lambda. What that lambda cannot fake is the mutex; what it can fake, and what
 * matters here, is the shape of the failure — a scroll that is cancelled where it suspends rather
 * than returning.
 */
class CalendarPagerScrollRequestTest {

    @Test
    fun `a slide cancelled mid-flight still reports its request handled`() = runTest {
        val animationStarted = CompletableDeferred<Unit>()
        val handled = mutableListOf<Int>()

        val slide = launch {
            runCalendarPagerScrollRequest(
                requestId = 41,
                targetPage = 3,
                currentPage = { 2 },
                animateToPage = {
                    animationStarted.complete(Unit)
                    // Stands in for `animateScrollToPage` losing the pager's scroll mutex to a
                    // finger: it holds the coroutine at a suspension point and is cancelled there,
                    // so it never returns normally and never runs the line after itself.
                    awaitCancellation()
                },
                onHandled = { id -> handled += id },
            )
        }

        animationStarted.await()
        slide.cancelAndJoin()

        // Before the `finally`, the CancellationException unwound straight past the report and this
        // list stayed empty — which on screen is `scrollRequest` stuck non-null and both chevrons
        // dead for the rest of the session.
        assertEquals(listOf(41), handled)
    }

    @Test
    fun `a slide that lands reports its request handled exactly once`() = runTest {
        val scrolledTo = mutableListOf<Int>()
        val handled = mutableListOf<Int>()

        runCalendarPagerScrollRequest(
            requestId = 7,
            targetPage = 5,
            currentPage = { 4 },
            animateToPage = { page -> scrolledTo += page },
            onHandled = { id -> handled += id },
        )

        assertEquals(listOf(5), scrolledTo)
        assertEquals(listOf(7), handled)
    }

    @Test
    fun `a request for the page already on screen reports handled without animating`() = runTest {
        var animations = 0
        val handled = mutableListOf<Int>()

        // The Today jump lands here whenever the target week is already the visible one. It still
        // has to release the chevrons, or a jump to a day in the current week disables them.
        runCalendarPagerScrollRequest(
            requestId = 9,
            targetPage = 2,
            currentPage = { 2 },
            animateToPage = { animations += 1 },
            onHandled = { id -> handled += id },
        )

        assertEquals(0, animations)
        assertEquals(listOf(9), handled)
    }
}
