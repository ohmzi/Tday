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
        TodayTasksEntry(
            date: Date(),
            title: "Today's Tasks",
            status: .tasks,
            taskCount: 2,
            tasks: [
                TodayTaskSnapshot(id: "placeholder-1", title: "Plan the morning", dueEpochMs: Date().timeIntervalEpochMs, priority: "medium"),
                TodayTaskSnapshot(id: "placeholder-2", title: "Review today", dueEpochMs: Date().addingTimeInterval(3_600).timeIntervalEpochMs, priority: "high")
            ]
        )
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
        .widgetURL(URL(string: "tday://todos/today"))
        .containerBackground(for: .widget) {
            widgetBackground
        }
    }

    private var spacing: CGFloat {
        family == .systemSmall ? 8 : 10
    }

    private var visibleTaskLimit: Int {
        switch family {
        case .systemSmall:
            return 1
        case .systemLarge:
            return 8
        default:
            return 4
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
            Link(destination: URL(string: "tday://todos/today")!) {
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
            }
            .buttonStyle(.plain)

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
        if family == .systemSmall, let firstTask = entry.tasks.first {
            Link(destination: Self.taskDeepLinkURL(for: firstTask.id)) {
                VStack(alignment: .leading, spacing: 6) {
                    priorityDot(firstTask.priority, size: 10)
                    Text(firstTask.title)
                        .font(.system(.subheadline, design: .rounded, weight: .bold))
                        .lineLimit(2)
                    Text(Self.dueTimeText(from: firstTask.dueEpochMs))
                        .font(.system(.caption, design: .rounded, weight: .bold))
                        .foregroundStyle(secondaryTextColor)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
        } else {
            VStack(alignment: .leading, spacing: family == .systemLarge ? 8 : 6) {
                ForEach(entry.tasks.prefix(visibleTaskLimit)) { task in
                    Link(destination: Self.taskDeepLinkURL(for: task.id)) {
                        taskRow(task)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func message(title: String, subtitle: String) -> some View {
        Link(destination: URL(string: "tday://todos/create?target=today")!) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(.subheadline, design: .rounded, weight: .bold))
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
        .buttonStyle(.plain)
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
        .frame(minHeight: family == .systemLarge ? 25 : 22)
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

    private static func taskDeepLinkURL(for taskID: String) -> URL {
        var components = URLComponents()
        components.scheme = "tday"
        components.host = "todos"
        components.path = "/all"
        components.queryItems = [URLQueryItem(name: "highlightTodoId", value: taskID)]
        return components.url ?? URL(string: "tday://todos/all")!
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
#endif
