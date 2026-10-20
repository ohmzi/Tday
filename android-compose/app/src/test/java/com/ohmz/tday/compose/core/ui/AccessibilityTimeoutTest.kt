package com.ohmz.tday.compose.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app does with the number `getRecommendedTimeoutMillis` hands back.
 *
 * The platform call itself is not testable off a device — it reads a user-level setting
 * through a system service — so the arithmetic either side of it is split out and pinned
 * here, which is the half that can be wrong in a way nobody would notice. A window that
 * silently stayed at eight seconds for a user who asked for thirty is the bug this unit
 * exists for, and it looks exactly like a working app from the outside.
 *
 * The recommendations below are the real ladder: Android's "Time to take action" offers
 * default, 10s, 30s, 1 minute and 2 minutes, and an accessibility service can ask for a
 * number so large it means never.
 */
class AccessibilityTimeoutTest {

    /** An accessibility service asking for effectively-never, in milliseconds. */
    private val never = Int.MAX_VALUE.toLong()

    /** The dock's own base, from `RootFeedDock`: the second surface asking this question. */
    private val dockBase = 2_400L

    @Test
    fun `a device with nothing set behaves exactly as it always did`() {
        // Every window in the app goes through this now, so "no setting" has to be an
        // identity rather than merely close to one — the framework returns our own
        // number back when the user has expressed no preference.
        assertEquals(
            TOAST_AUTO_DISMISS_SHORT_MS,
            timeoutOrNever(TOAST_AUTO_DISMISS_SHORT_MS, TOAST_AUTO_DISMISS_SHORT_MS),
        )
        assertEquals(
            TOAST_AUTO_DISMISS_WITH_ACTION_MS,
            boundedTimeout(TOAST_AUTO_DISMISS_WITH_ACTION_MS, TOAST_AUTO_DISMISS_WITH_ACTION_MS),
        )
        assertEquals(dockBase, boundedTimeout(dockBase, dockBase))
    }

    @Test
    fun `the settings ladder is honoured value for value`() {
        // Not rounded to the nearest anything and not capped short: a user who asked for
        // thirty seconds is asking for thirty seconds, and every option the platform's
        // own screen offers is below the ceiling. The dock is in here because its base is
        // the smallest in the app — 2.4s — and a floor that only ever grew the toast's
        // 8s would still leave the dock's user with a control that runs away from them.
        for (asked in listOf(10_000L, 30_000L, 60_000L, 120_000L)) {
            assertEquals(asked, boundedTimeout(asked, TOAST_AUTO_DISMISS_WITH_ACTION_MS))
            assertEquals(asked, boundedTimeout(asked, dockBase))
            assertEquals(asked, timeoutOrNever(asked, TOAST_AUTO_DISMISS_SHORT_MS))
        }
    }

    @Test
    fun `a recommendation shorter than ours never shortens the window`() {
        // The framework returns max(ours, theirs) and so cannot produce this. The floor
        // is for the build that does not: this code path exists to give somebody MORE
        // time, and there is no reading of the setting under which it should hand back
        // less than the window everyone else already gets.
        assertEquals(
            TOAST_AUTO_DISMISS_WITH_ACTION_MS,
            boundedTimeout(1_000L, TOAST_AUTO_DISMISS_WITH_ACTION_MS),
        )
        assertEquals(dockBase, boundedTimeout(1_000L, dockBase))
        assertEquals(
            TOAST_AUTO_DISMISS_SHORT_MS,
            timeoutOrNever(0L, TOAST_AUTO_DISMISS_SHORT_MS),
        )
    }

    @Test
    fun `no timeout means a toast that only speaks does not leave on its own`() {
        // Null rather than a very long delay. The difference is visible to the user: a
        // delay of Int.MAX_VALUE milliseconds is a coroutine parked for 24 days, and the
        // toast still cannot be closed by anything the timer knows about. Null is the
        // app agreeing it is not going anywhere, which is what makes TdayToastCard's
        // `dismiss` semantics the way out rather than a redundancy.
        assertNull(timeoutOrNever(never, TOAST_AUTO_DISMISS_SHORT_MS))
    }

    @Test
    fun `no timeout does not make an undo the app cannot keep`() {
        // The offer to put something back is only good while the delete is staged, so
        // "never" is the one answer an interactive window cannot take. It gets the
        // ceiling — fifteen times the window the toast had, and still a number the
        // commit can outlive. The dock lands on the same ceiling for a duller reason:
        // a dock that never closes is only a wide dock, but the timer holding it open
        // is a coroutine, and parking one for 24 days is not a thing to leave lying
        // behind a tab bar.
        val toastWindow = boundedTimeout(never, TOAST_AUTO_DISMISS_WITH_ACTION_MS)
        assertEquals(INTERACTIVE_TIMEOUT_CEILING_MS, toastWindow)
        assertTrue(toastWindow > TOAST_AUTO_DISMISS_WITH_ACTION_MS)
        assertEquals(INTERACTIVE_TIMEOUT_CEILING_MS, boundedTimeout(never, dockBase))
    }

    @Test
    fun `the staged delete outlives the button that undoes it, at every setting`() {
        // This is the invariant the unit is really about. It used to be two hardcoded
        // numbers 500 apart; the moment one of them started following a setting, the
        // eight-and-a-half-second commit would have fired while a thirty-second toast
        // was still offering an Undo that silently did nothing — a button that says the
        // delete was reversed when it was not.
        val expected = mapOf(
            // An untouched device still commits at the 8.5s the sync tests quote.
            TOAST_AUTO_DISMISS_WITH_ACTION_MS to 8_500L,
            10_000L to 10_500L,
            30_000L to 30_500L,
            120_000L to 120_500L,
            // "Never" is the ceiling's window, not the base's.
            never to INTERACTIVE_TIMEOUT_CEILING_MS + UNDO_COMMIT_GRACE_MS,
        )
        for ((recommended, commit) in expected) {
            val toast = boundedTimeout(recommended, TOAST_AUTO_DISMISS_WITH_ACTION_MS)
            assertEquals("recommendation $recommended", commit, undoCommitDelayFor(toast))
            assertTrue("recommendation $recommended", undoCommitDelayFor(toast) > toast)
        }
    }
}
