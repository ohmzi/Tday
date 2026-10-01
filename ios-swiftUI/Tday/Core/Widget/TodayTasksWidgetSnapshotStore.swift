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
    /// The local day `status`/`taskCount`/`tasks` describe, `[dayStart, dayEnd)`. Nil in
    /// snapshots written before it was recorded (schema 2), which the widget then dates by
    /// `generatedAtEpochMs` instead — see `TodayWidgetDayWindow.coveredDays`.
    let dayStartEpochMs: Int64?
    let dayEndEpochMs: Int64?
    /// The same "due today" selection for each of the next `TodayWidgetDayWindow.upcomingDayCount`
    /// local days, so the widget can turn over at midnight without the app writing anything —
    /// offline, nothing does. Defaulted so older snapshots still decode (as no upcoming days).
    let upcomingDays: [TodayTasksWidgetDaySnapshot]
    /// Tasks due on this snapshot's own day that are already completed — the done half of the
    /// header's "2 of 5 done", whose total is this plus `taskCount`. Counted by due date, not
    /// completion time, so finishing another day's task never fills today's ring. Mirrors
    /// Android's `WidgetSnapshot.completedCount`.
    let completedCount: Int
    /// Open tasks due before this snapshot's own day — the app's Today "Earlier" bucket, shown
    /// under an Overdue label below the day's own tasks and never part of `taskCount`. True count
    /// beside display-capped rows, like `taskCount`/`tasks`.
    let overdueCount: Int
    let overdueTasks: [TodayTasksWidgetTaskSnapshot]
    /// Every open task of each todo list, whatever day it is due, keyed by list id — what the
    /// List widget shows, which is the whole list rather than `perList`'s due-today-or-overdue
    /// slice of it (schema 5). Same capped-rows-beside-true-count shape; a list with nothing open
    /// has no entry. Defaulted so older snapshots decode (as every list empty until the next save).
    let openByList: [String: TodayTasksWidgetPerListSnapshot]

    init(
        schemaVersion: Int = TodayTasksWidgetSnapshotStore.snapshotSchemaVersion,
        generatedAtEpochMs: Int64,
        title: String,
        status: TodayTasksWidgetSnapshotStatus,
        taskCount: Int,
        tasks: [TodayTasksWidgetTaskSnapshot],
        perList: [String: TodayTasksWidgetPerListSnapshot] = [:],
        dayStartEpochMs: Int64? = nil,
        dayEndEpochMs: Int64? = nil,
        upcomingDays: [TodayTasksWidgetDaySnapshot] = [],
        completedCount: Int = 0,
        overdueCount: Int = 0,
        overdueTasks: [TodayTasksWidgetTaskSnapshot] = [],
        openByList: [String: TodayTasksWidgetPerListSnapshot] = [:]
    ) {
        self.schemaVersion = schemaVersion
        self.generatedAtEpochMs = generatedAtEpochMs
        self.title = title
        self.status = status
        self.taskCount = taskCount
        self.tasks = tasks
        self.perList = perList
        self.dayStartEpochMs = dayStartEpochMs
        self.dayEndEpochMs = dayEndEpochMs
        self.upcomingDays = upcomingDays
        self.completedCount = completedCount
        self.overdueCount = overdueCount
        self.overdueTasks = overdueTasks
        self.openByList = openByList
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
        dayStartEpochMs = try container.decodeIfPresent(Int64.self, forKey: .dayStartEpochMs)
        dayEndEpochMs = try container.decodeIfPresent(Int64.self, forKey: .dayEndEpochMs)
        upcomingDays = try container.decodeIfPresent([TodayTasksWidgetDaySnapshot].self, forKey: .upcomingDays) ?? []
        completedCount = try container.decodeIfPresent(Int.self, forKey: .completedCount) ?? 0
        overdueCount = try container.decodeIfPresent(Int.self, forKey: .overdueCount) ?? 0
        overdueTasks = try container.decodeIfPresent([TodayTasksWidgetTaskSnapshot].self, forKey: .overdueTasks) ?? []
        openByList = try container.decodeIfPresent([String: TodayTasksWidgetPerListSnapshot].self, forKey: .openByList) ?? [:]
    }

    /// True when the DISPLAYED content matches, ignoring `generatedAtEpochMs` (which changes
    /// on every rebuild). Used to skip needless WidgetKit reloads on a background sync that
    /// didn't alter what the widget actually shows. The day window counts as content: a
    /// rebuild on a new local day must land even when both days hold the same tasks, or the
    /// widget keeps a window that no longer contains "now".
    func hasSameContent(as other: TodayTasksWidgetSnapshot) -> Bool {
        schemaVersion == other.schemaVersion &&
            title == other.title &&
            status == other.status &&
            taskCount == other.taskCount &&
            tasks == other.tasks &&
            perList == other.perList &&
            dayStartEpochMs == other.dayStartEpochMs &&
            dayEndEpochMs == other.dayEndEpochMs &&
            upcomingDays == other.upcomingDays &&
            completedCount == other.completedCount &&
            overdueCount == other.overdueCount &&
            overdueTasks == other.overdueTasks &&
            openByList == other.openByList
    }

    /// This snapshot minus the days it pre-computes for the widget, the overdue rows and the
    /// List widget's whole lists. The Apple Watch mirror shows `tasks` alone, so any of them
    /// would only grow its WatchConnectivity application-context payload.
    func withoutUpcomingDays() -> TodayTasksWidgetSnapshot {
        TodayTasksWidgetSnapshot(
            schemaVersion: schemaVersion,
            generatedAtEpochMs: generatedAtEpochMs,
            title: title,
            status: status,
            taskCount: taskCount,
            tasks: tasks,
            perList: perList.mapValues { TodayTasksWidgetPerListSnapshot(totalCount: $0.totalCount, tasks: $0.tasks) },
            dayStartEpochMs: dayStartEpochMs,
            dayEndEpochMs: dayEndEpochMs,
            completedCount: completedCount,
            overdueCount: overdueCount
        )
    }
}

/// One upcoming local day of `TodayTasksWidgetSnapshot.upcomingDays`: the global "due today"
/// feed as it will read on that day — true `taskCount`, display-capped `tasks` — so the widget
/// can switch to it at midnight on its own.
struct TodayTasksWidgetDaySnapshot: Codable, Equatable {
    let dayStartEpochMs: Int64
    let dayEndEpochMs: Int64
    let taskCount: Int
    let tasks: [TodayTasksWidgetTaskSnapshot]
    /// That day's own tasks already completed ahead of it, and what is overdue by then — the same
    /// fields the snapshot carries for its own day, so the header and Overdue section turn over
    /// at midnight with the rest. Defaulted so days written before them still decode.
    let completedCount: Int
    let overdueCount: Int
    let overdueTasks: [TodayTasksWidgetTaskSnapshot]

    init(
        dayStartEpochMs: Int64,
        dayEndEpochMs: Int64,
        taskCount: Int,
        tasks: [TodayTasksWidgetTaskSnapshot],
        completedCount: Int = 0,
        overdueCount: Int = 0,
        overdueTasks: [TodayTasksWidgetTaskSnapshot] = []
    ) {
        self.dayStartEpochMs = dayStartEpochMs
        self.dayEndEpochMs = dayEndEpochMs
        self.taskCount = taskCount
        self.tasks = tasks
        self.completedCount = completedCount
        self.overdueCount = overdueCount
        self.overdueTasks = overdueTasks
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        dayStartEpochMs = try container.decode(Int64.self, forKey: .dayStartEpochMs)
        dayEndEpochMs = try container.decode(Int64.self, forKey: .dayEndEpochMs)
        taskCount = try container.decode(Int.self, forKey: .taskCount)
        tasks = try container.decode([TodayTasksWidgetTaskSnapshot].self, forKey: .tasks)
        completedCount = try container.decodeIfPresent(Int.self, forKey: .completedCount) ?? 0
        overdueCount = try container.decodeIfPresent(Int.self, forKey: .overdueCount) ?? 0
        overdueTasks = try container.decodeIfPresent([TodayTasksWidgetTaskSnapshot].self, forKey: .overdueTasks) ?? []
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
    /// `totalCount` as it will read on each of the snapshot's `upcomingDays`, same order.
    let upcomingTotalCounts: [Int]
    /// Every row that makes the display cap on ANY upcoming day, in the app's fixed order. A
    /// list's window is cumulative (overdue + due that day), so on upcoming day `n` the rows
    /// due before that day's end are, in order, a list that STARTS with exactly the capped rows
    /// a rebuild on that day would write — the widget filters by due time and needs no sort.
    let upcomingTasks: [TodayTasksWidgetTaskSnapshot]

    init(
        totalCount: Int,
        tasks: [TodayTasksWidgetTaskSnapshot],
        upcomingTotalCounts: [Int] = [],
        upcomingTasks: [TodayTasksWidgetTaskSnapshot] = []
    ) {
        self.totalCount = totalCount
        self.tasks = tasks
        self.upcomingTotalCounts = upcomingTotalCounts
        self.upcomingTasks = upcomingTasks
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        totalCount = try container.decode(Int.self, forKey: .totalCount)
        tasks = try container.decode([TodayTasksWidgetTaskSnapshot].self, forKey: .tasks)
        upcomingTotalCounts = try container.decodeIfPresent([Int].self, forKey: .upcomingTotalCounts) ?? []
        upcomingTasks = try container.decodeIfPresent([TodayTasksWidgetTaskSnapshot].self, forKey: .upcomingTasks) ?? []
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
    /// The list's own glyph and colour, as the opaque KEYS the list stores ("work", "TEAL") —
    /// never a resolved asset name or hex. A key is resolved by the reader, so the widget's
    /// accent follows a palette or glyph-table revision the next time it renders; a value baked
    /// in here would be frozen at write time, and nothing re-writes this file until the app's
    /// cache changes. The resolvers are `tdayLucideListAsset` and `tdayListAccentColorOrNil`
    /// (`Tday/UI/Theme/TdayListAccent.swift`), which the widget target compiles for this.
    ///
    /// Both OPTIONAL, and that is load-bearing twice over. A catalog written before these
    /// fields existed still decodes — the synthesized decoder reads an absent optional as nil
    /// — so a widget placed before this update keeps rendering its kind's accent instead of
    /// going blank until the app next writes. And nil is also the live "this list has no
    /// colour/glyph of its own" state: the reader falls back to the widget kind's accent rather
    /// than inventing one.
    let iconKey: String?
    let colorKey: String?

    init(id: String, name: String, kind: String, iconKey: String? = nil, colorKey: String? = nil) {
        self.id = id
        self.name = name
        self.kind = kind
        self.iconKey = iconKey
        self.colorKey = colorKey
    }
}

/// Writer for `WidgetSnapshotFileStore.listsFileName` (R7 configurable widgets): every todo
/// list and floater list the user has, with no task content, so the widget CONFIGURATION
/// picker (`TdayWidgetListEntityQuery` in the widget extension) can list and label choices
/// without the extension touching `AppContainer`/SwiftData — the same file-handoff shape the
/// task snapshots already use, per `WidgetSnapshotFileStore`'s doc comment.
enum WidgetConfigurableListsStore {
    static func save(from state: OfflineSyncState) {
        // `iconKey`/`color` go across RAW — not through `tdayResolvedListIconKey`, which would
        // resolve the name-derived guess here. The guess belongs to the reader: it is display
        // only (see that function's doc), and a guess written into a file read by another
        // process is indistinguishable from a choice the user made.
        let entries =
            state.lists.map {
                WidgetConfigurableListEntry(id: $0.id, name: $0.name, kind: "todo", iconKey: $0.iconKey, colorKey: $0.color)
            } +
            state.floaterLists.map {
                WidgetConfigurableListEntry(id: $0.id, name: $0.name, kind: "floater", iconKey: $0.iconKey, colorKey: $0.color)
            }
        guard let data = try? JSONEncoder().encode(entries) else {
            return
        }
        WidgetSnapshotFileStore.write(data, to: WidgetSnapshotFileStore.listsFileName)
    }
}

/// Every widget and watch snapshot write goes through here, on one serial background queue.
///
/// These writes used to run inline in `OfflineCacheManager.saveOfflineState`, on the main actor,
/// after every save that changed content. One save meant two snapshot builds (per-list sorts, a
/// seven-day window, a pass over the whole completed history for each day, rich-note flattening
/// for every row), two catalogue writes, two file reads and decodes, and, on a change, encodes,
/// protected atomic writes, a WidgetKit reload and a watch push. That was tens of milliseconds
/// and grew with lists and history. On Undo it landed in the toast's slide-out. When ticking
/// tasks in a row it landed in the next row's strike and collapse. On a sync it landed mid-push
/// or mid-scroll. None of it has to be on main: the stores are plain enums over value types, and
/// WidgetKit and WatchConnectivity are both safe to call off it.
///
/// Why a serial `DispatchQueue` plus a single pending slot, and not a Task per save: each save
/// has to land in the order it was made, or an older state written last would show a completed
/// task as open again. Unstructured Tasks give no such order, and the runtime is free to run a
/// higher-priority job ahead of a queued lower-priority one. A serial queue is FIFO whatever the
/// QoS. Main fills the slot in save order, and each block takes whatever is newest in it, so a
/// burst of saves costs one write and an older state can never land after a newer one.
///
/// `runNow` is the barrier for callers that must return with the snapshot written. It waits
/// behind every earlier `submit`, then does its own write. `flush` is the same barrier without
/// a write of its own, for a background caller whose cache save already queued one.
final class WidgetSnapshotWriter: @unchecked Sendable {
    static let shared = WidgetSnapshotWriter()

    private let queue = DispatchQueue(label: "com.ohmz.tday.widget-snapshot", qos: .utility)
    /// Marks the queue so `runNow` can tell it is already on it and run inline. A `sync` onto a
    /// serial queue from that same queue would deadlock.
    private let queueKey = DispatchSpecificKey<Bool>()
    /// Guards `pending`. Main writes it in `submit` and the queue drains it.
    private let lock = NSLock()
    private var pending: (state: OfflineSyncState, includeFloater: Bool)?

    private init() {
        queue.setSpecific(key: queueKey, value: true)
    }

    /// Hands `state` to the queue and returns at once. Latest-wins: if an earlier state has not
    /// been written yet, this one replaces it, and a Floater write asked for by either is kept.
    func submit(_ state: OfflineSyncState, includeFloater: Bool = true) {
        lock.lock()
        pending = (state: state, includeFloater: includeFloater || (pending?.includeFloater ?? false))
        lock.unlock()
        queue.async { [self] in
            lock.lock()
            let next = pending
            pending = nil
            lock.unlock()
            // Nil means an earlier block already took the newest state and wrote it.
            guard let next else { return }
            TodayTasksWidgetSnapshotStore.writeTodayTasks(from: next.state)
            if next.includeFloater {
                FloaterTasksWidgetSnapshotStore.writeFloaterTasks(from: next.state)
            }
        }
    }

    /// Runs `work` on the queue and returns when it is done. Every block submitted before this
    /// call has run by then.
    func runNow(_ work: () -> Void) {
        if DispatchQueue.getSpecific(key: queueKey) == true {
            work()
        } else {
            queue.sync(execute: work)
        }
    }

    /// Returns once every block submitted before this call has been written. For background
    /// callers that queue writes through `submit` (via a cache save) and must not return
    /// before they land, because iOS may suspend the app right after.
    func flush() {
        runNow {}
    }
}

enum TodayTasksWidgetSnapshotStore {
    /// 3: records the local day window and carries the upcoming days (`upcomingDays`, per-list
    /// `upcomingTasks`) so the widget turns over at midnight without a write.
    /// 4: each day carries its done count and its overdue tasks (`completedCount`,
    /// `overdueCount`, `overdueTasks`) for the header ring and the Overdue section.
    /// 5: every todo list's whole open list (`openByList`) for the List widget.
    static let snapshotSchemaVersion = 5
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
    /// Display cap for each of `upcomingDays`. Smaller than `taskLimit`: the largest widget fits
    /// nine rows, and these arrays exist only for the days the app never gets to rewrite.
    static let upcomingDayTaskLimit = 20
    /// Display cap for each day's Overdue section; the true count travels beside it. Matches
    /// Android's `OVERDUE_TASK_LIMIT`.
    static let overdueTaskLimit = 20

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

        // Today first, then the upcoming days the widget falls back on if the app does not
        // write again before midnight (see TodayWidgetDayWindow).
        let days = TodayWidgetDayWindow.days(
            from: now,
            count: 1 + TodayWidgetDayWindow.upcomingDayCount,
            calendar: calendar
        )
        let dayStartEpochMs = days[0].startEpochMs
        let dayEndEpochMs = days[0].endEpochMs
        let horizonEndEpochMs = days[days.count - 1].endEpochMs

        // An active iOS Focus filter (R6-3) narrows the widget to its chosen lists.
        let focusListIDs = TdayFocusFilterStore.activeListIDs()
        // Fixed TODO ordering (TaskSortEngine), identical to the app, applied
        // before the display cap so the widget shows the same leading tasks. Sorted once
        // across every covered day: the order is total (id breaks the last tie), so each
        // day's slice of it is exactly that day's own sorted list.
        func inFocus(_ listId: String?) -> Bool {
            guard let focusListIDs else { return true }
            return listId.map(focusListIDs.contains) ?? false
        }
        // Every open task due before the last covered day ends, overdue ones included: each day's
        // feed is its own slice of this, and its Overdue section is everything before it.
        let openTasks = TaskSortEngine.sortedTodos(
            state.todos.filter { record in
                guard let dueEpochMs = record.dueEpochMs else {
                    return false
                }
                return !record.completed && dueEpochMs < horizonEndEpochMs && inFocus(record.listId)
            },
            key: taskSortKey
        )
        func feedTasksDue(on day: TodayWidgetDayWindow.Day) -> [CachedTodoRecord] {
            openTasks.filter { record in record.dueEpochMs.map(day.contains) ?? false }
        }
        /// The app's Today "Earlier" bucket as it reads on `day`: open and due before it starts.
        /// For a later day that includes today's own open tasks — nothing rewrites the snapshot
        /// unless something changes, so a task still open by then was never completed.
        func overdueTasks(before day: TodayWidgetDayWindow.Day) -> [CachedTodoRecord] {
            openTasks.filter { record in (record.dueEpochMs ?? day.startEpochMs) < day.startEpochMs }
        }
        /// Tasks due on `day` that are already done, whenever they were checked off.
        func completedCount(on day: TodayWidgetDayWindow.Day) -> Int {
            state.completedItems.reduce(into: 0) { count, record in
                if let dueEpochMs = record.dueEpochMs, day.contains(dueEpochMs), inFocus(record.listId) {
                    count += 1
                }
            }
        }
        let todayTasks = feedTasksDue(on: days[0])

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
            // Everything the list will show on any covered day, sorted once. Its window only
            // grows day to day, so each day's rows are the ones due before that day's end.
            let coveredListTodos = TaskSortEngine.sortedTodos(
                state.todos.filter { record in
                    guard record.listId == list.id, !record.completed, let dueEpochMs = record.dueEpochMs else {
                        return false
                    }
                    return dueEpochMs < horizonEndEpochMs
                },
                key: taskSortKey
            )
            guard !coveredListTodos.isEmpty else { continue }
            func listTodosDue(before endEpochMs: Int64) -> [CachedTodoRecord] {
                coveredListTodos.filter { record in (record.dueEpochMs ?? endEpochMs) < endEpochMs }
            }
            let listTodos = listTodosDue(before: dayEndEpochMs)
            var upcomingTotalCounts: [Int] = []
            var upcomingTaskIDs = Set<String>()
            for day in days.dropFirst() {
                let dayListTodos = listTodosDue(before: day.endEpochMs)
                upcomingTotalCounts.append(dayListTodos.count)
                upcomingTaskIDs.formUnion(dayListTodos.prefix(perListTaskLimit).map(\.id))
            }
            perList[list.id] = TodayTasksWidgetPerListSnapshot(
                totalCount: listTodos.count,
                tasks: listTodos.prefix(perListTaskLimit).map(makeTaskSnapshot),
                upcomingTotalCounts: upcomingTotalCounts,
                upcomingTasks: coveredListTodos.filter { upcomingTaskIDs.contains($0.id) }.map(makeTaskSnapshot)
            )
        }

        // The List widget's whole lists: every open task in the list, due any day, in the app's
        // order. Like `perList`, independent of the Focus filter — a widget set to one list shows
        // that list. A list's rows do not depend on the day (only how a row's due is labelled,
        // which the widget works out as it renders), so there is nothing to pre-compute per day.
        var openByList: [String: TodayTasksWidgetPerListSnapshot] = [:]
        let openTodosByList = Dictionary(grouping: state.todos.filter { !$0.completed && $0.dueEpochMs != nil }) { $0.listId }
        for list in state.lists {
            guard let listTodos = openTodosByList[list.id], !listTodos.isEmpty else { continue }
            let sorted = TaskSortEngine.sortedTodos(listTodos, key: taskSortKey)
            openByList[list.id] = TodayTasksWidgetPerListSnapshot(
                totalCount: sorted.count,
                tasks: sorted.prefix(perListTaskLimit).map(makeTaskSnapshot)
            )
        }

        let upcomingDays = days.dropFirst().map { day in
            let dayTasks = feedTasksDue(on: day)
            let dayOverdue = overdueTasks(before: day)
            return TodayTasksWidgetDaySnapshot(
                dayStartEpochMs: day.startEpochMs,
                dayEndEpochMs: day.endEpochMs,
                taskCount: dayTasks.count,
                tasks: dayTasks.prefix(upcomingDayTaskLimit).map(makeTaskSnapshot),
                completedCount: completedCount(on: day),
                overdueCount: dayOverdue.count,
                overdueTasks: dayOverdue.prefix(overdueTaskLimit).map(makeTaskSnapshot)
            )
        }
        let todayOverdue = overdueTasks(before: days[0])

        return TodayTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: defaultTitle,
            status: todayTasks.isEmpty ? .empty : .tasks,
            taskCount: todayTasks.count,
            tasks: todayTasks.prefix(taskLimit).map(makeTaskSnapshot),
            perList: perList,
            dayStartEpochMs: dayStartEpochMs,
            dayEndEpochMs: dayEndEpochMs,
            upcomingDays: upcomingDays,
            completedCount: completedCount(on: days[0]),
            overdueCount: todayOverdue.count,
            overdueTasks: todayOverdue.prefix(overdueTaskLimit).map(makeTaskSnapshot),
            openByList: openByList
        )
    }

    /// Writes the Today snapshot and returns once it is on disk. It runs on
    /// `WidgetSnapshotWriter`'s queue, so it lands after every write submitted before it and can
    /// never race one. For callers that must finish with the snapshot written: the Focus filter
    /// intent, and a background refresh about to hand its task back. The cache-save path uses
    /// `WidgetSnapshotWriter.submit` and does not wait; the voice-create intent, which saves
    /// through it in the background, waits with `WidgetSnapshotWriter.flush` instead.
    static func saveTodayTasks(from state: OfflineSyncState) {
        WidgetSnapshotWriter.shared.runNow { writeTodayTasks(from: state) }
    }

    /// The write itself. Runs only on `WidgetSnapshotWriter`'s queue, so it is `fileprivate`.
    /// A caller elsewhere could otherwise write outside that order and have its snapshot
    /// overwritten by an older one still queued.
    fileprivate static func writeTodayTasks(from state: OfflineSyncState) {
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

    /// See `TodayTasksWidgetSnapshotStore.saveTodayTasks`: returns once the snapshot is written,
    /// and is ordered with every other snapshot write on `WidgetSnapshotWriter`'s queue.
    static func saveFloaterTasks(from state: OfflineSyncState) {
        WidgetSnapshotWriter.shared.runNow { writeFloaterTasks(from: state) }
    }

    /// The write itself. `fileprivate` for the same reason as the Today store's twin.
    fileprivate static func writeFloaterTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        // See writeTodayTasks: written unconditionally, cheap, keeps the widget configuration
        // picker's list names/choices fresh independent of task-content change detection.
        WidgetConfigurableListsStore.save(from: state)
        // Conditional reload: skip the write + WidgetKit reload when the displayed floater
        // content is unchanged (see writeTodayTasks). A background sync that didn't touch the
        // floater list leaves the widget untouched while the app still holds the latest state.
        if let existing = loadSnapshot(), existing.hasSameContent(as: snapshot) {
            return
        }
        guard let data = try? JSONEncoder().encode(snapshot) else {
            return
        }

        WidgetSnapshotFileStore.write(data, to: snapshotFileName)

        #if canImport(WidgetKit)
        // See writeTodayTasks: a per-list widget can render either shape from either gallery
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
