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

        XCTAssertEqual(snapshot.schemaVersion, TodayTasksWidgetSnapshotStore.snapshotSchemaVersion)
        XCTAssertEqual(snapshot.title, "Today's Tasks")
        XCTAssertEqual(snapshot.status, .tasks)
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

    func testSnapshotUsesEmptyStateForConfiguredWorkspaceWithoutTodayTasks() {
        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(),
            now: Date(timeIntervalSince1970: 1_764_072_600)
        )

        XCTAssertEqual(snapshot.status, .empty)
        XCTAssertEqual(snapshot.taskCount, 0)
        XCTAssertTrue(snapshot.tasks.isEmpty)
    }

    func testSnapshotUsesSetupStateBeforeWorkspaceConfiguration() {
        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(
                todos: [
                    todo(id: "today", title: "Today", dueEpochMs: Date(timeIntervalSince1970: 1_764_072_600).epochMs)
                ]
            ),
            workspaceConfigured: false,
            now: Date(timeIntervalSince1970: 1_764_072_600)
        )

        XCTAssertEqual(snapshot.status, .setup)
        XCTAssertEqual(snapshot.taskCount, 0)
        XCTAssertTrue(snapshot.tasks.isEmpty)
    }

    func testSnapshotDecodesLegacyPayloads() throws {
        let legacyJSON = """
        {
          "generatedAtEpochMs": 1764072600000,
          "title": "Today's Tasks",
          "taskCount": 1,
          "tasks": [
            {
              "id": "legacy",
              "title": "Legacy task",
              "dueEpochMs": 1764076200000,
              "priority": "low"
            }
          ]
        }
        """

        let snapshot = try JSONDecoder().decode(
            TodayTasksWidgetSnapshot.self,
            from: Data(legacyJSON.utf8)
        )

        XCTAssertEqual(snapshot.schemaVersion, 1)
        XCTAssertEqual(snapshot.status, .tasks)
        XCTAssertEqual(snapshot.taskCount, 1)
        XCTAssertEqual(snapshot.tasks.first?.id, "legacy")
    }

    func testWidgetConstantsStayAlignedWithExtension() {
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.widgetKind, "TodayTasksWidget")
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.appGroupSuiteName, "group.com.ohmz.tday")
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.snapshotKey, "tday.widget.todayTasksSnapshot")
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.snapshotSchemaVersion, 2)
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
