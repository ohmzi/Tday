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
                message(title: "No tasks due today", subtitle: "Add one for today")
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
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0))
                    .frame(height: 3)
            }
    }

    private var header: some View {
        HStack(spacing: 8) {
            if family == .systemSmall {
                smallCountLabel
            } else {
                Text(entry.title)
                    .font(.system(.headline, design: .rounded, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)

                Text(countText)
                    .font(.system(.caption, design: .rounded, weight: .heavy))
                    .foregroundStyle(accentColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .widgetAccentable()
            }

            Spacer(minLength: 4)

            Link(destination: URL(string: "tday://todos/create?target=today")!) {
                Text("+")
                    .font(.system(size: 24, weight: .heavy, design: .rounded))
                    .foregroundStyle(accentColor)
                    .frame(width: 44, height: 44)
                    .background(addButtonBackground)
                    .contentShape(Rectangle())
                    .widgetAccentable()
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add task for today")
        }
        .frame(minHeight: 44)
    }

    private var smallCountLabel: some View {
        VStack(alignment: .leading, spacing: -2) {
            Text("\(entry.taskCount)")
                .font(.system(.title, design: .rounded, weight: .heavy))
                .foregroundStyle(accentColor)
                .lineLimit(1)
                .widgetAccentable()
            Text("due")
                .font(.system(.caption2, design: .rounded, weight: .heavy))
                .foregroundStyle(secondaryTextColor)
                .lineLimit(1)
        }
        .accessibilityLabel(countText)
    }

    private var countText: String {
        "\(entry.taskCount) due"
    }

    private var addButtonBackground: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0.10))
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
        let totalCount = max(entry.taskCount, entry.tasks.count)
        let hasOverflow = totalCount > rowCapacity || entry.tasks.count < totalCount
        let visibleCapacity = hasOverflow && rowCapacity > 1 ? rowCapacity - 1 : rowCapacity
        let visibleCount = min(visibleCapacity, entry.tasks.count)
        let overflowCount = max(0, totalCount - visibleCount)

        VStack(alignment: .leading, spacing: taskRowSpacing) {
            ForEach(entry.tasks.prefix(visibleCount)) { task in
                taskRow(task)
            }
            if overflowCount > 0 && rowCapacity > 1 {
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
                .minimumScaleFactor(0.85)
            Spacer(minLength: 4)
            if family != .systemSmall {
                Text(Self.dueTimeText(from: task.dueEpochMs))
                    .font(.system(.caption2, design: .rounded, weight: .bold))
                    .foregroundStyle(secondaryTextColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
        }
        .foregroundStyle(.primary)
        .frame(height: taskRowHeight)
        .accessibilityLabel("\(task.title), due \(Self.dueTimeText(from: task.dueEpochMs))")
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
            return .tdayPriorityLow
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

private struct FloaterTasksEntry: TimelineEntry {
    let date: Date
    let title: String
    let status: FloaterTasksSnapshotStatus
    let taskCount: Int
    let tasks: [FloaterTaskSnapshot]
}

private struct FloaterTaskSnapshot: Codable, Identifiable {
    let id: String
    let title: String
    let priority: String
}

private enum FloaterTasksSnapshotStatus: String, Codable {
    case setup
    case empty
    case tasks
}

private struct FloaterTasksSnapshot: Codable {
    let schemaVersion: Int
    let generatedAtEpochMs: Int64
    let title: String
    let status: FloaterTasksSnapshotStatus
    let taskCount: Int
    let tasks: [FloaterTaskSnapshot]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedTasks = try container.decodeIfPresent([FloaterTaskSnapshot].self, forKey: .tasks) ?? []
        schemaVersion = try container.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? 1
        generatedAtEpochMs = try container.decode(Int64.self, forKey: .generatedAtEpochMs)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "Floater Tasks"
        status = (try? container.decodeIfPresent(FloaterTasksSnapshotStatus.self, forKey: .status)) ?? (decodedTasks.isEmpty ? .empty : .tasks)
        taskCount = try container.decodeIfPresent(Int.self, forKey: .taskCount) ?? decodedTasks.count
        tasks = decodedTasks
    }
}

private struct FloaterTasksProvider: TimelineProvider {
    func placeholder(in context: Context) -> FloaterTasksEntry {
        .previewTasks
    }

    func getSnapshot(in context: Context, completion: @escaping (FloaterTasksEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FloaterTasksEntry>) -> Void) {
        let entry = loadEntry()
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func loadEntry() -> FloaterTasksEntry {
        guard let snapshot = Self.loadSnapshot() else {
            return FloaterTasksEntry(
                date: Date(),
                title: "Floater Tasks",
                status: .setup,
                taskCount: 0,
                tasks: []
            )
        }

        return FloaterTasksEntry(
            date: Date(timeIntervalSince1970: TimeInterval(snapshot.generatedAtEpochMs) / 1_000),
            title: snapshot.title,
            status: snapshot.status,
            taskCount: snapshot.taskCount,
            tasks: snapshot.tasks
        )
    }

    private static func loadSnapshot() -> FloaterTasksSnapshot? {
        for store in defaultsStores() {
            guard let data = store.data(forKey: snapshotKey),
                  let snapshot = try? JSONDecoder().decode(FloaterTasksSnapshot.self, from: data) else {
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
    private static let snapshotKey = "tday.widget.floaterTasksSnapshot"
}

private extension FloaterTasksEntry {
    static let previewTasks = FloaterTasksEntry(
        date: Date(),
        title: "Floater Tasks",
        status: .tasks,
        taskCount: 10,
        tasks: [
            FloaterTaskSnapshot(id: "preview-1", title: "Draft the idea", priority: "high"),
            FloaterTaskSnapshot(id: "preview-2", title: "Queue reading", priority: "medium"),
            FloaterTaskSnapshot(id: "preview-3", title: "Try the new shortcut", priority: "low"),
            FloaterTaskSnapshot(id: "preview-4", title: "Collect shelf notes", priority: "medium"),
            FloaterTaskSnapshot(id: "preview-5", title: "Sketch someday flow", priority: "high"),
            FloaterTaskSnapshot(id: "preview-6", title: "Compare tools", priority: "medium"),
            FloaterTaskSnapshot(id: "preview-7", title: "Make the checklist", priority: "low"),
            FloaterTaskSnapshot(id: "preview-8", title: "Sort bookmarks", priority: "medium"),
            FloaterTaskSnapshot(id: "preview-9", title: "Ask about the vendor", priority: "low"),
            FloaterTaskSnapshot(id: "preview-10", title: "Polish notes", priority: "medium")
        ]
    )

    static let previewEmpty = FloaterTasksEntry(
        date: Date(),
        title: "Floater Tasks",
        status: .empty,
        taskCount: 0,
        tasks: []
    )

    static let previewSetup = FloaterTasksEntry(
        date: Date(),
        title: "Floater Tasks",
        status: .setup,
        taskCount: 0,
        tasks: []
    )
}

private struct FloaterTasksWidgetView: View {
    let entry: FloaterTasksEntry

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
                message(title: "No floater tasks", subtitle: "Add a floater")
            case .tasks:
                taskContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(URL(string: "tday://floater"))
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
        renderingMode == .fullColor ? .tdayFloaterGreen : .primary
    }

    private var secondaryTextColor: Color {
        renderingMode == .fullColor ? .secondary : .primary.opacity(0.72)
    }

    private var widgetBackground: some View {
        (colorScheme == .dark ? Color.tdayDarkSurface : Color.tdayLightSurface)
            .overlay(alignment: .top) {
                Rectangle()
                    .fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0))
                    .frame(height: 3)
            }
    }

    private var header: some View {
        HStack(spacing: 8) {
            if family == .systemSmall {
                smallCountLabel
            } else {
                Text(entry.title)
                    .font(.system(.headline, design: .rounded, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)

                Text(countText)
                    .font(.system(.caption, design: .rounded, weight: .heavy))
                    .foregroundStyle(accentColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .widgetAccentable()
            }

            Spacer(minLength: 4)

            Link(destination: URL(string: "tday://todos/create?target=floater")!) {
                Text("+")
                    .font(.system(size: 24, weight: .heavy, design: .rounded))
                    .foregroundStyle(accentColor)
                    .frame(width: 44, height: 44)
                    .background(addButtonBackground)
                    .contentShape(Rectangle())
                    .widgetAccentable()
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add floater task")
        }
        .frame(minHeight: 44)
    }

    private var smallCountLabel: some View {
        VStack(alignment: .leading, spacing: -2) {
            Text("\(entry.taskCount)")
                .font(.system(.title, design: .rounded, weight: .heavy))
                .foregroundStyle(accentColor)
                .lineLimit(1)
                .widgetAccentable()
            Text("open")
                .font(.system(.caption2, design: .rounded, weight: .heavy))
                .foregroundStyle(secondaryTextColor)
                .lineLimit(1)
        }
        .accessibilityLabel(countText)
    }

    private var countText: String {
        "\(entry.taskCount) open"
    }

    private var addButtonBackground: some View {
        RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0.10))
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
        let totalCount = max(entry.taskCount, entry.tasks.count)
        let hasOverflow = totalCount > rowCapacity || entry.tasks.count < totalCount
        let visibleCapacity = hasOverflow && rowCapacity > 1 ? rowCapacity - 1 : rowCapacity
        let visibleCount = min(visibleCapacity, entry.tasks.count)
        let overflowCount = max(0, totalCount - visibleCount)

        VStack(alignment: .leading, spacing: taskRowSpacing) {
            ForEach(entry.tasks.prefix(visibleCount)) { task in
                taskRow(task)
            }
            if overflowCount > 0 && rowCapacity > 1 {
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

    private func taskRow(_ task: FloaterTaskSnapshot) -> some View {
        HStack(spacing: 7) {
            priorityDot(task.priority, size: 7)
            Text(task.title)
                .font(.system(.caption, design: .rounded, weight: .bold))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            Spacer(minLength: 4)
        }
        .foregroundStyle(.primary)
        .frame(height: taskRowHeight)
        .accessibilityLabel(task.title)
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
            return .tdayPriorityLow
        }
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

struct FloaterTasksWidget: Widget {
    let kind = "FloaterTasksWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: FloaterTasksProvider()) { entry in
            FloaterTasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Floater Tasks")
        .description("Shows floater T'Day tasks at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .containerBackgroundRemovable(true)
    }
}

@main
struct TdayWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayTasksWidget()
        FloaterTasksWidget()
    }
}

private extension Color {
    static let tdayTodayBlue = Color(red: 110.0 / 255.0, green: 168.0 / 255.0, blue: 225.0 / 255.0)
    static let tdayFloaterGreen = Color(red: 77.0 / 255.0, green: 143.0 / 255.0, blue: 131.0 / 255.0)
    static let tdayLightSurface = Color.white
    static let tdayDarkSurface = Color(red: 0.09, green: 0.10, blue: 0.13)
    static let tdayPriorityHigh = Color(red: 0.89, green: 0.49, blue: 0.49)
    static let tdayPriorityMedium = Color(red: 0.87, green: 0.70, blue: 0.49)
    static let tdayPriorityLow = Color(red: 0.00, green: 0.48, blue: 1.00)
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

#Preview("Floater Small Tasks", as: .systemSmall) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewTasks
}

#Preview("Floater Medium Tasks", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewTasks
}

#Preview("Floater Large Tasks", as: .systemLarge) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewTasks
}

#Preview("Floater Empty", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewEmpty
}

#Preview("Floater Setup", as: .systemMedium) {
    FloaterTasksWidget()
} timeline: {
    FloaterTasksEntry.previewSetup
}
#endif
#endif
