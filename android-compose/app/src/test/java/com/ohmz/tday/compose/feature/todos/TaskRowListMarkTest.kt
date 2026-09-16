package com.ohmz.tday.compose.feature.todos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the per-row list mark is drawn, asserted without a screen.
 *
 * There is no emulator here, so "the mark is gone from the Anytime list detail" is not
 * a claim a test in this repo can make by looking. What it can do is what the rest of
 * the tree does with unprovable-locally claims: pull the decision out into a pure
 * function and pin the function. The rule below IS the rendering condition — the row
 * and the drag preview both call it and hold no second opinion — so a table over its
 * inputs is a table over what the eight screens draw.
 *
 * `showListIndicator` had no test at all before this, which is how a `when (mode)`
 * could keep answering "yes" for two different screens that share a mode for as long
 * as it did.
 */
class TaskRowListMarkTest {

    private val groceries = "list-groceries"
    private val work = "list-work"

    @Test
    fun `a mixed feed marks every row that has a list`() {
        // Today, Overdue, Scheduled, Priority, All and the Anytime HOME feed all arrive
        // here as "no scope": the row could have come from anywhere, so the mark is the
        // only thing on the row that says where.
        assertTrue(shouldShowListMark(rowListId = groceries, scopedListId = null))
        assertTrue(shouldShowListMark(rowListId = work, scopedListId = null))
        // A blank id is the same statement as a null one. `uiState.listId` is "" on some
        // of these screens and null on others, and that distinction has never meant
        // anything to anybody.
        assertTrue(shouldShowListMark(rowListId = groceries, scopedListId = ""))
    }

    @Test
    fun `a list detail does not repeat its own name on every row`() {
        // The reported defect. FLOATER-with-a-listId is the Anytime list detail; LIST is
        // the scheduled one. Both are one list filtered before composition, and both are
        // this line.
        assertFalse(shouldShowListMark(rowListId = groceries, scopedListId = groceries))
    }

    @Test
    fun `a row from another list keeps its mark even on a scoped screen`() {
        // The case that must NOT be swept up with the redundant ones: for the frame
        // between a task being moved out of this list and the query catching up, the
        // row is the one thing on screen that disagrees with the title — and the mark
        // is how the user can tell.
        assertTrue(shouldShowListMark(rowListId = work, scopedListId = groceries))
    }

    @Test
    fun `a task in no list has no mark to draw`() {
        // Unfiled Anytime tasks share the Anytime home feed with listed ones. There is
        // no list, so there is no glyph and no colour — drawing the default inbox here
        // would invent a list the task is not in.
        assertFalse(shouldShowListMark(rowListId = null, scopedListId = null))
        assertFalse(shouldShowListMark(rowListId = "", scopedListId = groceries))
    }
}
