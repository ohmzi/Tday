import Foundation

#if canImport(WidgetKit)
import WidgetKit
#endif

struct TodayTasksWidgetSnapshot: Codable, Equatable {
    let schemaVersion: Int
    let generatedAtEpochMs: Int64
    let title: String
    let status: TodayTasksWidgetSnapshotStatus
    let taskCount: Int
    let tasks: [TodayTasksWidgetTaskSnapshot]

    init(
        schemaVersion: Int = TodayTasksWidgetSnapshotStore.snapshotSchemaVersion,
        generatedAtEpochMs: Int64,
        title: String,
        status: TodayTasksWidgetSnapshotStatus,
        taskCount: Int,
        tasks: [TodayTasksWidgetTaskSnapshot]
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAtEpochMs = generatedAtEpochMs
        self.title = title
        self.status = status
        self.taskCount = taskCount
        self.tasks = tasks
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedTasks = try container.decodeIfPresent([TodayTasksWidgetTaskSnapshot].self, forKey: .tasks) ?? []
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        generatedAtEpochMs = try container.decode(Int64.self, forKey: .generatedAtEpochMs)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? TodayTasksWidgetSnapshotStore.defaultTitle
        status = (try? container.decodeIfPresent(TodayTasksWidgetSnapshotStatus.self, forKey: .status)) ?? (decodedTasks.isEmpty ? .empty : .tasks)
        taskCount = try container.decodeIfPresent(Int.self, forKey: .taskCount) ?? decodedTasks.count
        tasks = decodedTasks
    }
}

struct TodayTasksWidgetTaskSnapshot: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
    // Optional so previously persisted snapshots without this field still decode (as nil).
    let description: String?

    init(
        id: String,
        title: String,
        dueEpochMs: Int64,
        priority: String,
        description: String? = nil
    ) {
        self.id = id
        self.title = title
        self.dueEpochMs = dueEpochMs
        self.priority = priority
        self.description = description
    }
}

enum TodayTasksWidgetSnapshotStatus: String, Codable, Equatable {
    case setup
    case empty
    case tasks
}

enum TodayTasksWidgetSnapshotStore {
    static let snapshotSchemaVersion = 2
    static let widgetKind = "TodayTasksWidget"
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let snapshotKey = "tday.widget.todayTasksSnapshot"
    static let defaultTitle = "Today's Tasks"
    static let taskLimit = 50

    static func makeSnapshot(
        from state: OfflineSyncState,
        workspaceConfigured: Bool = true,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> TodayTasksWidgetSnapshot {
        guard workspaceConfigured else {
            return TodayTasksWidgetSnapshot(
                generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
                title: defaultTitle,
                status: .setup,
                taskCount: 0,
                tasks: []
            )
        }

        let dayStart = calendar.startOfDay(for: now)
        let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart.addingTimeInterval(86_400)
        let dayStartEpochMs = Int64(dayStart.timeIntervalSince1970 * 1_000)
        let dayEndEpochMs = Int64(dayEnd.timeIntervalSince1970 * 1_000)

        // An active iOS Focus filter (R6-3) narrows the widget to its chosen lists.
        let focusListIDs = TdayFocusFilterStore.activeListIDs()
        let todayTasks = state.todos
            .filter {
                guard let dueEpochMs = $0.dueEpochMs else {
                    return false
                }
                guard !$0.completed && dueEpochMs >= dayStartEpochMs && dueEpochMs < dayEndEpochMs else {
                    return false
                }
                guard let focusListIDs else { return true }
                return $0.listId.map(focusListIDs.contains) ?? false
            }
            .sorted { left, right in
                let leftDue = left.dueEpochMs ?? Int64.max
                let rightDue = right.dueEpochMs ?? Int64.max
                if leftDue == rightDue {
                    return left.title.localizedStandardCompare(right.title) == .orderedAscending
                }
                return leftDue < rightDue
            }

        return TodayTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: defaultTitle,
            status: todayTasks.isEmpty ? .empty : .tasks,
            taskCount: todayTasks.count,
            tasks: todayTasks.prefix(taskLimit).map {
                TodayTasksWidgetTaskSnapshot(
                    id: $0.id,
                    title: $0.title,
                    dueEpochMs: $0.dueEpochMs ?? dayStartEpochMs,
                    priority: $0.priority,
                    description: $0.description
                )
            }
        )
    }

    static func saveTodayTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        guard let data = try? JSONEncoder().encode(snapshot) else {
            return
        }

        let stores = defaultsStores()
        stores.forEach { store in
            store.set(data, forKey: snapshotKey)
        }

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
        #endif

        // Mirror the same Today snapshot to a paired Apple Watch (R6-4).
        WatchSessionManager.shared.syncTodaySnapshot()
    }

    static func loadSnapshot() -> TodayTasksWidgetSnapshot? {
        for store in defaultsStores() {
            guard let data = store.data(forKey: snapshotKey),
                  let snapshot = try? JSONDecoder().decode(TodayTasksWidgetSnapshot.self, from: data) else {
                continue
            }
            return snapshot
        }
        return nil
    }

    private static func defaultsStores() -> [UserDefaults] {
        var stores = [UserDefaults]()
        if let shared = UserDefaults(suiteName: appGroupSuiteName) {
            stores.append(shared)
        }
        stores.append(.standard)
        return stores
    }
}

struct FloaterTasksWidgetSnapshot: Codable, Equatable {
    let schemaVersion: Int
    let generatedAtEpochMs: Int64
    let title: String
    let status: FloaterTasksWidgetSnapshotStatus
    let taskCount: Int
    let tasks: [FloaterTasksWidgetTaskSnapshot]

    init(
        schemaVersion: Int = FloaterTasksWidgetSnapshotStore.snapshotSchemaVersion,
        generatedAtEpochMs: Int64,
        title: String,
        status: FloaterTasksWidgetSnapshotStatus,
        taskCount: Int,
        tasks: [FloaterTasksWidgetTaskSnapshot]
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAtEpochMs = generatedAtEpochMs
        self.title = title
        self.status = status
        self.taskCount = taskCount
        self.tasks = tasks
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedTasks = try container.decodeIfPresent([FloaterTasksWidgetTaskSnapshot].self, forKey: .tasks) ?? []
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        generatedAtEpochMs = try container.decode(Int64.self, forKey: .generatedAtEpochMs)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? FloaterTasksWidgetSnapshotStore.defaultTitle
        status = (try? container.decodeIfPresent(FloaterTasksWidgetSnapshotStatus.self, forKey: .status)) ?? (decodedTasks.isEmpty ? .empty : .tasks)
        taskCount = try container.decodeIfPresent(Int.self, forKey: .taskCount) ?? decodedTasks.count
        tasks = decodedTasks
    }
}

struct FloaterTasksWidgetTaskSnapshot: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let priority: String
}

enum FloaterTasksWidgetSnapshotStatus: String, Codable, Equatable {
    case setup
    case empty
    case tasks
}

enum FloaterTasksWidgetSnapshotStore {
    static let snapshotSchemaVersion = 1
    static let widgetKind = "FloaterTasksWidget"
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let snapshotKey = "tday.widget.floaterTasksSnapshot"
    static let defaultTitle = "Floater Tasks"
    static let taskLimit = 50

    static func makeSnapshot(
        from state: OfflineSyncState,
        workspaceConfigured: Bool = true,
        now: Date = Date()
    ) -> FloaterTasksWidgetSnapshot {
        guard workspaceConfigured else {
            return FloaterTasksWidgetSnapshot(
                generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
                title: defaultTitle,
                status: .setup,
                taskCount: 0,
                tasks: []
            )
        }

        let floaterTasks = state.floaters
            .filter { !$0.completed }
            .sorted(by: floaterWidgetSortPrecedes)

        return FloaterTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: defaultTitle,
            status: floaterTasks.isEmpty ? .empty : .tasks,
            taskCount: floaterTasks.count,
            tasks: floaterTasks.prefix(taskLimit).map {
                FloaterTasksWidgetTaskSnapshot(
                    id: $0.id,
                    title: $0.title,
                    priority: $0.priority
                )
            }
        )
    }

    static func saveFloaterTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        guard let data = try? JSONEncoder().encode(snapshot) else {
            return
        }

        let stores = defaultsStores()
        stores.forEach { store in
            store.set(data, forKey: snapshotKey)
        }

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
        #endif
    }

    static func loadSnapshot() -> FloaterTasksWidgetSnapshot? {
        for store in defaultsStores() {
            guard let data = store.data(forKey: snapshotKey),
                  let snapshot = try? JSONDecoder().decode(FloaterTasksWidgetSnapshot.self, from: data) else {
                continue
            }
            return snapshot
        }
        return nil
    }

    private static func defaultsStores() -> [UserDefaults] {
        var stores = [UserDefaults]()
        if let shared = UserDefaults(suiteName: appGroupSuiteName) {
            stores.append(shared)
        }
        stores.append(.standard)
        return stores
    }

    private static func floaterWidgetSortPrecedes(_ lhs: CachedFloaterRecord, _ rhs: CachedFloaterRecord) -> Bool {
        if lhs.pinned != rhs.pinned {
            return lhs.pinned && !rhs.pinned
        }
        let lhsRank = floaterWidgetPriorityRank(lhs.priority)
        let rhsRank = floaterWidgetPriorityRank(rhs.priority)
        if lhsRank != rhsRank {
            return lhsRank < rhsRank
        }
        if lhs.title.localizedCaseInsensitiveCompare(rhs.title) != .orderedSame {
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }
        return lhs.id < rhs.id
    }

    private static func floaterWidgetPriorityRank(_ priority: String) -> Int {
        if TaskPriorityDisplay.isUrgent(priority) {
            return 0
        }
        if TaskPriorityDisplay.isImportant(priority) {
            return 1
        }
        return 2
    }
}

/// App-side twin of the widget's pending-completion queue (widgets v2). The
/// widget's check ring runs in a process with no cache access, so a tap only
/// records `{kind, id}` under this app-group key; the app drains the queue
/// through TodoRepository's normal complete path when it activates. Key and
/// entry shape must stay in lockstep with WidgetPendingCompletionStore in
/// TdayWidget/TodayTasksWidget.swift.
enum WidgetPendingCompletionQueue {
    static let queueKey = "tday.widget.pendingCompletions"
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let todoKind = "todo"
    static let floaterKind = "floater"

    struct Entry: Codable, Equatable {
        let kind: String
        let id: String
    }

    /// Removes and returns the queued entries. The queue clears before the
    /// repository applies them, so a widget tap landing mid-drain starts a
    /// fresh queue for the next drain instead of being wiped unseen.
    static func drain() -> [Entry] {
        let store = UserDefaults(suiteName: appGroupSuiteName) ?? .standard
        guard let data = store.data(forKey: queueKey),
              let entries = try? JSONDecoder().decode([Entry].self, from: data),
              !entries.isEmpty else {
            return []
        }
        store.removeObject(forKey: queueKey)
        return entries
    }
}
