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
    /// Per-todo-list breakdown (R7 configurable widgets): the SAME due-today-or-overdue todos
    /// as `tasks`, but scoped to one list and keyed by that list's id, so a widget instance
    /// configured to a specific todo list can read just its slice without a second file.
    /// Wider window than `tasks` (which is strictly "due today") because a per-list widget is
    /// the user's whole view of that list, not a slice of a global aggregate — see
    /// `TodayTasksWidgetSnapshotStore.makeSnapshot`, which computes it as due-today-or-overdue.
    /// Defaulted so snapshots persisted before this field existed still decode (as empty —
    /// those widgets simply show "no tasks" until the next save, at most one state change away).
    let perList: [String: TodayTasksWidgetPerListSnapshot]

    init(
        schemaVersion: Int = TodayTasksWidgetSnapshotStore.snapshotSchemaVersion,
        generatedAtEpochMs: Int64,
        title: String,
        status: TodayTasksWidgetSnapshotStatus,
        taskCount: Int,
        tasks: [TodayTasksWidgetTaskSnapshot],
        perList: [String: TodayTasksWidgetPerListSnapshot] = [:]
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAtEpochMs = generatedAtEpochMs
        self.title = title
        self.status = status
        self.taskCount = taskCount
        self.tasks = tasks
        self.perList = perList
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
        perList = try container.decodeIfPresent([String: TodayTasksWidgetPerListSnapshot].self, forKey: .perList) ?? [:]
    }

    /// True when the DISPLAYED content matches, ignoring `generatedAtEpochMs` (which changes
    /// on every rebuild). Used to skip needless WidgetKit reloads on a background sync that
    /// didn't alter what the widget actually shows.
    func hasSameContent(as other: TodayTasksWidgetSnapshot) -> Bool {
        schemaVersion == other.schemaVersion &&
            title == other.title &&
            status == other.status &&
            taskCount == other.taskCount &&
            tasks == other.tasks &&
            perList == other.perList
    }
}

/// One todo list's slice of `TodayTasksWidgetSnapshot.perList`: the display-capped task rows
/// (`tasks`, capped at `TodayTasksWidgetSnapshotStore.perListTaskLimit`) plus the list's TRUE
/// due-today-or-overdue count (`totalCount`), mirroring how the top-level snapshot separates
/// `tasks` (capped) from `taskCount` (true) so the widget's count pill stays accurate even when
/// a list has more open items than the display cap.
struct TodayTasksWidgetPerListSnapshot: Codable, Equatable {
    let totalCount: Int
    let tasks: [TodayTasksWidgetTaskSnapshot]
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
    // Backend-completion payload (widgets v2 instant sync): the CANONICAL id the
    // /api/todo/complete endpoint expects, plus the recurring-instance date. `id`
    // (the display id) is not always the canonical id for recurring instances, so
    // the widget carries both. Defaulted so snapshots persisted before these
    // existed still decode (canonicalId falls back to the display id).
    let canonicalId: String
    let instanceDateEpochMs: Int64?

    init(
        id: String,
        title: String,
        dueEpochMs: Int64,
        priority: String,
        description: String? = nil,
        pinned: Bool = false,
        updatedAtEpochMs: Int64? = nil,
        canonicalId: String? = nil,
        instanceDateEpochMs: Int64? = nil
    ) {
        self.id = id
        self.title = title
        self.dueEpochMs = dueEpochMs
        self.priority = priority
        self.description = description
        self.pinned = pinned
        self.updatedAtEpochMs = updatedAtEpochMs
        self.canonicalId = canonicalId ?? id
        self.instanceDateEpochMs = instanceDateEpochMs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedId = try container.decode(String.self, forKey: .id)
        id = decodedId
        title = try container.decode(String.self, forKey: .title)
        dueEpochMs = try container.decode(Int64.self, forKey: .dueEpochMs)
        priority = try container.decode(String.self, forKey: .priority)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        updatedAtEpochMs = try container.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs)
        canonicalId = try container.decodeIfPresent(String.self, forKey: .canonicalId) ?? decodedId
        instanceDateEpochMs = try container.decodeIfPresent(Int64.self, forKey: .instanceDateEpochMs)
    }
}

enum TodayTasksWidgetSnapshotStatus: String, Codable, Equatable {
    case setup
    case empty
    case tasks
}

/// Protected on-disk home for the widget CONTENT snapshots — task titles, notes and due
/// times, i.e. exactly the text the user typed.
///
/// These used to be written as plain JSON into App Group + standard UserDefaults. A
/// UserDefaults plist is unencrypted, gets only default protection, and lands in device
/// backups, so anyone with the device or a backup could read the task text. This is the
/// same mechanism `WidgetBackendSession` already uses for the session cookie.
///
/// `.completeUntilFirstUserAuthentication` is REQUIRED here, not a weaker compromise: the
/// widget has to keep rendering while the device is locked, and `.complete` would make the
/// file unreadable exactly then. The tradeoff is that between a reboot and the first unlock
/// the widget falls back to its setup/empty state — the same deal the session file takes.
enum WidgetSnapshotFileStore {
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let todayFileName = "widget-today-snapshot.json"
    static let floaterFileName = "widget-floater-snapshot.json"
    /// Lightweight catalog (id/name/kind, no task content) of every todo list and floater
    /// list, written alongside the two snapshots above so the widget CONFIGURATION picker
    /// (per-list widgets, R7) can list choices without the extension touching AppContainer /
    /// SwiftData. See `WidgetConfigurableListsStore` below for the writer and
    /// `TdayWidgetListEntityQuery` in TdayWidget/TodayTasksWidget.swift for the reader.
    static let listsFileName = "widget-lists-snapshot.json"

    static func read(_ fileName: String) -> Data? {
        guard let fileURL = fileURL(fileName) else {
            return nil
        }
        return try? Data(contentsOf: fileURL)
    }

    static func write(_ data: Data, to fileName: String) {
        guard let fileURL = fileURL(fileName) else {
            return
        }
        do {
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            // Keep task text out of device backups. A protected-until-first-unlock file is
            // still written in the clear into an UNENCRYPTED Finder/iTunes backup, which
            // would hand over the very content this move exists to protect. An atomic write
            // replaces the file, so the flag has to be re-applied every time.
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            var mutableURL = fileURL
            try? mutableURL.setResourceValues(resourceValues)
        } catch {
            // Best-effort: the widget keeps showing its previous timeline entry.
        }
    }

    private static func fileURL(_ fileName: String) -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupSuiteName)?
            .appendingPathComponent(fileName)
    }
}

/// One row of the widget's list-picker catalog: no task content, just enough to populate and
/// label the "Edit Widget" list chooser. `kind` is the raw string form of `WidgetListKind`
/// (defined on the widget-extension side, TdayWidget/TodayTasksWidget.swift) — kept as a plain
/// String here so the App target never needs to depend on that extension-only type; the two
/// must stay in lockstep ("todo" / "floater").
struct WidgetConfigurableListEntry: Codable, Equatable {
    let id: String
    let name: String
    let kind: String
}

/// Writer for `WidgetSnapshotFileStore.listsFileName` (R7 configurable widgets): every todo
/// list and floater list the user has, with no task content, so the widget CONFIGURATION
/// picker (`TdayWidgetListEntityQuery` in the widget extension) can list and label choices
/// without the extension touching `AppContainer`/SwiftData — the same file-handoff shape the
/// task snapshots already use, per `WidgetSnapshotFileStore`'s doc comment.
enum WidgetConfigurableListsStore {
    static func save(from state: OfflineSyncState) {
        let entries =
            state.lists.map { WidgetConfigurableListEntry(id: $0.id, name: $0.name, kind: "todo") } +
            state.floaterLists.map { WidgetConfigurableListEntry(id: $0.id, name: $0.name, kind: "floater") }
        guard let data = try? JSONEncoder().encode(entries) else {
            return
        }
        WidgetSnapshotFileStore.write(data, to: WidgetSnapshotFileStore.listsFileName)
    }
}

enum TodayTasksWidgetSnapshotStore {
    static let snapshotSchemaVersion = 2
    static let widgetKind = "TodayTasksWidget"
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let snapshotFileName = WidgetSnapshotFileStore.todayFileName
    /// Pre-migration home of the snapshot (unencrypted UserDefaults). Read once on the first
    /// load after the upgrade, then deleted — see `drainLegacyDefaultsSnapshot()`.
    static let legacySnapshotKey = "tday.widget.todayTasksSnapshot"
    static let defaultTitle = "Today's Tasks"
    static let taskLimit = 50
    /// Display cap for ONE list's slice of `perList`. Smaller than the global `taskLimit`
    /// because a single list rarely has dozens of due/overdue items, and this map holds one
    /// such array per todo list — keeping it small bounds both the on-disk file and the
    /// WatchConnectivity application-context payload `WatchSessionManager` mirrors it into.
    static let perListTaskLimit = 20

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

        func makeTaskSnapshot(_ record: CachedTodoRecord) -> TodayTasksWidgetTaskSnapshot {
            TodayTasksWidgetTaskSnapshot(
                id: record.id,
                title: record.title,
                dueEpochMs: record.dueEpochMs ?? dayStartEpochMs,
                priority: record.priority,
                description: record.description.map(flattenNotesToPlainText),
                pinned: record.pinned,
                updatedAtEpochMs: record.updatedAtEpochMs > 0 ? record.updatedAtEpochMs : nil,
                canonicalId: record.canonicalId,
                instanceDateEpochMs: record.instanceDateEpochMs
            )
        }

        // Per-list breakdown (R7 configurable widgets): every todo list's own due-today-OR-
        // OVERDUE pending todos, independent of the Focus filter above (a widget explicitly
        // configured to one list shows that list, full stop — Focus is a Today-feed concept).
        // Overdue is included (dueEpochMs < dayEnd, no lower bound) because a per-list widget
        // is the user's whole window into that list, unlike the global Today aggregate which
        // is deliberately "due today" only.
        var perList: [String: TodayTasksWidgetPerListSnapshot] = [:]
        for list in state.lists {
            let listTodos = TaskSortEngine.sortedTodos(
                state.todos.filter { record in
                    guard record.listId == list.id, !record.completed, let dueEpochMs = record.dueEpochMs else {
                        return false
                    }
                    return dueEpochMs < dayEndEpochMs
                },
                key: taskSortKey
            )
            guard !listTodos.isEmpty else { continue }
            perList[list.id] = TodayTasksWidgetPerListSnapshot(
                totalCount: listTodos.count,
                tasks: listTodos.prefix(perListTaskLimit).map(makeTaskSnapshot)
            )
        }

        return TodayTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: defaultTitle,
            status: todayTasks.isEmpty ? .empty : .tasks,
            taskCount: todayTasks.count,
            tasks: todayTasks.prefix(taskLimit).map(makeTaskSnapshot),
            perList: perList
        )
    }

    static func saveTodayTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        // The lists catalog (id/name/kind, no task content) backs the widget CONFIGURATION
        // picker and has no bearing on `hasSameContent`, so it is written unconditionally —
        // cheap, and keeps a renamed/added/removed list visible to "Edit Widget" promptly.
        WidgetConfigurableListsStore.save(from: state)
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

        WidgetSnapshotFileStore.write(data, to: snapshotFileName)

        #if canImport(WidgetKit)
        // Per-list widgets (R7) let EITHER widget kind render EITHER shape (a todo list picked
        // from the "Floater Tasks" gallery slot still renders due-date-shaped, and vice versa),
        // so a change here can affect a "FloaterTasksWidget" instance too — reload both kinds
        // rather than just `widgetKind`.
        WidgetCenter.shared.reloadAllTimelines()
        #endif

        // Mirror the same Today snapshot to a paired Apple Watch (R6-4).
        WatchSessionManager.shared.syncTodaySnapshot()
    }

    static func loadSnapshot() -> TodayTasksWidgetSnapshot? {
        // Runs before the file read so an upgrade never leaves the old plaintext behind,
        // whichever copy ends up being the newer one.
        let legacy = drainLegacyDefaultsSnapshot()
        if let data = WidgetSnapshotFileStore.read(snapshotFileName),
           let snapshot = try? JSONDecoder().decode(TodayTasksWidgetSnapshot.self, from: data) {
            return snapshot
        }
        return legacy
    }

    /// One-shot migration off UserDefaults: take the old copy, mirror it into the protected
    /// file (so the widget keeps rendering across the upgrade), and delete the key from BOTH
    /// defaults so the plaintext task titles stop lingering in a backed-up plist. A no-op on
    /// every later call — the keys are gone.
    @discardableResult
    private static func drainLegacyDefaultsSnapshot() -> TodayTasksWidgetSnapshot? {
        var legacyData: Data?
        for store in legacyDefaultsStores() {
            guard let data = store.data(forKey: legacySnapshotKey) else {
                continue
            }
            if legacyData == nil {
                legacyData = data
            }
            store.removeObject(forKey: legacySnapshotKey)
        }
        guard let legacyData,
              let snapshot = try? JSONDecoder().decode(TodayTasksWidgetSnapshot.self, from: legacyData) else {
            return nil
        }
        if WidgetSnapshotFileStore.read(snapshotFileName) == nil {
            WidgetSnapshotFileStore.write(legacyData, to: snapshotFileName)
        }
        return snapshot
    }

    private static func legacyDefaultsStores() -> [UserDefaults] {
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
    /// Per-floater-list breakdown (R7 configurable widgets): the same pending floaters as
    /// `tasks`, scoped to one floater list and keyed by its id. See the twin field on
    /// `TodayTasksWidgetSnapshot` for why this exists.
    let perList: [String: FloaterTasksWidgetPerListSnapshot]

    init(
        schemaVersion: Int = FloaterTasksWidgetSnapshotStore.snapshotSchemaVersion,
        generatedAtEpochMs: Int64,
        title: String,
        status: FloaterTasksWidgetSnapshotStatus,
        taskCount: Int,
        tasks: [FloaterTasksWidgetTaskSnapshot],
        perList: [String: FloaterTasksWidgetPerListSnapshot] = [:]
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAtEpochMs = generatedAtEpochMs
        self.title = title
        self.status = status
        self.taskCount = taskCount
        self.tasks = tasks
        self.perList = perList
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
        perList = try container.decodeIfPresent([String: FloaterTasksWidgetPerListSnapshot].self, forKey: .perList) ?? [:]
    }

    /// True when the DISPLAYED content matches, ignoring `generatedAtEpochMs`. Lets a
    /// background sync that didn't change the floater list leave the widget untouched.
    func hasSameContent(as other: FloaterTasksWidgetSnapshot) -> Bool {
        schemaVersion == other.schemaVersion &&
            title == other.title &&
            status == other.status &&
            taskCount == other.taskCount &&
            tasks == other.tasks &&
            perList == other.perList
    }
}

/// One floater list's slice of `FloaterTasksWidgetSnapshot.perList` — see the todo twin,
/// `TodayTasksWidgetPerListSnapshot`, for why `totalCount` is tracked separately from the
/// display-capped `tasks` array.
struct FloaterTasksWidgetPerListSnapshot: Codable, Equatable {
    let totalCount: Int
    let tasks: [FloaterTasksWidgetTaskSnapshot]
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
    // Backend-completion payload (widgets v2 instant sync): the CANONICAL id the
    // /api/floater/complete endpoint expects. Floaters have no instance date.
    // Defaulted so snapshots persisted before this existed still decode
    // (canonicalId falls back to the display id).
    let canonicalId: String

    init(
        id: String,
        title: String,
        priority: String,
        pinned: Bool = false,
        updatedAtEpochMs: Int64? = nil,
        canonicalId: String? = nil
    ) {
        self.id = id
        self.title = title
        self.priority = priority
        self.pinned = pinned
        self.updatedAtEpochMs = updatedAtEpochMs
        self.canonicalId = canonicalId ?? id
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedId = try container.decode(String.self, forKey: .id)
        id = decodedId
        title = try container.decode(String.self, forKey: .title)
        priority = try container.decode(String.self, forKey: .priority)
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        updatedAtEpochMs = try container.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMs)
        canonicalId = try container.decodeIfPresent(String.self, forKey: .canonicalId) ?? decodedId
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
    static let snapshotFileName = WidgetSnapshotFileStore.floaterFileName
    /// Pre-migration home of the snapshot (unencrypted UserDefaults). See the Today store.
    static let legacySnapshotKey = "tday.widget.floaterTasksSnapshot"
    static let defaultTitle = "Floater Tasks"
    static let taskLimit = 50
    /// Display cap for ONE list's slice of `perList` — see the todo twin for why it's smaller
    /// than `taskLimit`.
    static let perListTaskLimit = 20

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

        func makeTaskSnapshot(_ record: CachedFloaterRecord) -> FloaterTasksWidgetTaskSnapshot {
            FloaterTasksWidgetTaskSnapshot(
                id: record.id,
                title: record.title,
                priority: record.priority,
                pinned: record.pinned,
                updatedAtEpochMs: record.updatedAtEpochMs > 0 ? record.updatedAtEpochMs : nil,
                canonicalId: record.canonicalId
            )
        }

        // Per-list breakdown (R7 configurable widgets) — see the todo twin in
        // TodayTasksWidgetSnapshotStore.makeSnapshot for the rationale.
        var perList: [String: FloaterTasksWidgetPerListSnapshot] = [:]
        for list in state.floaterLists {
            let listFloaters = TaskSortEngine.sortedFloaters(
                state.floaters.filter { $0.listId == list.id && !$0.completed },
                key: taskSortKey
            )
            guard !listFloaters.isEmpty else { continue }
            perList[list.id] = FloaterTasksWidgetPerListSnapshot(
                totalCount: listFloaters.count,
                tasks: listFloaters.prefix(perListTaskLimit).map(makeTaskSnapshot)
            )
        }

        return FloaterTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: defaultTitle,
            status: floaterTasks.isEmpty ? .empty : .tasks,
            taskCount: floaterTasks.count,
            tasks: floaterTasks.prefix(taskLimit).map(makeTaskSnapshot),
            perList: perList
        )
    }

    static func saveFloaterTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        // See saveTodayTasks: written unconditionally, cheap, keeps the widget configuration
        // picker's list names/choices fresh independent of task-content change detection.
        WidgetConfigurableListsStore.save(from: state)
        // Conditional reload: skip the write + WidgetKit reload when the displayed floater
        // content is unchanged (see saveTodayTasks). A background sync that didn't touch the
        // floater list leaves the widget untouched while the app still holds the latest state.
        if let existing = loadSnapshot(), existing.hasSameContent(as: snapshot) {
            return
        }
        guard let data = try? JSONEncoder().encode(snapshot) else {
            return
        }

        WidgetSnapshotFileStore.write(data, to: snapshotFileName)

        #if canImport(WidgetKit)
        // See saveTodayTasks: a per-list widget can render either shape from either gallery
        // kind now, so both kinds need reloading, not just `widgetKind`.
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    static func loadSnapshot() -> FloaterTasksWidgetSnapshot? {
        let legacy = drainLegacyDefaultsSnapshot()
        if let data = WidgetSnapshotFileStore.read(snapshotFileName),
           let snapshot = try? JSONDecoder().decode(FloaterTasksWidgetSnapshot.self, from: data) {
            return snapshot
        }
        return legacy
    }

    /// One-shot migration off UserDefaults — see the Today store's twin.
    @discardableResult
    private static func drainLegacyDefaultsSnapshot() -> FloaterTasksWidgetSnapshot? {
        var legacyData: Data?
        for store in legacyDefaultsStores() {
            guard let data = store.data(forKey: legacySnapshotKey) else {
                continue
            }
            if legacyData == nil {
                legacyData = data
            }
            store.removeObject(forKey: legacySnapshotKey)
        }
        guard let legacyData,
              let snapshot = try? JSONDecoder().decode(FloaterTasksWidgetSnapshot.self, from: legacyData) else {
            return nil
        }
        if WidgetSnapshotFileStore.read(snapshotFileName) == nil {
            WidgetSnapshotFileStore.write(legacyData, to: snapshotFileName)
        }
        return snapshot
    }

    private static func legacyDefaultsStores() -> [UserDefaults] {
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

/// App-side writer for the shared backend session the widget uses to fire an
/// authenticated completion straight from a tapped check ring (widgets v2 instant
/// sync). The widget process has no login session of its own, so the app hands it
/// the base URL + a pre-built Cookie header through the App Group container.
///
/// The session cookie is sensitive, so it is stored in a file (NOT UserDefaults,
/// which is unencrypted on disk) with `.completeUntilFirstUserAuthentication`
/// protection — encrypted at rest, readable by the widget after the first unlock,
/// mirroring the app's AfterFirstUnlock keychain semantics. A widget-side reader
/// (`WidgetBackendSession.load()`) is duplicated in TdayWidget/TodayTasksWidget.swift.
enum WidgetBackendSession {
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let fileName = "widget-backend-session.json"

    /// Mirrors CookieStore.authCookieNames. The session cookie is the ONLY one that
    /// authenticates; auth.js also sets `authjs.csrf-token` / `authjs.callback-url`,
    /// which linger after the session cookie expires.
    private static let authCookieNames: Set<String> = [
        "authjs.session-token",
        "__Secure-authjs.session-token",
    ]

    struct Payload: Codable {
        let baseURL: String
        let cookieHeader: String
        /// The host's TOFU-pinned public-key fingerprint, when the app has one (i.e.
        /// a self-signed / privately-issued cert). The widget has no keychain access,
        /// so without this it could not reproduce the app's pinning and its TLS
        /// handshake to such a server would simply fail. Defaulted for old payloads.
        let pinnedFingerprint: String?

        init(baseURL: String, cookieHeader: String, pinnedFingerprint: String? = nil) {
            self.baseURL = baseURL
            self.cookieHeader = cookieHeader
            self.pinnedFingerprint = pinnedFingerprint
        }
    }

    private static func fileURL() -> URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupSuiteName)?
            .appendingPathComponent(fileName)
    }

    /// Captures the current cookies for `baseURL` and persists them (encrypted at
    /// rest) so the widget can authenticate its instant completion call. No-op if
    /// the App Group container is unavailable; clears the session when there is no
    /// live auth cookie to hand over.
    ///
    /// `pinnedFingerprint` carries the app's TOFU pin for this host (nil for
    /// system-trusted or local servers, which need no pin).
    static func save(baseURL: URL, pinnedFingerprint: String? = nil) {
        guard let fileURL = fileURL() else {
            return
        }
        let cookies = HTTPCookieStorage.shared.cookies(for: baseURL) ?? []
        // Require the SESSION cookie specifically — not merely a non-empty header.
        // CookieStore.removeExpiredAuthCookies() drops only the expired session
        // cookie, leaving csrf/callback-url behind; keying off "any cookie" would
        // keep overwriting the file with a session-less header, so every widget tap
        // would 401 forever (silently) instead of clearing the stale session here.
        guard cookies.contains(where: { authCookieNames.contains($0.name) }) else {
            clear()
            return
        }
        let cookieHeader = cookies
            .map { "\($0.name)=\($0.value)" }
            .joined(separator: "; ")
        let payload = Payload(
            baseURL: baseURL.absoluteString,
            cookieHeader: cookieHeader,
            pinnedFingerprint: pinnedFingerprint
        )
        guard let data = try? JSONEncoder().encode(payload) else {
            return
        }
        do {
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            // Keep the session token out of device backups. Protected-until-first-unlock
            // files still land in the clear inside an UNENCRYPTED Finder/iTunes backup,
            // whereas the Keychain copy this mirrors is sealed to the device. Excluding
            // it keeps the widget's copy device-bound like the original.
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            var mutableURL = fileURL
            try? mutableURL.setResourceValues(resourceValues)
        } catch {
            // Best-effort: the pending-completion queue remains the fallback.
        }
    }

    static func clear() {
        guard let fileURL = fileURL() else {
            return
        }
        try? FileManager.default.removeItem(at: fileURL)
    }
}
