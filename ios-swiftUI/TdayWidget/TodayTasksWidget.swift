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

    var body: some View {
        TdayTasksWidgetContent(
            title: entry.title,
            status: TaskWidgetStatus(entry.status),
            taskCount: entry.taskCount,
            rows: entry.tasks.map {
                WidgetTaskRowModel(
                    id: $0.id,
                    title: $0.title,
                    priority: $0.priority,
                    dueEpochMs: $0.dueEpochMs
                )
            },
            mode: .today
        )
    }
}

private struct WidgetTaskRowModel: Identifiable {
    let id: String
    let title: String
    let priority: String
    let dueEpochMs: Int64?
}

private enum TaskWidgetStatus {
    case setup
    case empty
    case tasks

    init(_ status: TodayTasksSnapshotStatus) {
        switch status {
        case .setup:
            self = .setup
        case .empty:
            self = .empty
        case .tasks:
            self = .tasks
        }
    }

    init(_ status: FloaterTasksSnapshotStatus) {
        switch status {
        case .setup:
            self = .setup
        case .empty:
            self = .empty
        case .tasks:
            self = .tasks
        }
    }
}

private enum TaskWidgetMode {
    case today
    case floater

    var openURL: URL {
        switch self {
        case .today:
            return URL(string: "tday://home")!
        case .floater:
            return URL(string: "tday://floater")!
        }
    }

    var createURL: URL {
        switch self {
        case .today:
            return URL(string: "tday://todos/create?target=today")!
        case .floater:
            return URL(string: "tday://todos/create?target=floater")!
        }
    }

    var countUnit: String {
        switch self {
        case .today:
            return "due"
        case .floater:
            return "open"
        }
    }

    var emptyTitle: String {
        switch self {
        case .today:
            return "No tasks due today"
        case .floater:
            return "No floater tasks"
        }
    }

    var emptySubtitle: String {
        switch self {
        case .today:
            return "Add one for today"
        case .floater:
            return "Add a floater"
        }
    }

    var addAccessibilityLabel: String {
        switch self {
        case .today:
            return "Add task for today"
        case .floater:
            return "Add floater task"
        }
    }

    var showsDueTime: Bool {
        self == .today
    }

    func accentColor(renderingMode: WidgetRenderingMode) -> Color {
        guard renderingMode == .fullColor else {
            return .primary
        }
        switch self {
        case .today:
            return .tdayTodayBlue
        case .floater:
            return .tdayFloaterGreen
        }
    }

    func featuredRowColor(renderingMode: WidgetRenderingMode, colorScheme: ColorScheme) -> Color {
        guard renderingMode == .fullColor else {
            return .primary.opacity(0.08)
        }
        switch self {
        case .today:
            return colorScheme == .dark ? .tdayTodayFeatureWashDark : .tdayTodayFeatureWash
        case .floater:
            return colorScheme == .dark ? .tdayFloaterFeatureWashDark : .tdayFloaterFeatureWash
        }
    }
}

private struct TdayTasksWidgetContent: View {
    let title: String
    let status: TaskWidgetStatus
    let taskCount: Int
    let rows: [WidgetTaskRowModel]
    let mode: TaskWidgetMode

    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: metrics.contentSpacing) {
            header

            switch status {
            case .setup:
                message(title: "Open T'Day", subtitle: "Set up your workspace")
            case .empty:
                message(title: mode.emptyTitle, subtitle: mode.emptySubtitle)
            case .tasks:
                taskList
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(mode.openURL)
        .containerBackground(for: .widget) {
            widgetBackground
        }
    }

    private var metrics: WidgetLayoutMetrics {
        WidgetLayoutMetrics(family: family)
    }

    private var accentColor: Color {
        mode.accentColor(renderingMode: renderingMode)
    }

    private var secondaryTextColor: Color {
        renderingMode == .fullColor ? .secondary : .primary.opacity(0.72)
    }

    private var countText: String {
        "\(taskCount) \(mode.countUnit)"
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
                Text(title)
                    .font(.system(size: family == .systemLarge ? 17 : 16, weight: .bold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)

                countPill
            }

            Spacer(minLength: 4)
            addButton
        }
        .frame(minHeight: metrics.headerHeight)
    }

    private var smallCountLabel: some View {
        VStack(alignment: .leading, spacing: -1) {
            Text("\(taskCount)")
                .font(.system(size: 28, weight: .heavy, design: .rounded))
                .foregroundStyle(accentColor)
                .lineLimit(1)
                .widgetAccentable()
            Text(mode.countUnit)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(secondaryTextColor)
                .lineLimit(1)
        }
        .accessibilityLabel(countText)
    }

    private var countPill: some View {
        Text(countText)
            .font(.system(size: 12, weight: .heavy, design: .rounded))
            .foregroundStyle(accentColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, 10)
            .frame(height: 26)
            .background(Capsule().fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0.10)))
            .widgetAccentable()
    }

    private var addButton: some View {
        Link(destination: mode.createURL) {
            Image(systemName: "plus")
                .font(.system(size: family == .systemSmall ? 16 : 17, weight: .heavy, design: .rounded))
                .foregroundStyle(accentColor)
                .frame(width: metrics.addButtonSize, height: metrics.addButtonSize)
                .background(
                    RoundedRectangle(cornerRadius: metrics.addButtonCornerRadius, style: .continuous)
                        .fill(accentColor.opacity(renderingMode == .fullColor ? 0.14 : 0.10))
                )
                .contentShape(Rectangle())
                .widgetAccentable()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(mode.addAccessibilityLabel)
    }

    private var taskList: some View {
        let rowCapacity = metrics.visibleRowCapacity
        let totalCount = max(taskCount, rows.count)
        let hasOverflow = totalCount > rowCapacity || rows.count < totalCount
        let visibleCapacity = hasOverflow && rowCapacity > 1 ? rowCapacity - 1 : rowCapacity
        let visibleRows = Array(rows.prefix(visibleCapacity))
        let overflowCount = max(0, totalCount - visibleRows.count)

        return VStack(alignment: .leading, spacing: metrics.rowSpacing) {
            ForEach(Array(visibleRows.enumerated()), id: \.element.id) { index, row in
                taskRow(row, featured: family == .systemLarge && index == 0)
            }
            if overflowCount > 0 && rowCapacity > 1 {
                overflowRow(count: overflowCount)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func message(title: String, subtitle: String) -> some View {
        VStack(alignment: .center, spacing: family == .systemSmall ? 6 : 8) {
            Capsule()
                .fill(accentColor.opacity(renderingMode == .fullColor ? 0.18 : 0.10))
                .frame(width: family == .systemSmall ? 28 : 34, height: 3)
                .widgetAccentable()

            Text(title)
                .font(.system(size: family == .systemLarge ? 17 : 15, weight: .bold, design: .rounded))
                .lineLimit(family == .systemSmall ? 1 : 2)
                .minimumScaleFactor(0.85)

            if family != .systemSmall {
                Text(subtitle)
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundStyle(secondaryTextColor)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .multilineTextAlignment(.center)
    }

    private func taskRow(_ row: WidgetTaskRowModel, featured: Bool) -> some View {
        HStack(spacing: featured ? 8 : 7) {
            priorityDot(row.priority, size: featured ? 8 : 7)
            Text(row.title)
                .font(.system(size: featured ? 13 : metrics.rowFontSize, weight: .bold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            Spacer(minLength: 4)
            if mode.showsDueTime, family != .systemSmall, let dueEpochMs = row.dueEpochMs {
                dueTimeChip(Self.dueTimeText(from: dueEpochMs))
            }
        }
        .foregroundStyle(.primary)
        .padding(.horizontal, featured ? 9 : 2)
        .frame(height: featured ? metrics.featuredRowHeight : metrics.rowHeight)
        .background {
            if featured {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(mode.featuredRowColor(renderingMode: renderingMode, colorScheme: colorScheme))
            }
        }
        .accessibilityLabel(accessibilityLabel(for: row))
    }

    private func dueTimeChip(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, 7)
            .frame(height: 22)
            .background(
                Capsule().fill(
                    (colorScheme == .dark ? Color.tdayDueChipBackgroundDark : Color.tdayDueChipBackground)
                        .opacity(renderingMode == .fullColor ? 1 : 0.12)
                )
            )
    }

    private func overflowRow(count: Int) -> some View {
        Text("+\(count) more")
            .font(.system(size: 11, weight: .heavy, design: .rounded))
            .foregroundStyle(accentColor)
            .lineLimit(1)
            .widgetAccentable()
            .padding(.leading, 16)
            .frame(height: metrics.rowHeight, alignment: .leading)
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
        case "high", "urgent", "important":
            return .tdayPriorityHigh
        case "medium":
            return .tdayPriorityMedium
        default:
            return .tdayPriorityLow
        }
    }

    private func accessibilityLabel(for row: WidgetTaskRowModel) -> String {
        guard mode.showsDueTime, let dueEpochMs = row.dueEpochMs else {
            return row.title
        }
        return "\(row.title), due \(Self.dueTimeText(from: dueEpochMs))"
    }

    private static func dueTimeText(from epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1_000)
        return date.formatted(date: .omitted, time: .shortened)
    }
}

private struct WidgetLayoutMetrics {
    let contentSpacing: CGFloat
    let headerHeight: CGFloat
    let addButtonSize: CGFloat
    let addButtonCornerRadius: CGFloat
    let rowHeight: CGFloat
    let featuredRowHeight: CGFloat
    let rowSpacing: CGFloat
    let rowFontSize: CGFloat
    let visibleRowCapacity: Int

    init(family: WidgetFamily) {
        switch family {
        case .systemSmall:
            contentSpacing = 6
            headerHeight = 40
            addButtonSize = 40
            addButtonCornerRadius = 12
            rowHeight = 22
            featuredRowHeight = 22
            rowSpacing = 3
            rowFontSize = 12
            visibleRowCapacity = 2
        case .systemLarge:
            contentSpacing = 10
            headerHeight = 46
            addButtonSize = 46
            addButtonCornerRadius = 14
            rowHeight = 25
            featuredRowHeight = 34
            rowSpacing = 5
            rowFontSize = 13
            visibleRowCapacity = 5
        default:
            contentSpacing = 8
            headerHeight = 44
            addButtonSize = 44
            addButtonCornerRadius = 13
            rowHeight = 24
            featuredRowHeight = 24
            rowSpacing = 4
            rowFontSize = 12
            visibleRowCapacity = 3
        }
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

    var body: some View {
        TdayTasksWidgetContent(
            title: entry.title,
            status: TaskWidgetStatus(entry.status),
            taskCount: entry.taskCount,
            rows: entry.tasks.map {
                WidgetTaskRowModel(
                    id: $0.id,
                    title: $0.title,
                    priority: $0.priority,
                    dueEpochMs: nil
                )
            },
            mode: .floater
        )
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
    static let tdayTodayFeatureWash = Color(red: 243.0 / 255.0, green: 249.0 / 255.0, blue: 1.0)
    static let tdayFloaterFeatureWash = Color(red: 240.0 / 255.0, green: 248.0 / 255.0, blue: 245.0 / 255.0)
    static let tdayDueChipBackground = Color(red: 241.0 / 255.0, green: 244.0 / 255.0, blue: 249.0 / 255.0)
    static let tdayTodayFeatureWashDark = Color(red: 0.09, green: 0.14, blue: 0.20)
    static let tdayFloaterFeatureWashDark = Color(red: 0.08, green: 0.17, blue: 0.15)
    static let tdayDueChipBackgroundDark = Color(red: 0.13, green: 0.15, blue: 0.21)
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
