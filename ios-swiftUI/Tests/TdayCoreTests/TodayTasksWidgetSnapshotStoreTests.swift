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

    func testHasSameContentIgnoresGeneratedAtButCatchesContentChanges() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let dueSoon = startOfDay.addingTimeInterval(9 * 3_600).epochMs

        let state = OfflineSyncState(
            todos: [todo(id: "soon", title: "Soon", dueEpochMs: dueSoon)]
        )
        let first = TodayTasksWidgetSnapshotStore.makeSnapshot(from: state, now: now, calendar: calendar)
        // Same displayed content, generated 30s later -> only generatedAtEpochMs differs.
        let later = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: state,
            now: now.addingTimeInterval(30),
            calendar: calendar
        )
        XCTAssertNotEqual(first.generatedAtEpochMs, later.generatedAtEpochMs)
        XCTAssertTrue(first.hasSameContent(as: later), "same displayed content must compare equal despite a newer timestamp")

        // A real visible change (title) must break the content comparison.
        let renamed = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: OfflineSyncState(todos: [todo(id: "soon", title: "Soon (edited)", dueEpochMs: dueSoon)]),
            now: now,
            calendar: calendar
        )
        XCTAssertFalse(first.hasSameContent(as: renamed))
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

        // Distinct minutes so due-time ordering is unambiguous (dues within the same
        // minute floor-tie and fall through to the modified/id tiebreak).
        let state = OfflineSyncState(
            todos: [
                todo(id: "with-note", title: "With note", dueEpochMs: due, description: "Bring the receipts"),
                todo(id: "without-note", title: "Without note", dueEpochMs: due + 60_000)
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
        // Task text lives in the protected App Group file, not UserDefaults. The extension
        // hardcodes the same name, so a rename here has to be mirrored there.
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.snapshotFileName, "widget-today-snapshot.json")
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.legacySnapshotKey, "tday.widget.todayTasksSnapshot")
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
        // priorityRank is vocabulary-tolerant to match the shared engine: "Urgent"
        // ranks as High and "Important" as Medium (not Low). All modified times are
        // equal here, so within a rank the tiebreak falls to id ascending.
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
            // High rank: high-b, urgent-a (id asc) | Medium rank: medium-a, urgent-b | Low: low-a
            ["pinned-low", "high-b", "urgent-a", "medium-a", "urgent-b", "low-a"]
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
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.snapshotFileName, "widget-floater-snapshot.json")
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.legacySnapshotKey, "tday.widget.floaterTasksSnapshot")
        XCTAssertEqual(FloaterTasksWidgetSnapshotStore.snapshotSchemaVersion, 1)
    }

    // MARK: - Snapshot storage (task text at rest)
    //
    // The snapshots carry task titles, notes and due times. They used to be written as plain
    // JSON into UserDefaults, whose plist is unencrypted and lands in device backups. These
    // pin the replacement: an App Group file, encrypted at rest, out of backups, and still
    // readable by the widget on a locked device.

    func testSnapshotFileIsEncryptedAtRestAndExcludedFromBackup() throws {
        let fileURL = try appGroupFileURL(TodayTasksWidgetSnapshotStore.snapshotFileName)
        addTeardownBlock { try? FileManager.default.removeItem(at: fileURL) }

        WidgetSnapshotFileStore.write(Data("task text".utf8), to: TodayTasksWidgetSnapshotStore.snapshotFileName)

        let attributes = try FileManager.default.attributesOfItem(atPath: fileURL.path)
        // .completeUntilFirstUserAuthentication and NOT .complete: the widget has to keep
        // rendering while the device is locked, which .complete would break.
        //
        // The Simulator does not implement data protection at all, so the attribute is simply
        // absent there — asserting it unconditionally would fail on every simulator run. Assert
        // it whenever the platform records one, and demand it on real hardware, which is where
        // the guarantee actually has to hold.
        if let protection = attributes[.protectionKey] as? FileProtectionType {
            XCTAssertEqual(protection, .completeUntilFirstUserAuthentication)
        } else {
            #if !targetEnvironment(simulator)
            XCTFail("widget snapshot was written with no file protection class")
            #endif
        }
        XCTAssertTrue(
            try fileURL.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup ?? false,
            "task text must not ride along in an unencrypted device backup"
        )
        XCTAssertEqual(WidgetSnapshotFileStore.read(TodayTasksWidgetSnapshotStore.snapshotFileName), Data("task text".utf8))
    }

    func testLegacyDefaultsSnapshotIsMigratedOnceAndThenDeleted() throws {
        let fileURL = try appGroupFileURL(TodayTasksWidgetSnapshotStore.snapshotFileName)
        let stores = [
            UserDefaults(suiteName: TodayTasksWidgetSnapshotStore.appGroupSuiteName),
            .standard
        ].compactMap { $0 }
        let key = TodayTasksWidgetSnapshotStore.legacySnapshotKey

        try? FileManager.default.removeItem(at: fileURL)
        addTeardownBlock {
            try? FileManager.default.removeItem(at: fileURL)
            stores.forEach { $0.removeObject(forKey: key) }
        }

        let legacy = TodayTasksWidgetSnapshot(
            generatedAtEpochMs: 1_764_072_600_000,
            title: "Today's Tasks",
            status: .tasks,
            taskCount: 1,
            tasks: [
                TodayTasksWidgetTaskSnapshot(
                    id: "legacy",
                    title: "Buy the thing",
                    dueEpochMs: 1_764_076_200_000,
                    priority: "low"
                )
            ]
        )
        let legacyData = try JSONEncoder().encode(legacy)
        stores.forEach { $0.set(legacyData, forKey: key) }

        // The upgrade must not lose the widget's content...
        let loaded = TodayTasksWidgetSnapshotStore.loadSnapshot()
        XCTAssertEqual(loaded?.tasks.first?.title, "Buy the thing")
        // ...it moves into the protected file...
        XCTAssertNotNil(WidgetSnapshotFileStore.read(TodayTasksWidgetSnapshotStore.snapshotFileName))
        // ...and the plaintext copies are gone rather than left behind to be backed up.
        for store in stores {
            XCTAssertNil(store.data(forKey: key), "legacy plaintext snapshot still present in UserDefaults")
        }
        // Still readable afterwards, now from the file alone.
        XCTAssertEqual(TodayTasksWidgetSnapshotStore.loadSnapshot()?.tasks.first?.title, "Buy the thing")
    }

    // MARK: - Local SwiftData store at rest
    //
    // Same threat as the snapshots above, bigger prize: the SwiftData store holds every task
    // title, note and list name plus the UNSYNCED pending mutations. It is a plain SQLite file
    // (SwiftData offers no cipher hook), so what these pin is the protection that IS achievable
    // — Data Protection class and, above all, exclusion from device/iCloud backups.

    func testStoreProtectionCoversTheWalAndShmSidecars() {
        let storeURL = URL(fileURLWithPath: "/tmp/tday-test/default.store")

        XCTAssertEqual(
            LocalStoreFileProtection.protectedURLs(for: storeURL).map(\.lastPathComponent),
            ["default.store", "default.store-wal", "default.store-shm"],
            "the -wal holds the most recently typed rows; protecting only the .store protects nothing"
        )
    }

    func testStoreProtectionKeepsTaskTextOutOfBackupsAndSkipsMissingFiles() throws {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("tday-store-protection-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }

        let storeURL = directory.appendingPathComponent("default.store")
        let walURL = directory.appendingPathComponent("default.store-wal")
        // No -shm on disk: SQLite creates and drops the sidecars as it checkpoints, so applying
        // protection must tolerate a partially present set rather than bail on the first miss.
        try Data("task title".utf8).write(to: storeURL)
        try Data("unsynced mutation".utf8).write(to: walURL)

        let stamped = LocalStoreFileProtection.apply(to: storeURL)

        XCTAssertEqual(stamped, [storeURL, walURL])
        for url in stamped {
            XCTAssertTrue(
                try url.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup ?? false,
                "\(url.lastPathComponent) must not ride along in an unencrypted device backup"
            )
            // The Simulator does not implement data protection, so the attribute is simply absent
            // there. Assert it whenever the platform records one, and demand it on real hardware.
            let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
            if let protection = attributes[.protectionKey] as? FileProtectionType {
                // NOT .complete: that would make the store unreadable while the device is locked
                // and break the widgets' background refresh.
                XCTAssertEqual(protection, .completeUntilFirstUserAuthentication)
            } else {
                #if !targetEnvironment(simulator)
                XCTFail("\(url.lastPathComponent) was left with no file protection class")
                #endif
            }
        }
    }

    // MARK: - App lock cover

    func testAppLockRendersNothingWhileTheSettingIsOff() {
        for isLocked in [true, false] {
            for isSceneActive in [true, false] {
                XCTAssertEqual(
                    AppLockController.coverMode(isEnabled: false, isLocked: isLocked, isSceneActive: isSceneActive),
                    .hidden,
                    "the lock is default OFF and must stay completely inert then"
                )
            }
        }
    }

    func testAppLockCoversContentWhenArmedAndWhenLeavingTheForeground() {
        XCTAssertEqual(
            AppLockController.coverMode(isEnabled: true, isLocked: true, isSceneActive: false),
            .gate
        )
        XCTAssertEqual(
            AppLockController.coverMode(isEnabled: true, isLocked: true, isSceneActive: true),
            .gate
        )
        // Leaving the foreground before the gate re-arms at .background: this is the state the
        // app-switcher snapshot is taken in, so it must not show task text.
        XCTAssertEqual(
            AppLockController.coverMode(isEnabled: true, isLocked: false, isSceneActive: false),
            .privacyCover
        )
        XCTAssertEqual(
            AppLockController.coverMode(isEnabled: true, isLocked: false, isSceneActive: true),
            .hidden
        )
    }

    private func appGroupFileURL(_ fileName: String) throws -> URL {
        let container = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: TodayTasksWidgetSnapshotStore.appGroupSuiteName
        )
        return try XCTUnwrap(container, "App Group container unavailable").appendingPathComponent(fileName)
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
        description: String? = nil,
        listId: String? = nil
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
            listId: listId,
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

    private func list(id: String, name: String) -> CachedListRecord {
        CachedListRecord(id: id, name: name, color: nil, iconKey: nil, todoCount: 0, updatedAtEpochMs: 0, createdAtEpochMs: 0)
    }

    private func floaterList(id: String, name: String) -> CachedFloaterListRecord {
        CachedFloaterListRecord(id: id, name: name, color: nil, iconKey: nil, todoCount: 0, updatedAtEpochMs: 0, createdAtEpochMs: 0)
    }

    // MARK: - Per-list breakdown (R7 configurable widgets)
    //
    // `perList` scopes a widget instance to ONE list, independent of the global "due today"
    // aggregate above — these pin its due-today-OR-OVERDUE window, the empty-list omission, the
    // display cap vs. true count split, and that it ignores the Focus filter (which is a
    // Today-feed concept, not a per-list-widget one).

    func testPerListIncludesTodayAndOverdueTasksForThatList() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let yesterday = startOfDay.addingTimeInterval(-3_600).epochMs
        let dueSoon = startOfDay.addingTimeInterval(9 * 3_600).epochMs
        let tomorrow = startOfDay.addingTimeInterval(24 * 3_600).epochMs

        let state = OfflineSyncState(
            todos: [
                todo(id: "overdue", title: "Overdue", dueEpochMs: yesterday, listId: "list-1"),
                todo(id: "today", title: "Today", dueEpochMs: dueSoon, listId: "list-1"),
                todo(id: "future", title: "Future", dueEpochMs: tomorrow, listId: "list-1"),
                todo(id: "completed", title: "Completed", dueEpochMs: yesterday, completed: true, listId: "list-1")
            ],
            lists: [list(id: "list-1", name: "Work")]
        )

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(from: state, now: now, calendar: calendar)

        // Overdue + today, but NOT tomorrow or the completed row.
        let list1 = try! XCTUnwrap(snapshot.perList["list-1"])
        XCTAssertEqual(list1.totalCount, 2)
        XCTAssertEqual(Set(list1.tasks.map(\.id)), ["overdue", "today"])
        // The GLOBAL aggregate is unaffected: still strictly due-today, so the per-list overdue
        // inclusion never leaks into the widget instances that have no list configured.
        XCTAssertEqual(snapshot.tasks.map(\.id), ["today"])
    }

    func testPerListDoesNotLeakOtherListsTasks() {
        let nowEpochMs: Int64 = 1_764_072_600_000
        let state = OfflineSyncState(
            todos: [
                todo(id: "in-list-1", title: "In list 1", dueEpochMs: nowEpochMs, listId: "list-1"),
                todo(id: "in-list-2", title: "In list 2", dueEpochMs: nowEpochMs, listId: "list-2")
            ],
            lists: [list(id: "list-1", name: "Work"), list(id: "list-2", name: "Home")]
        )

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(
            from: state,
            now: Date(timeIntervalSince1970: 1_764_072_600)
        )

        XCTAssertEqual(snapshot.perList["list-1"]?.tasks.map(\.id), ["in-list-1"])
        XCTAssertEqual(snapshot.perList["list-2"]?.tasks.map(\.id), ["in-list-2"])
    }

    func testPerListOmittedForListsWithNoOpenTasks() {
        let state = OfflineSyncState(
            todos: [],
            lists: [list(id: "empty-list", name: "Empty")]
        )
        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(from: state, now: Date(timeIntervalSince1970: 1_764_072_600))
        XCTAssertNil(snapshot.perList["empty-list"], "an empty list should have no perList entry, not an empty one")
    }

    func testPerListTracksTrueCountBeyondDisplayCap() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let todos = (0..<25).map { index in
            todo(
                id: "task-\(index)",
                title: "Task \(index)",
                dueEpochMs: startOfDay.addingTimeInterval(TimeInterval(index * 60)).epochMs,
                listId: "busy-list"
            )
        }
        let state = OfflineSyncState(todos: todos, lists: [list(id: "busy-list", name: "Busy")])

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(from: state, now: now, calendar: calendar)

        let busy = try! XCTUnwrap(snapshot.perList["busy-list"])
        XCTAssertEqual(busy.totalCount, 25)
        XCTAssertEqual(busy.tasks.count, TodayTasksWidgetSnapshotStore.perListTaskLimit)
    }

    func testPerListIgnoresActiveFocusFilter() {
        addTeardownBlock { TdayFocusFilterStore.setActiveListIDs([]) }
        // Focus narrows the GLOBAL Today feed only — a widget explicitly configured to a list
        // shows that list regardless of which Focus is active.
        TdayFocusFilterStore.setActiveListIDs(["list-2"])

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let dueSoon = startOfDay.addingTimeInterval(9 * 3_600).epochMs

        let state = OfflineSyncState(
            todos: [todo(id: "focused-out", title: "Focused out", dueEpochMs: dueSoon, listId: "list-1")],
            lists: [list(id: "list-1", name: "Work")]
        )

        let snapshot = TodayTasksWidgetSnapshotStore.makeSnapshot(from: state, now: now, calendar: calendar)

        // Excluded from the global feed by the active Focus (list-1 is not list-2)...
        XCTAssertTrue(snapshot.tasks.isEmpty)
        // ...but still present in its own per-list slice.
        XCTAssertEqual(snapshot.perList["list-1"]?.tasks.map(\.id), ["focused-out"])
    }

    func testFloaterPerListIncludesOnlyThatListsOpenFloaters() {
        let state = OfflineSyncState(
            floaters: [
                floater(id: "in-list", title: "In list", listId: "floater-list-1"),
                floater(id: "completed-in-list", title: "Completed", completed: true, listId: "floater-list-1"),
                floater(id: "other-list", title: "Other list", listId: "floater-list-2")
            ],
            floaterLists: [floaterList(id: "floater-list-1", name: "Someday")]
        )

        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(from: state)

        XCTAssertEqual(snapshot.perList["floater-list-1"]?.tasks.map(\.id), ["in-list"])
        XCTAssertNil(snapshot.perList["floater-list-2"], "no CachedFloaterListRecord was provided for floater-list-2")
    }

    func testFloaterPerListOmittedForListsWithNoOpenFloaters() {
        let state = OfflineSyncState(floaters: [], floaterLists: [floaterList(id: "empty", name: "Empty")])
        let snapshot = FloaterTasksWidgetSnapshotStore.makeSnapshot(from: state)
        XCTAssertNil(snapshot.perList["empty"])
    }

    func testPerListChangeIsDetectedByHasSameContent() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_764_072_600)
        let startOfDay = calendar.startOfDay(for: now)
        let yesterday = startOfDay.addingTimeInterval(-3_600).epochMs

        let baseState = OfflineSyncState(lists: [list(id: "list-1", name: "Work")])
        let first = TodayTasksWidgetSnapshotStore.makeSnapshot(from: baseState, now: now, calendar: calendar)

        // Adds an OVERDUE todo only — invisible to the global "due today" feed (which requires
        // dueEpochMs >= dayStart), so this isolates a perList-ONLY content change: taskCount/tasks
        // stay identical between first and second, and only perList should differ.
        var changedState = baseState
        changedState.todos.append(todo(id: "overdue-only", title: "Overdue only", dueEpochMs: yesterday, listId: "list-1"))
        let second = TodayTasksWidgetSnapshotStore.makeSnapshot(from: changedState, now: now, calendar: calendar)

        XCTAssertEqual(first.taskCount, second.taskCount, "sanity: the global feed itself is unchanged by this edit")
        XCTAssertFalse(
            first.hasSameContent(as: second),
            "a perList-only change (no global-feed change) must still be treated as new content"
        )
    }

    // MARK: - Widget configuration picker catalog (R7)

    func testConfigurableListsStoreWritesTodoAndFloaterLists() throws {
        let fileURL = try appGroupFileURL(WidgetSnapshotFileStore.listsFileName)
        addTeardownBlock { try? FileManager.default.removeItem(at: fileURL) }

        let state = OfflineSyncState(
            lists: [list(id: "todo-list-1", name: "Work")],
            floaterLists: [floaterList(id: "floater-list-1", name: "Someday")]
        )
        WidgetConfigurableListsStore.save(from: state)

        let data = try XCTUnwrap(WidgetSnapshotFileStore.read(WidgetSnapshotFileStore.listsFileName))
        let entries = try JSONDecoder().decode([WidgetConfigurableListEntry].self, from: data)

        XCTAssertEqual(Set(entries.map(\.id)), ["todo-list-1", "floater-list-1"])
        XCTAssertEqual(entries.first(where: { $0.id == "todo-list-1" })?.kind, "todo")
        XCTAssertEqual(entries.first(where: { $0.id == "floater-list-1" })?.kind, "floater")
    }
}

private extension Date {
    var epochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}
