import XCTest
@testable import Tday

final class TodayTasksWidgetSnapshotStoreTests: XCTestCase {
    func testSnapshotIncludesOnlyPendingTasksDueToday() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)

        let yesterday = startOfDay.addingTimeInterval(-60).epochMs
        let dueSoon = startOfDay.addingTimeInterval(9 * 3_600).epochMs
        let dueLater = startOfDay.addingTimeInterval(17 * 3_600).epochMs
        let tomorrow = startOfDay.addingTimeInterval(24 * 3_600).epochMs

        let state = OfflineSyncState(
            todos: [
                todo(id: "yesterday", title: "Yesterday", dueEpochMs: yesterday),
                todo(id: "completed", title: "Completed", dueEpochMs: dueSoon, completed: true),
                todo(id: "later", title: "Later", dueEpochMs: dueLater),
                todo(id: "soon", title: "Soon", dueEpochMs: dueSoon),
                todo(id: "tomorrow", title: "Tomorrow", dueEpochMs: tomorrow)
            ]
        )

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: state,
            now: now,
            calendar: calendar
        )

        XCTAssertEqual(snapshot.title, "Today's Tasks")
        XCTAssertEqual(snapshot.taskCount, 2)
        XCTAssertEqual(snapshot.tasks.map(\.id), ["soon", "later"])
    }

    func testSnapshotCapsTasksForWidgetDisplay() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let todos = (0..<10).map { index in
            todo(
                id: "task-\(index)",
                title: "Task \(index)",
                dueEpochMs: startOfDay.addingTimeInterval(TimeInterval(index * 600)).epochMs
            )
        }

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(todos: todos),
            now: now,
            calendar: calendar
        )

        XCTAssertEqual(snapshot.taskCount, 10)
        XCTAssertEqual(snapshot.tasks.count, 8)
        XCTAssertEqual(snapshot.tasks.first?.id, "task-0")
        XCTAssertEqual(snapshot.tasks.last?.id, "task-7")
    }

    private func todo(
        id: String,
        title: String,
        dueEpochMs: Int64,
        completed: Bool = false
    ) -> CachedTodoRecord {
        CachedTodoRecord(
            id: id,
            canonicalId: id,
            title: title,
            description: nil,
            priority: "low",
            dueEpochMs: dueEpochMs,
            rrule: nil,
            instanceDateEpochMs: nil,
            pinned: false,
            completed: completed,
            listId: nil,
            updatedAtEpochMs: dueEpochMs
        )
    }
}

private extension Date {
    var epochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}
