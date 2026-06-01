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
        let todos = (0..<25).map { index in
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

        XCTAssertEqual(snapshot.taskCount, 25)
        XCTAssertEqual(snapshot.tasks.count, 20)
        XCTAssertEqual(snapshot.tasks.first?.id, "task-0")
        XCTAssertEqual(snapshot.tasks.last?.id, "task-19")
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

    func testFloaterSnapshotIncludesOnlyPendingFloaters() {
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let state = OfflineSyncState(
            floaters: [
                floater(id: "open", title: "Open"),
                floater(id: "listed", title: "Listed", listId: "list-1"),
                floater(id: "completed", title: "Completed", completed: true)
            ]
        )

        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(from: state, now: now)

        XCTAssertEqual(snapshot.schemaVersion, FloaterTasksWidgetSnapshotStore.snapshotSchemaVersion)
        XCTAssertEqual(snapshot.title, "Floater Tasks")
        XCTAssertEqual(snapshot.status, .tasks)
        XCTAssertEqual(snapshot.taskCount, 2)
        XCTAssertEqual(snapshot.tasks.map(\.id), ["listed", "open"])
    }

    func testFloaterSnapshotSortsPinnedPriorityTitleAndID() {
        let state = OfflineSyncState(
            floaters: [
                floater(id: "low-a", title: "Alpha", priority: "Low"),
                floater(id: "high-b", title: "Beta", priority: "High"),
                floater(id: "medium-a", title: "Alpha", priority: "Medium"),
                floater(id: "pinned-low", title: "Zulu", priority: "Low", pinned: true),
                floater(id: "urgent-a", title: "Alpha", priority: "Urgent"),
                floater(id: "urgent-b", title: "Alpha", priority: "Important")
            ]
        )

        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(from: state)

        XCTAssertEqual(
            snapshot.tasks.map(\.id),
            ["pinned-low", "urgent-a", "urgent-b", "high-b", "medium-a", "low-a"]
        )
    }

    func testFloaterSnapshotCapsTasksForWidgetDisplay() {
        let floaters = (0..<25).map { index in
            floater(
                id: "task-\(index)",
                title: "Task \(String(format: "%02d", index))"
            )
        }

        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(floaters: floaters)
        )

        XCTAssertEqual(snapshot.taskCount, 25)
        XCTAssertEqual(snapshot.tasks.count, 20)
        XCTAssertEqual(snapshot.tasks.first?.id, "task-0")
        XCTAssertEqual(snapshot.tasks.last?.id, "task-19")
    }

    func testFloaterSnapshotUsesEmptyStateForConfiguredWorkspaceWithoutFloaterTasks() {
        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(),
            now: Date(timeIntervalSince1970: 1_764_072_600)
        )

        XCTAssertEqual(snapshot.status, .empty)
        XCTAssertEqual(snapshot.taskCount, 0)
        XCTAssertTrue(snapshot.tasks.isEmpty)
    }

    func testFloaterSnapshotUsesSetupStateBeforeWorkspaceConfiguration() {
        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(
                floaters: [
                    floater(id: "floater", title: "Floater")
                ]
            ),
            workspaceConfigured: false,
            now: Date(timeIntervalSince1970: 1_764_072_600)
        )

        XCTAssertEqual(snapshot.status, .setup)
        XCTAssertEqual(snapshot.taskCount, 0)
        XCTAssertTrue(snapshot.tasks.isEmpty)
    }

    func testFloaterWidgetConstantsStayAlignedWithExtension() {
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.widgetKind, "FloaterTasksWidget")
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.appGroupSuiteName, "group.com.ohmz.tday")
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.snapshotKey, "tday.widget.floaterTasksSnapshot")
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.snapshotSchemaVersion, 1)
    }

    func testCreateFloaterDeepLinkRoute() {
        XCTAssertEqual(
            AppRoute.from(url: URL(string: "tday://todos/create?target=floater")!),
            .createFloaterTodo
        )
        XCTAssertEqual(
            AppRoute.from(url: URL(string: "tday://todos/create?target=today")!),
            .createTodayTodo
        )
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

    private func floater(
        id: String,
        title: String,
        priority: String = "low",
        pinned: Bool = false,
        completed: Bool = false,
        listId: String? = nil
    ) -> CachedFloaterRecord {
        CachedFloaterRecord(
            id: id,
            canonicalId: id,
            title: title,
            description: nil,
            priority: priority,
            pinned: pinned,
            completed: completed,
            listId: listId,
            updatedAtEpochMs: 0
        )
    }
}

private extension Date {
    var epochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}
