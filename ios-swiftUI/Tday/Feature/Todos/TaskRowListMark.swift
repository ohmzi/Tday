import Foundation

/// Whether a task row should draw its list's glyph as a trailing mark.
///
/// The mark answers one question — "which list is this task in?" — and it is worth drawing
/// exactly where the screen has not already answered it. On Today, Overdue, Scheduled,
/// Priority, All and the Anytime HOME feed a row could have come from any list, so the mark
/// is the only thing that says. On a list DETAIL screen every row was filtered to one list
/// before it was composed, so the mark repeats the screen's own title once per row, forever,
/// and says nothing either time.
///
/// Expressed as scope rather than as a mode test on purpose. `TodoListMode.floater` serves
/// TWO screens — the Anytime home feed (`AppRootView` builds it with `listId: nil`) and one
/// Anytime list's detail (`floaterListTodos` passes the id) — so `mode != .list`, the test
/// this replaces, physically cannot separate them. That is the defect the user reported: the
/// mark was suppressed on `.list` and kept on both halves of `.floater`. Comparing the row's
/// list against the screen's scope subsumes the old `.list` case rather than sitting beside
/// it, so the two detail screens stop being two cases.
///
/// It also keeps the one case that must survive: a row whose list is NOT the scoped one still
/// draws its mark. That is what a feed looks like for the frame between a task being moved to
/// another list and the list query catching up, and on that frame the mark is the most
/// informative thing on the row.
///
/// Swift twin of `shouldShowListMark` in Android's `TaskRowListMark.kt`, and the same three
/// lines, because the argument is about the screens rather than about either toolkit.
///
/// - Parameters:
///   - rowListId: the list this task belongs to, or nil for an unfiled task.
///   - scopedListId: the list the SCREEN is already showing, or nil/blank on a mixed feed
///     that spans every list.
func shouldShowListMark(rowListId: String?, scopedListId: String?) -> Bool {
    guard let rowListId, !rowListId.isEmpty else { return false }
    guard let scopedListId, !scopedListId.isEmpty else { return true }
    return rowListId != scopedListId
}
