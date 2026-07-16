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
        let todos = (0..<55).map { index in
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

        XCTAssertEqual(snapshot.taskCount, 55)
        XCTAssertEqual(snapshot.tasks.count, 50)
        XCTAssertEqual(snapshot.tasks.first?.id, "task-0")
        XCTAssertEqual(snapshot.tasks.last?.id, "task-49")
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
        XCTAssertNil(snapshot.tasks.first?.description)
    }

    func testSnapshotIncludesTaskDescriptions() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let due = startOfDay.addingTimeInterval(9 * 3_600).epochMs

        let state = OfflineSyncState(
            todos: [
                todo(id: "with-note", title: "With note", dueEpochMs: due, description: "Bring the receipts"),
                todo(id: "without-note", title: "Without note", dueEpochMs: due + 1)
            ]
        )

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: state,
            now: now,
            calendar: calendar
        )

        XCTAssertEqual(snapshot.tasks.map(\.id), ["with-note", "without-note"])
        XCTAssertEqual(snapshot.tasks.first?.description, "Bring the receipts")
        XCTAssertNil(snapshot.tasks.last?.description)
    }

    func testSnapshotRoundTripsTaskDescriptions() throws {
        let snapshot = TodayTasksWidgetSnapshot(
            generatedAtEpochMs: 1_764_072_600_000,
            title: "Today's Tasks",
            status: .tasks,
            taskCount: 1,
            tasks: [
                TodayTasksWidgetTaskSnapshot(
                    id: "task",
                    title: "Task",
                    dueEpochMs: 1_764_076_200_000,
                    priority: "low",
                    description: "Two-line widget note"
                )
            ]
        )

        let data = try JSONEncoder().encode(snapshot)
        let decoded = try JSONDecoder().decode(TodayTasksWidgetSnapshot.self, from: data)

        XCTAssertEqual(decoded, snapshot)
        XCTAssertEqual(decoded.tasks.first?.description, "Two-line widget note")
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

    func testFloaterSnapshotSortsByFixedOrdering() {
        // Fixed FLOATER ordering: pinned, priority High->Low, modified desc, id.
        // Only canonical "High"/"Medium"/"Low" rank; non-canonical ("Urgent",
        // "Important") fall through to Low, matching the shared engine exactly.
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
            ["pinned-low", "high-b", "medium-a", "low-a", "urgent-a", "urgent-b"]
        )
    }

    func testFloaterSnapshotBreaksPriorityTiesByModifiedThenID() {
        // Same priority: most-recently-modified first, then id when unmodified.
        let state = OfflineSyncState(
            floaters: [
                floater(id: "older", title: "Older", priority: "Low", updatedAtEpochMs: 1_000),
                floater(id: "newer", title: "Newer", priority: "Low", updatedAtEpochMs: 2_000),
                floater(id: "b-untouched", title: "Untouched B", priority: "Low"),
                floater(id: "a-untouched", title: "Untouched A", priority: "Low")
            ]
        )

        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(from: state)

        // newer/older lead by modified desc; the two unmodified fall last by id asc.
        XCTAssertEqual(
            snapshot.tasks.map(\.id),
            ["newer", "older", "a-untouched", "b-untouched"]
        )
    }

    func testFloaterSnapshotCapsTasksForWidgetDisplay() {
        // Descending updatedAt so the fixed ordering (modified desc) yields
        // task-0 (newest) first through task-54 (oldest) last.
        let floaters = (0..<55).map { index in
            floater(
                id: "task-\(index)",
                title: "Task \(String(format: "%02d", index))",
                updatedAtEpochMs: Int64(10_000 - index)
            )
        }

        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(floaters: floaters)
        )

        XCTAssertEqual(snapshot.taskCount, 55)
        XCTAssertEqual(snapshot.tasks.count, 50)
        XCTAssertEqual(snapshot.tasks.first?.id, "task-0")
        XCTAssertEqual(snapshot.tasks.last?.id, "task-49")
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
        completed: Bool = false,
        description: String? = nil
    ) -> CachedTodoRecord {
        CachedTodoRecord(
            id: id,
            canonicalId: id,
            title: title,
            description: description,
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
        listId: String? = nil,
        updatedAtEpochMs: Int64 = 0
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
            updatedAtEpochMs: updatedAtEpochMs
        )
    }
}

private extension Date {
    var epochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}
