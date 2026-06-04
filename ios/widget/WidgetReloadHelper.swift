// WidgetReloadHelper.swift
// Tday — ios-swiftUI/Tday/Widget/
//
// Drop this file into your main app target (NOT the TdayWidget extension target).
// The extension reads shared data via App Group; this file writes it and
// signals WidgetKit to reload.

import Foundation
import WidgetKit

/// Shared App Group identifier — must match exactly in:
///   • Main app target → Signing & Capabilities → App Groups
///   • TdayWidget extension → Signing & Capabilities → App Groups
///   • TdayWidget/TdayWidgetProvider.swift (reading side)
let kTdayAppGroupID = "group.com.ohmz.tday"   // ← replace with your real App Group ID

// MARK: - WidgetReloadHelper

/// Central helper. Call from ViewModels / use-cases after any task mutation.
/// All methods are safe to call from any thread/actor.
@MainActor
final class WidgetReloadHelper {

    static let shared = WidgetReloadHelper()
    private init() {}

    // -----------------------------------------------------------------------
    // MARK: Public API
    // -----------------------------------------------------------------------

    /// Write updated task snapshot to shared storage, then ask WidgetKit to
    /// rebuild all timelines. Call after every add / edit / complete / delete.
    func reloadAfterTaskChange() {
        persistTaskSnapshot()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Same as reloadAfterTaskChange but only reloads the Today widget timeline.
    func reloadTodayWidget() {
        persistTaskSnapshot()
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetKind.today)
    }

    /// Same as reloadAfterTaskChange but only reloads the Floater widget timeline.
    func reloadFloaterWidget() {
        persistFloaterSnapshot()
        WidgetCenter.shared.reloadTimelines(ofKind: WidgetKind.floater)
    }

    // -----------------------------------------------------------------------
    // MARK: Snapshot persistence (App Group UserDefaults)
    // -----------------------------------------------------------------------

    /// Write a compact JSON snapshot of today's tasks into the shared App Group
    /// so the widget extension can read it without hitting the network or SwiftData.
    private func persistTaskSnapshot() {
        Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            await self._persistTaskSnapshot()
        }
    }

    private func persistFloaterSnapshot() {
        Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            await self._persistFloaterSnapshot()
        }
    }

    // These are nonisolated so they run off the main actor
    private nonisolated func _persistTaskSnapshot() async {
        guard let defaults = UserDefaults(suiteName: kTdayAppGroupID) else { return }
        // Fetch today's tasks from SwiftData on a background context.
        // Replace TaskSnapshotLoader with your actual data access layer.
        let items = await TaskSnapshotLoader.shared.loadTodayTasks()
        if let data = try? JSONEncoder().encode(items) {
            defaults.set(data, forKey: SharedDefaultsKey.todayTasksJSON)
            defaults.set(Date().timeIntervalSince1970, forKey: SharedDefaultsKey.lastUpdated)
        }
    }

    private nonisolated func _persistFloaterSnapshot() async {
        guard let defaults = UserDefaults(suiteName: kTdayAppGroupID) else { return }
        let items = await TaskSnapshotLoader.shared.loadFloaterTasks()
        if let data = try? JSONEncoder().encode(items) {
            defaults.set(data, forKey: SharedDefaultsKey.floaterTasksJSON)
        }
    }
}

// MARK: - Shared key names (identical in widget extension)

enum SharedDefaultsKey {
    static let todayTasksJSON   = "tday_widget_today_tasks"
    static let floaterTasksJSON = "tday_widget_floater_tasks"
    static let lastUpdated      = "tday_widget_last_updated"
}

// MARK: - Widget kind identifiers

enum WidgetKind {
    static let today   = "TdayTodayWidget"    // must match `kind:` in TodayWidget.swift
    static let floater = "TdayFloaterWidget"  // must match `kind:` in FloaterWidget.swift
}

// MARK: - Lightweight DTO (shared between app and widget extension via a framework
//          or by duplicating this struct in the extension target)

struct WidgetTaskSnapshot: Codable, Identifiable {
    let id: String
    let title: String
    let priority: String  // "HIGH" | "MEDIUM" | "LOW"
    let isDone: Bool
    let dueTime: TimeInterval?  // nil → no specific time
}

// MARK: - TaskSnapshotLoader
// Replace the bodies with calls into your real SwiftData / repository layer.

actor TaskSnapshotLoader {
    static let shared = TaskSnapshotLoader()
    private init() {}

    func loadTodayTasks() async -> [WidgetTaskSnapshot] {
        // TODO: fetch from your SwiftData ModelContainer for today's todos.
        // Example:
        //   let ctx = ModelContext(sharedModelContainer)
        //   let todos = try? ctx.fetch(FetchDescriptor<Todo>(
        //       predicate: #Predicate { Calendar.current.isDateInToday($0.due) },
        //       sortBy: [SortDescriptor(\.priority, order: .reverse)]
        //   ))
        //   return todos?.map { WidgetTaskSnapshot(id: $0.id, title: $0.title, ...) } ?? []
        return []
    }

    func loadFloaterTasks() async -> [WidgetTaskSnapshot] {
        // TODO: fetch from your FloaterTask SwiftData store.
        return []
    }
}
