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

/// Which list a completed row's trailing mark stands for — the other half of the question
/// `shouldShowListMark` answers. That one says WHETHER the mark is worth drawing; this one says
/// WHICH list it is. Both are needed on the Completed screen, and neither can be read off the
/// row: `CompletedItem` carries `listId`, `listName` and `listColor` as a denormalised SNAPSHOT
/// taken when the task was completed — the backend's `completedtodo` table stores
/// `projectName`/`projectColor` and no icon key at all, and `CompletedItem` has no `iconKey`
/// field — so the glyph can only come from resolving the live list.
///
/// The namespace argument is why this is a function rather than two lines at the call site.
/// Scheduled lists and Floater lists are two disjoint stores (`ListRepository.buildLists` reads
/// `state.lists`, `FloaterListRepository.buildLists` reads `state.floaterLists`, and their local
/// ids are prefixed `local-list-` and `local-floater-list-`), and nothing stops a Floater list
/// from sharing a name with a scheduled one. Resolving a completed Floater against the scheduled
/// collection would therefore not draw the WRONG glyph — it would find nothing and silently drop
/// the mark, which is the failure the namespace branch exists to avoid. `CompletedItem.isFloater`
/// is the source of truth for which collection to search, the same field Android's
/// `CompletedScreen.kt` branches on.
///
/// `listId` is tried first, because this client has it and an id survives a rename. It is not
/// sufficient on its own: the backend nulls a completed Floater's `listID` the moment its list is
/// deleted (`ON DELETE SET NULL`, keeping `originalListID`), so a deleted-list row falls through
/// to the name. The name match is case- and whitespace-insensitive to match Android's
/// `CompletedItem.resolveListSummary`, and takes the first match inside the chosen namespace.
///
/// - Parameters:
///   - listId: the list's id as the completed record snapshotted it, or nil when the list was
///     deleted before the snapshot was taken.
///   - listName: the list's name as the completed record snapshotted it.
///   - isFloater: which of the two namespaces the row belongs to.
///   - scheduledLists: the scheduled (Todo) lists — searched only when `isFloater` is false.
///   - floaterLists: the Floater lists — searched only when `isFloater` is true.
func tdayResolvedRowList(
    listId: String?,
    listName: String?,
    isFloater: Bool,
    scheduledLists: [ListSummary],
    floaterLists: [ListSummary]
) -> ListSummary? {
    let namespace = isFloater ? floaterLists : scheduledLists

    if let listId, !listId.isEmpty, let byId = namespace.first(where: { $0.id == listId }) {
        return byId
    }

    guard let listName else { return nil }
    let wanted = listName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard !wanted.isEmpty else { return nil }
    return namespace.first {
        $0.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == wanted
    }
}

/// Convenience over the primitive form above, so the row can hand over the item it already has.
func tdayResolvedRowList(
    for item: CompletedItem,
    scheduledLists: [ListSummary],
    floaterLists: [ListSummary]
) -> ListSummary? {
    tdayResolvedRowList(
        listId: item.listId,
        listName: item.listName,
        isFloater: item.isFloater,
        scheduledLists: scheduledLists,
        floaterLists: floaterLists
    )
}
