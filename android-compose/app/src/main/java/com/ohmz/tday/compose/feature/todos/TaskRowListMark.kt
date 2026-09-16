package com.ohmz.tday.compose.feature.todos

/**
 * Whether a task row should draw its list's glyph as a trailing mark.
 *
 * The mark answers one question — "which list is this task in?" — and it is worth
 * drawing exactly where the screen has not already answered it. On Today, Overdue,
 * Scheduled, Priority, All and the Anytime HOME feed a row could have come from any
 * list, so the mark is the only thing that says. On a list DETAIL screen every row
 * was filtered to one list before it was composed (TodoRepository filters on
 * `listId` for both `LIST` and `FLOATER`-with-a-listId), so the mark repeats the
 * screen's own title once per row, forever, and says nothing either time.
 *
 * Expressed as scope rather than as a mode table on purpose. `TodoListMode.FLOATER`
 * serves TWO screens — the Anytime home feed (no `listId`) and one Anytime list's
 * detail (a `listId`) — so a `when (mode)` physically cannot separate them, which is
 * the defect the user reported: the mark was suppressed on `LIST` and kept on both
 * halves of `FLOATER`. Comparing the row's list against the screen's scope subsumes
 * the old `LIST -> false` branch rather than sitting beside it, so the two detail
 * screens stop being two cases.
 *
 * It also keeps the one case that must survive: a row whose list is NOT the scoped
 * one still draws its mark. That is what a feed looks like for the frame between a
 * task being moved to another list and the list query catching up, and on that frame
 * the mark is the most informative thing on the row.
 *
 * @param rowListId the list this task belongs to, or null for an unfiled task.
 * @param scopedListId the list the SCREEN is already showing, or null/blank on a
 *   mixed feed that spans every list.
 */
internal fun shouldShowListMark(rowListId: String?, scopedListId: String?): Boolean {
    if (rowListId.isNullOrBlank()) return false
    if (scopedListId.isNullOrBlank()) return true
    return rowListId != scopedListId
}
