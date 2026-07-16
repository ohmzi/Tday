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

    /// True when the DISPLAYED content matches, ignoring `generatedAtEpochMs` (which changes
    /// on every rebuild). Used to skip needless WidgetKit reloads on a background sync that
    /// didn't alter what the widget actually shows.
    func hasSameContent(as other: TodayTasksWidgetSnapshot) -> Bool {
        schemaVersion == other.schemaVersion &&
            title == other.title &&
            status == other.status &&
            taskCount == other.taskCount &&
            tasks == other.tasks
    }
}

struct TodayTasksWidgetTaskSnapshot: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
    // Optional so previously persisted snapshots without this field still decode (as nil).
    let description: String?
    // Inputs for the fixed ordering (TaskSortEngine), so the widget row carries
    // enough to sort identically to the app. Defaulted/optional so snapshots
    // persisted before these existed still decode.
    let pinned: Bool
    let updatedAtEpochMs: Int64?

    init(
        id: String,
        title: String,
        dueEpochMs: Int64,
        priority: String,
        description: String? = nil,
        pinned: Bool = false,
        updatedAtEpochMs: Int64? = nil
    ) {
        self.id = id
        self.title = title
        self.dueEpochMs = dueEpochMs
        self.priority = priority
        self.description = description
        self.pinned = pinned
        self.updatedAtEpochMs = updatedAtEpochMs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        title = try container.decode(String.self, forKey: .title)
        dueEpochMs = try container.decode(Int64.self, forKey: .dueEpochMs)
        priority = try container.decode(String.self, forKey: .priority)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        updatedAtEpochMs = try container.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs)
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
        // Fixed TODO ordering (TaskSortEngine), identical to the app, applied
        // before the display cap so the widget shows the same leading tasks.
        let todayTasks = TaskSortEngine.sortedTodos(
            state.todos.filter { record in
                guard let dueEpochMs = record.dueEpochMs else {
                    return false
                }
                guard !record.completed && dueEpochMs >= dayStartEpochMs && dueEpochMs < dayEndEpochMs else {
                    return false
                }
                guard let focusListIDs else { return true }
                return record.listId.map(focusListIDs.contains) ?? false
            },
            key: taskSortKey
        )

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
                    description: $0.description,
                    pinned: $0.pinned,
                    updatedAtEpochMs: $0.updatedAtEpochMs > 0 ? $0.updatedAtEpochMs : nil
                )
            }
        )
    }

    static func saveTodayTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        // Conditional reload: if the DISPLAYED content is unchanged (ignoring the volatile
        // generatedAt timestamp), skip the write + WidgetKit reload. This is what lets a
        // background sync that only touched non-today data leave the widget untouched while
        // the app still holds the latest state.
        if let existing = loadSnapshot(), existing.hasSameContent(as: snapshot) {
            return
        }
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

    /// True when the DISPLAYED content matches, ignoring `generatedAtEpochMs`. Lets a
    /// background sync that didn't change the floater list leave the widget untouched.
    func hasSameContent(as other: FloaterTasksWidgetSnapshot) -> Bool {
        schemaVersion == other.schemaVersion &&
            title == other.title &&
            status == other.status &&
            taskCount == other.taskCount &&
            tasks == other.tasks
    }
}

struct FloaterTasksWidgetTaskSnapshot: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let priority: String
    // Inputs for the fixed ordering (TaskSortEngine), so the widget row carries
    // enough to sort identically to the app. Defaulted/optional so snapshots
    // persisted before these existed still decode.
    let pinned: Bool
    let updatedAtEpochMs: Int64?

    init(
        id: String,
        title: String,
        priority: String,
        pinned: Bool = false,
        updatedAtEpochMs: Int64? = nil
    ) {
        self.id = id
        self.title = title
        self.priority = priority
        self.pinned = pinned
        self.updatedAtEpochMs = updatedAtEpochMs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        title = try container.decode(String.self, forKey: .title)
        priority = try container.decode(String.self, forKey: .priority)
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        updatedAtEpochMs = try container.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs)
    }
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

        // Fixed FLOATER ordering (TaskSortEngine), identical to the app, applied
        // before the display cap so the widget shows the same leading tasks.
        let floaterTasks = TaskSortEngine.sortedFloaters(
            state.floaters.filter { !$0.completed },
            key: taskSortKey
        )

        return FloaterTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: defaultTitle,
            status: floaterTasks.isEmpty ? .empty : .tasks,
            taskCount: floaterTasks.count,
            tasks: floaterTasks.prefix(taskLimit).map {
                FloaterTasksWidgetTaskSnapshot(
                    id: $0.id,
                    title: $0.title,
                    priority: $0.priority,
                    pinned: $0.pinned,
                    updatedAtEpochMs: $0.updatedAtEpochMs > 0 ? $0.updatedAtEpochMs : nil
                )
            }
        )
    }

    static func saveFloaterTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        // Conditional reload: skip the write + WidgetKit reload when the displayed floater
        // content is unchanged (see saveTodayTasks). A background sync that didn't touch the
        // floater list leaves the widget untouched while the app still holds the latest state.
        if let existing = loadSnapshot(), existing.hasSameContent(as: snapshot) {
            return
        }
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
