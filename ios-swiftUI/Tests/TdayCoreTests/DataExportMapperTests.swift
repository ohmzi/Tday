import XCTest
#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

final class DataExportMapperTests: XCTestCase {

    func testMapsCacheRecordsIntoBundle() {
        let state = OfflineSyncState(
            todos: [
                CachedTodoRecord(
                    id: "local-todo-1", canonicalId: "todo-1", title: "Plan",
                    description: nil, priority: "High", dueEpochMs: 1_000, rrule: nil,
                    instanceDateEpochMs: nil, pinned: false, completed: false,
                    listId: "list-1", updatedAtEpochMs: 2_000
                ),
            ],
            floaters: [
                CachedFloaterRecord(
                    id: "local-fl-1", canonicalId: "fl-1", title: "Idea",
                    description: nil, priority: "Low", pinned: false, completed: false,
                    listId: nil, updatedAtEpochMs: 0
                ),
            ],
            completedItems: [
                CachedCompletedRecord(
                    id: "c-1", originalTodoId: "todo-9", title: "Done", description: nil,
                    priority: "Low", dueEpochMs: nil, completedAtEpochMs: 3_000, rrule: nil,
                    instanceDateEpochMs: nil, listId: nil, listName: nil, listColor: nil
                ),
            ],
            completedFloaters: [
                CachedCompletedFloaterRecord(
                    id: "cf-1", originalFloaterId: "fl-9", title: "DoneFloater", description: nil,
                    priority: "Low", completedAtEpochMs: 4_000, listId: nil, listName: nil, listColor: nil
                ),
            ],
            lists: [
                CachedListRecord(
                    id: "list-1", name: "Work", color: "BLUE", iconKey: nil,
                    todoCount: 0, updatedAtEpochMs: 0, createdAtEpochMs: 0
                ),
            ],
            floaterLists: [],
            aiSummaryEnabled: false
        )

        let export = LocalExportMapper.make(from: state, source: "local-ios", exportedAtEpochMs: 5_000)

        XCTAssertEqual(export.source, "local-ios")
        // Todo maps by canonical (server) id, not the interim local id.
        XCTAssertEqual(export.todos.first?.todo.id, "todo-1")
        XCTAssertEqual(export.todos.first?.todo.priority, "High")
        XCTAssertEqual(export.todos.first?.todo.listID, "list-1")
        XCTAssertEqual(export.floaters.first?.id, "fl-1")
        XCTAssertEqual(export.lists.first?.name, "Work")
        XCTAssertEqual(export.lists.first?.color, "BLUE")
        XCTAssertEqual(export.completedTodos.first?.originalTodoID, "todo-9")
        XCTAssertEqual(export.completedFloaters.first?.originalFloaterID, "fl-9")
        XCTAssertEqual(export.preferences?.aiSummaryEnabled, false)
    }

    func testTodoWithoutDueFallsBackToExportTimestamp() {
        let state = OfflineSyncState(
            todos: [
                CachedTodoRecord(
                    id: "l", canonicalId: "t", title: "x", description: nil, priority: "Low",
                    dueEpochMs: nil, rrule: nil, instanceDateEpochMs: nil, pinned: false,
                    completed: false, listId: nil, updatedAtEpochMs: 0
                ),
            ]
        )
        let export = LocalExportMapper.make(from: state, source: "local-ios", exportedAtEpochMs: 9_000)
        XCTAssertEqual(export.todos.first?.todo.due, Date(epochMilliseconds: 9_000).ISO8601Format())
    }
}
