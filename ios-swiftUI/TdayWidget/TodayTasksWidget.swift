#if canImport(WidgetKit) && canImport(SwiftUI)
import SwiftUI
import WidgetKit

private struct TodayTasksEntry: TimelineEntry {
    let date: Date
    let title: String
    let status: TodayTasksSnapshotStatus
    let taskCount: Int
    let tasks: [TodayTaskSnapshot]
}

private struct TodayTaskSnapshot: Codable, Identifiable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
}

private enum TodayTasksSnapshotStatus: String, Codable {
    case setup
    case empty
    case tasks
}

private struct TodayTasksSnapshot: Codable {
    let schemaVersion: Int
    let generatedAtEpochMs: Int64
    let title: String
    let status: TodayTasksSnapshotStatus
    let taskCount: Int
    let tasks: [TodayTaskSnapshot]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedTasks = try container.decodeIfPresent([TodayTaskSnapshot].self, forKey: .tasks) ?? []
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        generatedAtEpochMs = try container.decode(Int64.self, forKey: .generatedAtEpochMs)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Today's Tasks"
        status = (try? container.decodeIfPresent(TodayTasksSnapshotStatus.self, forKey: .status)) ?? (decodedTasks.isEmpty ? .empty : .tasks)
        taskCount = try container.decodeIfPresent(Int.self, forKey: .taskCount) ?? decodedTasks.count
        tasks = decodedTasks
    }
}

private struct TodayTasksProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayTasksEntry {
        .previewTasks
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayTasksEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayTasksEntry>) -> Void) {
        let entry = loadEntry()
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadEntry() -> TodayTasksEntry {
        guard let snapshot = Self.loadSnapshot() else {
            return TodayTasksEntry(
                date: Date(),
                title: "Today's Tasks",
                status: .setup,
                taskCount: 0,
                tasks: []
            )
        }

        return TodayTasksEntry(
            date: Date(timeIntervalSince1970: TimeInterval(snapshot.generatedAtEpochMs) / 1_000),
            title: snapshot.title,
            status: snapshot.status,
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

private extension TodayTasksEntry {
    static let previewTasks = TodayTasksEntry(
        date: Date(),
        title: "Today's Tasks",
        status: .tasks,
        taskCount: 10,
        tasks: [
            TodayTaskSnapshot(id: "preview-1", title: "Plan the morning", dueEpochMs: Date().timeIntervalEpochMs, priority: "medium"),
            TodayTaskSnapshot(id: "preview-2", title: "Review today", dueEpochMs: Date().addingTimeInterval(3_600).timeIntervalEpochMs, priority: "high"),
            TodayTaskSnapshot(id: "preview-3", title: "Send the quick update", dueEpochMs: Date().addingTimeInterval(7_200).timeIntervalEpochMs, priority: "low"),
            TodayTaskSnapshot(id: "preview-4", title: "Reset the evening list", dueEpochMs: Date().addingTimeInterval(10_800).timeIntervalEpochMs, priority: "medium"),
            TodayTaskSnapshot(id: "preview-5", title: "Call the contractor", dueEpochMs: Date().addingTimeInterval(12_600).timeIntervalEpochMs, priority: "high"),
            TodayTaskSnapshot(id: "preview-6", title: "Pick up groceries", dueEpochMs: Date().addingTimeInterval(14_400).timeIntervalEpochMs, priority: "medium"),
            TodayTaskSnapshot(id: "preview-7", title: "Prep tomorrow", dueEpochMs: Date().addingTimeInterval(16_200).timeIntervalEpochMs, priority: "low"),
            TodayTaskSnapshot(id: "preview-8", title: "Evening reset", dueEpochMs: Date().addingTimeInterval(18_000).timeIntervalEpochMs, priority: "medium"),
            TodayTaskSnapshot(id: "preview-9", title: "Queue notes", dueEpochMs: Date().addingTimeInterval(19_800).timeIntervalEpochMs, priority: "low"),
            TodayTaskSnapshot(id: "preview-10", title: "Close the loop", dueEpochMs: Date().addingTimeInterval(21_600).timeIntervalEpochMs, priority: "medium")
        ]
    )

    static let previewEmpty = TodayTasksEntry(
        date: Date(),
        title: "Today's Tasks",
        status: .empty,
        taskCount: 0,
        tasks: []
    )

    static let previewSetup = TodayTasksEntry(
        date: Date(),
        title: "Today's Tasks",
        status: .setup,
        taskCount: 0,
        tasks: []
    )
}

private struct TodayTasksWidgetView: View {
    let entry: TodayTasksEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            header

            switch entry.status {
            case .setup:
                message(title: "Open T'Day", subtitle: "Set up your workspace")
            case .empty:
                message(title: "No tasks due today", subtitle: "Add today")
            case .tasks:
                taskContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(URL(string: "tday://home"))
        .containerBackground(for: .widget) {
            widgetBackground
        }
    }

    private var spacing: CGFloat {
        switch family {
        case .systemSmall:
            return 7
        case .systemLarge:
            return 10
        default:
            return 9
        }
    }

    private var accentColor: Color {
        renderingMode == .fullColor ? .tdayTodayBlue : .primary
    }

    private var secondaryTextColor: Color {
        renderingMode == .fullColor ? .secondary : .primary.opacity(0.72)
    }

    private var widgetBackground: some View {
        (colorScheme == .dark ? Color.tdayDarkSurface : Color.tdayLightSurface)
            .overlay(alignment: .topLeading) {
                accentColor.opacity(renderingMode == .fullColor ? 0.10 : 0)
            }
    }

    private var header: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                if family != .systemSmall {
                    Text(entry.title)
                        .font(.system(.headline, design: .rounded, weight: .bold))
                        .lineLimit(1)
                }

                Text("\(entry.taskCount)")
                    .font(.system(family == .systemSmall ? .title : .subheadline, design: .rounded, weight: .heavy))
                    .foregroundStyle(accentColor)
                    .widgetAccentable()
            }

            Spacer(minLength: 4)

            Link(destination: URL(string: "tday://todos/create?target=today")!) {
                Text("+")
                    .font(.system(size: 24, weight: .heavy, design: .rounded))
                    .foregroundStyle(accentColor)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                    .widgetAccentable()
            }
            .buttonStyle(.plain)
        }
        .frame(minHeight: 44)
    }

    @ViewBuilder
    private var taskContent: some View {
        GeometryReader { proxy in
            taskListContent(availableHeight: proxy.size.height)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    @ViewBuilder
    private func taskListContent(availableHeight: CGFloat) -> some View {
        let rowCapacity = visibleRowCapacity(for: availableHeight)
        let hasOverflow = entry.tasks.count > rowCapacity
        let visibleCount = hasOverflow && rowCapacity > 1 ? rowCapacity - 1 : rowCapacity
        let overflowCount = max(0, entry.tasks.count - visibleCount)

        VStack(alignment: .leading, spacing: taskRowSpacing) {
            ForEach(entry.tasks.prefix(visibleCount)) { task in
                taskRow(task)
            }
            if hasOverflow && rowCapacity > 1 {
                overflowRow(count: overflowCount)
            }
        }
    }

    private func message(title: String, subtitle: String) -> some View {
        VStack(alignment: .center, spacing: family == .systemSmall ? 3 : 4) {
            Text(title)
                .font(.system(family == .systemLarge ? .headline : .subheadline, design: .rounded, weight: .bold))
                .lineLimit(2)
            if family != .systemSmall {
                Text(subtitle)
                    .font(.system(.caption, design: .rounded, weight: .bold))
                    .foregroundStyle(secondaryTextColor)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .multilineTextAlignment(.center)
    }

    private func taskRow(_ task: TodayTaskSnapshot) -> some View {
        HStack(spacing: 7) {
            priorityDot(task.priority, size: 7)
            Text(task.title)
                .font(.system(.caption, design: .rounded, weight: .bold))
                .lineLimit(1)
            Spacer(minLength: 4)
            Text(Self.dueTimeText(from: task.dueEpochMs))
                .font(.system(.caption2, design: .rounded, weight: .bold))
                .foregroundStyle(secondaryTextColor)
                .lineLimit(1)
        }
        .foregroundStyle(.primary)
        .frame(height: taskRowHeight)
    }

    private func overflowRow(count: Int) -> some View {
        Text("+\(count) more")
            .font(.system(.caption2, design: .rounded, weight: .heavy))
            .foregroundStyle(accentColor)
            .lineLimit(1)
            .widgetAccentable()
            .frame(height: taskRowHeight, alignment: .leading)
    }

    private func priorityDot(_ priority: String, size: CGFloat) -> some View {
        Circle()
            .fill(priorityColor(for: priority))
            .frame(width: size, height: size)
            .widgetAccentable()
    }

    private func priorityColor(for priority: String) -> Color {
        guard renderingMode == .fullColor else {
            return accentColor
        }

        switch priority.lowercased() {
        case "high":
            return .tdayPriorityHigh
        case "medium":
            return .tdayPriorityMedium
        default:
            return .secondary
        }
    }

    private static func dueTimeText(from epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1_000)
        return date.formatted(date: .omitted, time: .shortened)
    }

    private var taskRowHeight: CGFloat {
        switch family {
        case .systemSmall:
            return 24
        case .systemLarge:
            return 25
        default:
            return 22
        }
    }

    private var taskRowSpacing: CGFloat {
        switch family {
        case .systemSmall:
            return 4
        case .systemLarge:
            return 8
        default:
            return 6
        }
    }

    private func visibleRowCapacity(for height: CGFloat) -> Int {
        let rowStride = taskRowHeight + taskRowSpacing
        guard rowStride > 0 else {
            return 1
        }
        return max(1, Int((height + taskRowSpacing) / rowStride))
    }

}

@main
struct TodayTasksWidget: Widget {
    let kind = "TodayTasksWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayTasksProvider()) { entry in
            TodayTasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Today's Tasks")
        .description("Shows today's T'Day tasks at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .containerBackgroundRemovable(true)
    }
}

private extension Color {
    static let tdayTodayBlue = Color(red: 110.0 / 255.0, green: 168.0 / 255.0, blue: 225.0 / 255.0)
    static let tdayLightSurface = Color.white
    static let tdayDarkSurface = Color(red: 0.09, green: 0.10, blue: 0.13)
    static let tdayPriorityHigh = Color(red: 0.89, green: 0.49, blue: 0.49)
    static let tdayPriorityMedium = Color(red: 0.87, green: 0.70, blue: 0.49)
}

private extension Date {
    var timeIntervalEpochMs: Int64 {
        Int64(timeIntervalSince1970 * 1_000)
    }
}

#if DEBUG
#Preview("Small Tasks", as: .systemSmall) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewTasks
}

#Preview("Medium Tasks", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewTasks
}

#Preview("Large Tasks", as: .systemLarge) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewTasks
}

#Preview("Empty", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewEmpty
}

#Preview("Setup", as: .systemMedium) {
    TodayTasksWidget()
} timeline: {
    TodayTasksEntry.previewSetup
}
#endif
#endif
