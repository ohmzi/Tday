import AppIntents
import Foundation
import WidgetKit

enum CarTaskIntentTarget: String, AppEnum {
    case today
    case floater

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "T'Day Destination")
    static var caseDisplayRepresentations: [CarTaskIntentTarget: DisplayRepresentation] = [
        .today: "T'Day",
        .floater: "Floater"
    ]

    var mode: CarTaskMode {
        switch self {
        case .today:
            return .today
        case .floater:
            return .floater
        }
    }
}

struct CreateCarTaskIntent: AppIntent {
    static var title: LocalizedStringResource = "Add T'Day Task"
    static var description = IntentDescription("Adds a Today task or floater to T'Day by voice.")
    static var openAppWhenRun = false

    @Parameter(title: "Task")
    var taskTitle: String

    @Parameter(title: "Destination", default: .today)
    var destination: CarTaskIntentTarget

    init() {}

    init(taskTitle: String, destination: CarTaskIntentTarget) {
        self.taskTitle = taskTitle
        self.destination = destination
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let trimmedTitle = taskTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedTitle.isEmpty else {
            TdayTelemetry.addBreadcrumb(
                "car_task.voice_create",
                data: ["platform": "ios", "mode": destination.mode.telemetryName, "result": "blank"]
            )
            return .result(dialog: "I did not hear a task title.")
        }

        let mode = destination.mode
        let payload = mode.createPayload(title: trimmedTitle)
        do {
            if mode == .floater {
                try await AppContainer.shared.todoRepository.createFloater(payload: payload)
            } else {
                try await AppContainer.shared.todoRepository.createTodo(payload: payload)
            }
            TdayTelemetry.addBreadcrumb(
                "car_task.voice_create",
                data: ["platform": "ios", "mode": mode.telemetryName, "result": "success"]
            )
            return .result(dialog: mode == .floater ? "Added to Floater." : "Added to T'Day.")
        } catch {
            TdayTelemetry.capture(
                error,
                operation: "car_task.voice_create",
                data: ["platform": "ios", "mode": mode.telemetryName]
            )
            throw error
        }
    }
}

// MARK: - Focus filters (R6-3, iOS-only)

/// Shared store for the list-ID set an active iOS Focus limits Today to. Backed
/// by the App Group so the widget snapshot builder sees the same value. `nil`
/// (or an empty set) means "no Focus filter active" → show every list.
enum TdayFocusFilterStore {
    private static let suiteName = "group.com.ohmz.tday"
    private static let activeListIDsKey = "tday.focus.activeListIDs"

    private static var defaults: UserDefaults {
        UserDefaults(suiteName: suiteName) ?? .standard
    }

    static func activeListIDs() -> Set<String>? {
        guard let ids = defaults.array(forKey: activeListIDsKey) as? [String], !ids.isEmpty else {
            return nil
        }
        return Set(ids)
    }

    static func setActiveListIDs(_ ids: [String]) {
        if ids.isEmpty {
            defaults.removeObject(forKey: activeListIDsKey)
        } else {
            defaults.set(ids, forKey: activeListIDsKey)
        }
    }

    /// Whether a todo in `listId` is visible under the current Focus. A todo with
    /// no list is hidden while a filter is active — the user picked explicit lists.
    static func allows(listId: String?) -> Bool {
        guard let active = activeListIDs() else { return true }
        guard let listId else { return false }
        return active.contains(listId)
    }
}

/// A user list, exposed to the Focus Filter picker in Settings ▸ Focus.
struct TdayListAppEntity: AppEntity, Identifiable {
    let id: String
    let name: String

    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "List")
    static var defaultQuery = TdayListEntityQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)")
    }
}

struct TdayListEntityQuery: EntityQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [TdayListAppEntity] {
        let wanted = Set(identifiers)
        return allLists().filter { wanted.contains($0.id) }
    }

    @MainActor
    func suggestedEntities() async throws -> [TdayListAppEntity] {
        allLists()
    }

    @MainActor
    private func allLists() -> [TdayListAppEntity] {
        AppContainer.shared.cacheManager.loadOfflineState().lists
            .map { TdayListAppEntity(id: $0.id, name: $0.name) }
    }
}

/// Lets an iOS Focus narrow the Today feed (and home-screen widget) to a chosen
/// set of lists. The system runs `perform()` when the Focus turns on/off, so the
/// stored set always reflects the active Focus; clearing it restores every list.
struct TdayFocusFilterIntent: SetFocusFilterIntent {
    static var title: LocalizedStringResource = "Filter T'Day lists"
    static var description: IntentDescription? =
        IntentDescription("Choose which lists appear in Today and the widget while a Focus is on.")

    @Parameter(title: "Lists")
    var lists: [TdayListAppEntity]?

    var displayRepresentation: DisplayRepresentation {
        let names = (lists ?? []).map(\.name)
        let subtitle: String
        switch names.count {
        case 0: subtitle = "All lists"
        case 1: subtitle = names[0]
        default: subtitle = "\(names.count) lists"
        }
        return DisplayRepresentation(title: "T'Day", subtitle: "\(subtitle)")
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        TdayFocusFilterStore.setActiveListIDs((lists ?? []).map(\.id))
        // Rebuild the today snapshot so the widget reflects the new filter now,
        // not on the next cache change. saveTodayTasks also reloads the timeline.
        let state = try await AppContainer.shared.cacheManager.loadOfflineState()
        TodayTasksWidgetSnapshotStore.saveTodayTasks(from: state)
        return .result()
    }
}

struct TdayCarAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CreateCarTaskIntent(),
            phrases: [
                "Add task in \(.applicationName)",
                "Add T'Day task in \(.applicationName)"
            ],
            shortTitle: "Add Task",
            systemImageName: "plus"
        )
        AppShortcut(
            intent: CreateCarTaskIntent(taskTitle: "", destination: .floater),
            phrases: [
                "Add floater in \(.applicationName)",
                "Add anytime task in \(.applicationName)"
            ],
            shortTitle: "Add Floater",
            systemImageName: "leaf"
        )
    }
}
