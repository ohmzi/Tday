import Foundation

/// Whether a floater appears on the Floater main-screen surfaces: the feed, the all-floaters widget
/// and the no-list summary. Mirror of the shared Kotlin `FloaterMainScreen`, which this app does
/// not link. Keep the rule identical: a task with no list, or whose list has not loaded, reads as
/// shown. `tday-web/tests/guardrails/floater-main-screen-parity.test.ts` checks it.
enum FloaterMainScreen {
    static func isShown(listId: String?, hiddenListIds: Set<String>) -> Bool {
        guard let listId else { return true }
        return !hiddenListIds.contains(listId)
    }

    /// The rows the Floater main feed draws. Only the rows: search, the list tile counts and the
    /// empty answer keep reading every task, so a hidden list's open tasks hold "all done" back.
    static func feedItems(_ items: [TodoItem], lists: [ListSummary]) -> [TodoItem] {
        let hidden = Set(lists.filter { !$0.showOnMainScreen }.map(\.id))
        guard !hidden.isEmpty else { return items }
        return items.filter { isShown(listId: $0.listId, hiddenListIds: hidden) }
    }

    /// A search result the feed has no row for: its hidden list is the only place to go.
    static func hiddenList(for todo: TodoItem, lists: [ListSummary]) -> ListSummary? {
        guard let listId = todo.listId else { return nil }
        return lists.first { $0.id == listId && !$0.showOnMainScreen }
    }
}
