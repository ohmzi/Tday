// TdayWidgetProvider.swift
// Tday — ios-swiftUI/TdayWidget/
//
// Improved TimelineProvider for the Today (scheduled-task) widget.
// Key improvements over a naive implementation:
//   • Reads task data from shared App Group UserDefaults (written by the main app)
//     so the extension never needs its own network call.
//   • Timeline generates 4 entries spaced 15 minutes apart.
//   • Policy is .atEnd so WidgetKit refreshes when all entries are consumed.
//   • After the last entry, WidgetKit calls getTimeline again at the natural
//     15-min boundary — the main app's reloadAllTimelines() fires sooner on changes.

import SwiftUI
import WidgetKit

// MARK: - Entry

struct TodayWidgetEntry: TimelineEntry {
    let date: Date
    let tasks: [WidgetTaskSnapshot]
    let lastUpdated: Date
    let isPlaceholder: Bool
}

// MARK: - Timeline Provider

struct TodayWidgetProvider: TimelineProvider {

    // ------------------------------------------------------------------
    // Placeholder — shown while WidgetKit is loading real data
    // ------------------------------------------------------------------
    func placeholder(in context: Context) -> TodayWidgetEntry {
        TodayWidgetEntry(
            date: .now,
            tasks: sampleTasks(),
            lastUpdated: .now,
            isPlaceholder: true
        )
    }

    // ------------------------------------------------------------------
    // Snapshot — shown in the widget gallery picker
    // ------------------------------------------------------------------
    func getSnapshot(in context: Context, completion: @escaping (TodayWidgetEntry) -> Void) {
        let entry = TodayWidgetEntry(
            date: .now,
            tasks: context.isPreview ? sampleTasks() : loadSharedTasks(),
            lastUpdated: loadLastUpdated(),
            isPlaceholder: false
        )
        completion(entry)
    }

    // ------------------------------------------------------------------
    // Timeline — the heart of the refresh strategy
    // ------------------------------------------------------------------
    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayWidgetEntry>) -> Void) {
        let tasks = loadSharedTasks()
        let lastUpdated = loadLastUpdated()
        var entries: [TodayWidgetEntry] = []

        // Build 4 entries spaced 15 minutes apart starting now.
        // WidgetKit will display one entry at a time, advancing on each date.
        // When the last entry's date passes, the .atEnd policy triggers a
        // fresh getTimeline call (roughly every hour in total).
        let now = Date.now
        for minuteOffset in stride(from: 0, through: 45, by: 15) {
            let entryDate = Calendar.current.date(
                byAdding: .minute,
                value: minuteOffset,
                to: now
            ) ?? now

            entries.append(TodayWidgetEntry(
                date: entryDate,
                tasks: tasks,
                lastUpdated: lastUpdated,
                isPlaceholder: false
            ))
        }

        // .atEnd: after the last entry WidgetKit will call getTimeline again.
        // The main app calls WidgetCenter.shared.reloadAllTimelines() on every
        // mutation so the widget typically updates immediately on task changes,
        // not just at the hour boundary.
        let timeline = Timeline(entries: entries, policy: .atEnd)
        completion(timeline)
    }

    // ------------------------------------------------------------------
    // MARK: Data reading from shared App Group
    // ------------------------------------------------------------------

    private func loadSharedTasks() -> [WidgetTaskSnapshot] {
        guard
            let defaults = UserDefaults(suiteName: kTdayAppGroupID),
            let data = defaults.data(forKey: SharedDefaultsKey.todayTasksJSON),
            let tasks = try? JSONDecoder().decode([WidgetTaskSnapshot].self, from: data)
        else { return [] }
        return tasks
    }

    private func loadLastUpdated() -> Date {
        guard let defaults = UserDefaults(suiteName: kTdayAppGroupID) else { return .distantPast }
        let ts = defaults.double(forKey: SharedDefaultsKey.lastUpdated)
        return ts > 0 ? Date(timeIntervalSince1970: ts) : .distantPast
    }

    private func sampleTasks() -> [WidgetTaskSnapshot] {
        [
            WidgetTaskSnapshot(id: "1", title: "Design review", priority: "HIGH",   isDone: false, dueTime: nil),
            WidgetTaskSnapshot(id: "2", title: "Write tests",   priority: "MEDIUM", isDone: false, dueTime: nil),
            WidgetTaskSnapshot(id: "3", title: "Deploy backend",priority: "LOW",    isDone: true,  dueTime: nil),
        ]
    }
}

// MARK: - Floater Widget Provider (mirrors TodayWidgetProvider)

struct FloaterWidgetEntry: TimelineEntry {
    let date: Date
    let tasks: [WidgetTaskSnapshot]
    let isPlaceholder: Bool
}

struct FloaterWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> FloaterWidgetEntry {
        FloaterWidgetEntry(date: .now, tasks: [], isPlaceholder: true)
    }

    func getSnapshot(in context: Context, completion: @escaping (FloaterWidgetEntry) -> Void) {
        completion(FloaterWidgetEntry(date: .now, tasks: loadFloaters(), isPlaceholder: false))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FloaterWidgetEntry>) -> Void) {
        let tasks = loadFloaters()
        var entries: [FloaterWidgetEntry] = []
        let now = Date.now
        for offset in stride(from: 0, through: 45, by: 15) {
            let d = Calendar.current.date(byAdding: .minute, value: offset, to: now) ?? now
            entries.append(FloaterWidgetEntry(date: d, tasks: tasks, isPlaceholder: false))
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    private func loadFloaters() -> [WidgetTaskSnapshot] {
        guard
            let defaults = UserDefaults(suiteName: kTdayAppGroupID),
            let data = defaults.data(forKey: SharedDefaultsKey.floaterTasksJSON),
            let tasks = try? JSONDecoder().decode([WidgetTaskSnapshot].self, from: data)
        else { return [] }
        return tasks
    }
}
