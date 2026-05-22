#if canImport(WidgetKit) && canImport(SwiftUI)
import SwiftUI
import WidgetKit

private struct TodayTasksEntry: TimelineEntry {
    let date: Date
    let title: String
    let taskCount: Int
    let tasks: [TodayTaskSnapshot]
}

private struct TodayTaskSnapshot: Codable, Identifiable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
}

private struct TodayTasksSnapshot: Codable {
    let generatedAtEpochMs: Int64
    let title: String
    let taskCount: Int
    let tasks: [TodayTaskSnapshot]
}

private struct TodayTasksProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayTasksEntry {
        TodayTasksEntry(
            date: Date(),
            title: "Today's Tasks",
            taskCount: 2,
            tasks: [
                TodayTaskSnapshot(id: "placeholder-1", title: "Plan the morning", dueEpochMs: Date().timeIntervalEpochMs, priority: "medium"),
                TodayTaskSnapshot(id: "placeholder-2", title: "Review today", dueEpochMs: Date().addingTimeInterval(3_600).timeIntervalEpochMs, priority: "high")
            ]
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayTasksEntry) -> Void) {
        completion(loadEntry() ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayTasksEntry>) -> Void) {
        let entry = loadEntry() ?? placeholder(in: context)
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadEntry() -> TodayTasksEntry? {
        guard let snapshot = Self.loadSnapshot() else {
            return nil
        }

        return TodayTasksEntry(
            date: Date(timeIntervalSince1970: TimeInterval(snapshot.generatedAtEpochMs) / 1_000),
            title: snapshot.title,
            taskCount: snapshot.taskCount,
            tasks: snapshot.tasks
        )
    }

    private static func loadSnapshot() -> TodayTasksSnapshot? {
        for store in defaultsStores() {
            guard let data = store.data(forKey: snapshotKey),
                  let snapshot = try? JSONDecoder().decode(TodayTasksSnapshot.self, from: data) else {
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

    private static let appGroupSuiteName = "group.com.ohmz.tday"
    private static let snapshotKey = "tday.widget.todayTasksSnapshot"
}

private struct TodayTasksWidgetView: View {
    let entry: TodayTasksEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(entry.title)
                    .font(.headline)
                Spacer()
                Text("\(entry.taskCount)")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
            }

            if entry.tasks.isEmpty {
                Text("No tasks due today")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            } else {
                ForEach(entry.tasks.prefix(4)) { task in
                    if let url = Self.taskDeepLinkURL(for: task.id) {
                        Link(destination: url) {
                            taskRow(task)
                        }
                        .foregroundStyle(.primary)
                    } else {
                        taskRow(task)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .widgetURL(URL(string: "tday://todos/today"))
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private func priorityColor(for priority: String) -> Color {
        switch priority.lowercased() {
        case "high":
            return .red
        case "medium":
            return .orange
        default:
            return .secondary
        }
    }

    private static func dueTimeText(from epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1_000)
        return date.formatted(date: .omitted, time: .shortened)
    }

    private static func taskDeepLinkURL(for taskID: String) -> URL? {
        var components = URLComponents()
        components.scheme = "tday"
        components.host = "todos"
        components.path = "/all"
        components.queryItems = [URLQueryItem(name: "highlightTodoId", value: taskID)]
        return components.url
    }

    private func taskRow(_ task: TodayTaskSnapshot) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(priorityColor(for: task.priority))
                .frame(width: 7, height: 7)
            Text(task.title)
                .lineLimit(1)
            Spacer(minLength: 4)
            Text(Self.dueTimeText(from: task.dueEpochMs))
                .foregroundStyle(.secondary)
        }
        .font(.caption)
    }
}

struct TodayTasksWidget: Widget {
    let kind = "TodayTasksWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayTasksProvider()) { entry in
            TodayTasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Today's Tasks")
        .description("Shows the current Tday tasks for today.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

private extension Date {
    var timeIntervalEpochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}
#endif
