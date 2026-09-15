package com.ohmz.tday.compose.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dismissal decision, pinned where no device is needed to read it.
 *
 * "An open row closes when you touch anything else" is a screen-wide behaviour
 * assembled from a pointer observer, a scroll collector, a back handler and four
 * gesture handlers, none of which this repository's gates can drive — there is no
 * Robolectric and no Compose harness on this source set. What every one of those
 * paths funnels into is a single write to [TaskSwipeSlot.openId] and a single
 * question asked by each row, and that question is [shouldCloseSwipeRow]. Pin the
 * question and the paths above it become plumbing.
 *
 * The same function, the same name and the same truth table exist on iOS and on
 * web, which is the other half of the reason it is a function at all: three
 * clients left to answer "what does anywhere else mean" in three gesture
 * handlers would drift, and nothing would notice until someone used two of them.
 */
class TaskSwipeDismissPolicyTest {

    @Test
    fun `an empty slot revokes - this is the case the old guard got wrong`() {
        // The regression this whole file exists for. Every row used to ask
        // `openSwipeTaskId != null && openSwipeTaskId != id && isOpen`, so the
        // slot could be handed from one row to another but never taken back:
        // writing `null` closed nothing at all. Every dismissal the app now
        // performs — an outside tap, a scroll, back, entering bulk selection —
        // is a write of `null`, so under that guard every one of them would have
        // been a silent no-op.
        //
        // The proof it was load-bearing was already in the tree as a workaround:
        // opening bulk selection wrote `null` and then closed the row a second
        // time through a separate `LaunchedEffect(selectionActive)`, because the
        // write on its own did nothing. That second effect is gone with this.
        assertTrue(
            shouldCloseSwipeRow(
                openRowId = null,
                thisRowId = "a",
                isOpenOrDragging = true,
            ),
        )
    }

    @Test
    fun `another row holding the slot closes this one - one row open at a time`() {
        assertTrue(
            shouldCloseSwipeRow(
                openRowId = "b",
                thisRowId = "a",
                isOpenOrDragging = true,
            ),
        )
    }

    @Test
    fun `a row never closes itself out from under its own finger`() {
        // The single most important negative case. The row holding the slot is
        // the row the user is working, and an interceptor that shut it would be
        // taking the row away mid-gesture. The outside-tap modifier's 8 dp slop
        // test is the other half of this guarantee — a drag of the open row, in
        // either direction, is invisible to it.
        assertFalse(
            shouldCloseSwipeRow(
                openRowId = "a",
                thisRowId = "a",
                isOpenOrDragging = true,
            ),
        )
    }

    @Test
    fun `a closed row has nothing to close, whoever holds the slot`() {
        // Not merely redundant: `close()` on a row already home would start a
        // release spring to where the row already is. Most rows in a feed of
        // hundreds are in exactly this state, and every one of them is asked.
        assertFalse(
            shouldCloseSwipeRow(
                openRowId = null,
                thisRowId = "a",
                isOpenOrDragging = false,
            ),
        )
        assertFalse(
            shouldCloseSwipeRow(
                openRowId = "b",
                thisRowId = "a",
                isOpenOrDragging = false,
            ),
        )
        assertFalse(
            shouldCloseSwipeRow(
                openRowId = "a",
                thisRowId = "a",
                isOpenOrDragging = false,
            ),
        )
    }

    @Test
    fun `a disclaim from a row that does not hold the slot leaves it alone`() {
        // The narrow revoke, and the reason it is a named function rather than
        // four inline `if`s. A row handing its OWN slot back must not shut a
        // different row: that other row is the one the user is working.
        assertEquals(
            "b",
            swipeSlotAfterRowDisclaim(openRowId = "b", thisRowId = "a"),
        )
        assertNull(swipeSlotAfterRowDisclaim(openRowId = "a", thisRowId = "a"))
        assertNull(swipeSlotAfterRowDisclaim(openRowId = null, thisRowId = "a"))
    }

    @Test
    fun `the long-press drag is the revoke this one must not be used for`() {
        // The bug this pins, stated as the assertion that would have caught it.
        // Starting a drag-to-reschedule on row "a" while row "b" is open is the
        // ordinary case — the open row is almost never the row being picked up —
        // and routing that through the disclaim above returns "b": the slot
        // survives, and an armed Delete pill rides under the user's thumb for
        // the length of the drag. The drag start therefore writes `null` at the
        // call site, unconditionally, the way iOS's `beginInAppDrag` does. If
        // someone ever "tidies" that into `closeSwipeSlot()`, this row says what
        // they have just done.
        assertEquals(
            "b",
            swipeSlotAfterRowDisclaim(openRowId = "b", thisRowId = "a"),
        )
        // And what the drag start actually writes, which closes "b" through
        // `shouldCloseSwipeRow` on "b"'s own collector.
        assertTrue(
            shouldCloseSwipeRow(
                openRowId = null,
                thisRowId = "b",
                isOpenOrDragging = true,
            ),
        )
    }
}
