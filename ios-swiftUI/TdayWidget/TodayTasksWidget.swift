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
    // Optional so snapshots persisted before this field existed still decode (as nil).
    let description: String?

    init(
        id: String,
        title: String,
        dueEpochMs: Int64,
        priority: String,
        description: String? = nil
    ) {
        self.id = id
        self.title = title
        self.dueEpochMs = dueEpochMs
        self.priority = priority
        self.description = description
    }
}

private enum TodayTasksSnapshotStatus: String, Codable {
    case setup
    case empty
    case tasks
}

private func isTaskWidgetDaytime(_ date: Date) -> Bool {
    let hour = Calendar.current.component(.hour, from: date)
    return (6..<18).contains(hour)
}

private func nextTaskWidgetDayNightRefresh(after date: Date, calendar: Calendar = .current) -> Date {
    let hour = calendar.component(.hour, from: date)
    let targetHour = hour < 6 ? 6 : (hour < 18 ? 18 : 6)
    let targetDate: Date
    if hour >= 18 {
        targetDate = calendar.date(byAdding: .day, value: 1, to: date) ?? date.addingTimeInterval(86_400)
    } else {
        targetDate = date
    }

    return calendar.date(bySettingHour: targetHour, minute: 0, second: 0, of: targetDate)
        ?? date.addingTimeInterval(1_800)
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
        let now = Date()
        let entry = loadEntry(date: now)
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: now) ?? now.addingTimeInterval(1800)
        let nextDayNightRefresh = nextTaskWidgetDayNightRefresh(after: now)
        completion(Timeline(entries: [entry], policy: .after(min(nextRefresh, nextDayNightRefresh))))
    }

    private func loadEntry(date: Date = Date()) -> TodayTasksEntry {
        guard let snapshot = Self.loadSnapshot() else {
            return TodayTasksEntry(
                date: date,
                title: "Today's Tasks",
                status: .setup,
                taskCount: 0,
                tasks: []
            )
        }

        return TodayTasksEntry(
            date: date,
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
                    dueEpochMs: $0.dueEpochMs,
                    description: $0.description
                )
            },
            date: entry.date,
            mode: .today
        )
    }
}

private struct WidgetTaskRowModel: Identifiable {
    let id: String
    let title: String
    let priority: String
    let dueEpochMs: Int64?
    let description: String?

    var note: String? {
        guard let trimmed = description?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}

private enum TaskWidgetStatus: Equatable {
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

    func emptyWatermarkSystemName(isDaytime: Bool) -> String {
        switch self {
        case .today:
            return isDaytime ? "sun.max.fill" : "moon.stars.fill"
        case .floater:
            return "leaf"
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

}

private struct TdayTasksWidgetContent: View {
    let title: String
    let status: TaskWidgetStatus
    let taskCount: Int
    let rows: [WidgetTaskRowModel]
    let date: Date
    let mode: TaskWidgetMode

    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack(alignment: .topLeading) {
            messageWatermark

            switch status {
            case .setup:
                message(title: "Open T'Day", subtitle: "Set up your workspace")
            case .empty:
                message(title: mode.emptyTitle, subtitle: "")
            case .tasks:
                EmptyView()
            }

            VStack(alignment: .leading, spacing: metrics.contentSpacing) {
                header

                if status == .tasks {
                    taskList
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
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

    private var watermarkColor: Color {
        guard renderingMode == .fullColor else {
            return .primary.opacity(0.08)
        }
        let color: Color
        switch mode {
        case .today where !isTaskWidgetDaytime(date):
            color = .tdayTitleNight
        default:
            color = accentColor
        }
        return color.opacity(colorScheme == .dark ? 0.16 : 0.11)
    }

    private var countText: String {
        "\(taskCount) \(mode.countUnit)"
    }

    private var widgetBackground: some View {
        colorScheme == .dark ? Color.tdayDarkSurface : Color.tdayLightSurface
    }

    private var messageWatermark: some View {
        GeometryReader { proxy in
            Image(systemName: mode.emptyWatermarkSystemName(isDaytime: isTaskWidgetDaytime(date)))
                .font(.system(size: metrics.watermarkSize, weight: .regular))
                .foregroundStyle(watermarkColor)
                .rotationEffect(.degrees(-7))
                .frame(width: metrics.watermarkSize, height: metrics.watermarkSize)
                .position(
                    x: proxy.size.width - (metrics.watermarkSize / 2) + metrics.watermarkTrailingOffset,
                    y: proxy.size.height * metrics.watermarkVerticalFraction
                )
        }
        .accessibilityHidden(true)
    }

    private var header: some View {
        HStack(spacing: 8) {
            if family == .systemSmall {
                if status == .tasks {
                    smallCountLabel
                }
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(title)
                        .font(.system(size: family == .systemLarge ? 17 : 16, weight: .bold, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)

                    if status == .tasks {
                        countPill
                    }
                }
            }

            Spacer(minLength: 4)
            addButton
        }
        .frame(minHeight: metrics.headerHeight)
    }

    private var smallCountLabel: some View {
        Text(countText)
            .font(.system(size: 18, weight: .heavy, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .accessibilityLabel(countText)
    }

    private var countPill: some View {
        Text(countText)
            .font(.system(size: 12, weight: .heavy, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, 2)
            .frame(height: 26)
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
        // WidgetKit system-family widgets do not support true in-widget scrolling, so iOS keeps a best-fit row set plus overflow text.
        let totalCount = max(taskCount, rows.count)
        let visibleRows = visibleTaskRows(totalCount: totalCount)
        let overflowCount = max(0, totalCount - visibleRows.count)

        return VStack(alignment: .leading, spacing: metrics.rowSpacing) {
            ForEach(visibleRows) { row in
                taskRow(row)
            }
            if overflowCount > 0 {
                overflowRow(count: overflowCount)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func rowUnitCost(_ row: WidgetTaskRowModel) -> Int {
        metrics.showsNotes && row.note != nil ? 2 : 1
    }

    private func visibleTaskRows(totalCount: Int) -> [WidgetTaskRowModel] {
        let capacity = metrics.rowUnitCapacity

        // If every task is available and fits within the unit budget, show
        // them all and skip the overflow row entirely.
        if rows.count >= totalCount {
            let totalUnits = rows.reduce(0) { $0 + rowUnitCost($1) }
            if totalUnits <= capacity {
                return rows
            }
        }

        // Otherwise reserve 1 unit for the "+X more" row and fill rows
        // greedily in order until the next row would no longer fit.
        var visible: [WidgetTaskRowModel] = []
        var usedUnits = 0
        for row in rows {
            let cost = rowUnitCost(row)
            guard usedUnits + cost + 1 <= capacity else {
                break
            }
            visible.append(row)
            usedUnits += cost
        }
        return visible
    }

    private func message(title: String, subtitle: String) -> some View {
        VStack(alignment: .center, spacing: family == .systemSmall ? 6 : 8) {
            Text(title)
                .font(.system(size: family == .systemSmall ? 14 : (family == .systemLarge ? 17 : 15), weight: .bold, design: .rounded))
                .lineLimit(family == .systemSmall ? 1 : 2)
                .minimumScaleFactor(family == .systemSmall ? 0.75 : 0.85)

            if family != .systemSmall, !subtitle.isEmpty {
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

    private func taskRow(_ row: WidgetTaskRowModel) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 7) {
            priorityDot(row.priority, size: 7)
                // Pin the dot to the first line (near its vertical centre) instead
                // of centring it across a wrapped two-line title.
                .alignmentGuide(.firstTextBaseline) { dimension in
                    dimension[VerticalAlignment.center] + 3
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(row.title)
                    .font(.system(size: metrics.rowFontSize, weight: .bold, design: .rounded))
                    .lineLimit(2)
                if metrics.showsNotes, let note = row.note {
                    Text(note)
                        .font(.system(size: metrics.rowFontSize - 2, weight: .semibold, design: .rounded))
                        .foregroundStyle(secondaryTextColor)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 4)
            if mode.showsDueTime, family != .systemSmall, let dueEpochMs = row.dueEpochMs {
                dueTimeChip(Self.dueTimeText(from: dueEpochMs))
            }
        }
        .foregroundStyle(.primary)
        .padding(.horizontal, 2)
        .frame(minHeight: metrics.rowHeight, alignment: .leading)
        .accessibilityLabel(accessibilityLabel(for: row))
    }

    private func dueTimeChip(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .padding(.horizontal, 2)
            .frame(height: 22)
    }

    private func overflowRow(count: Int) -> some View {
        Text("+\(count) more")
            .font(.system(size: 11, weight: .heavy, design: .rounded))
            .foregroundStyle(secondaryTextColor)
            .lineLimit(1)
            .padding(.leading, 16)
            .frame(height: metrics.rowHeight, alignment: .leading)
    }

    private func priorityDot(_ priority: String, size: CGFloat) -> some View {
        Circle()
            .fill(widgetPriorityColor(priority))
            .frame(width: size, height: size)
    }

    private func widgetPriorityColor(_ priority: String) -> Color {
        guard renderingMode == .fullColor else {
            return .primary.opacity(0.78)
        }

        switch priority.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "high", "urgent":
            return colorScheme == .dark ? .tdayPriorityHighDark : .tdayPriorityHigh
        case "medium", "important":
            return colorScheme == .dark ? .tdayPriorityMediumDark : .tdayPriorityMedium
        default:
            return colorScheme == .dark ? .tdayPriorityLowDark : .tdayPriorityLow
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
    let rowSpacing: CGFloat
    let rowFontSize: CGFloat
    // Vertical budget in row units: a noteless row costs 1 unit, a row with a
    // visible note costs 2 units, and the "+X more" overflow row costs 1 unit.
    let rowUnitCapacity: Int
    let showsNotes: Bool
    let watermarkSize: CGFloat
    let watermarkTrailingOffset: CGFloat
    let watermarkVerticalFraction: CGFloat

    init(family: WidgetFamily) {
        switch family {
        case .systemSmall:
            contentSpacing = 5
            headerHeight = 38
            addButtonSize = 38
            addButtonCornerRadius = 12
            rowHeight = 21
            rowSpacing = 2
            rowFontSize = 12
            rowUnitCapacity = 2
            showsNotes = false
            watermarkSize = 116
            watermarkTrailingOffset = 18
            watermarkVerticalFraction = 0.70
        case .systemLarge:
            contentSpacing = 8
            headerHeight = 45
            addButtonSize = 46
            addButtonCornerRadius = 14
            rowHeight = 24
            rowSpacing = 4
            rowFontSize = 13
            // ~306pt usable - 45pt header - 8pt spacing ~= 253pt; 9 x 28pt units - 4 ~= 248pt.
            rowUnitCapacity = 9
            showsNotes = true
            watermarkSize = 224
            watermarkTrailingOffset = 28
            watermarkVerticalFraction = 0.68
        default:
            contentSpacing = 7
            headerHeight = 42
            addButtonSize = 42
            addButtonCornerRadius = 13
            rowHeight = 22
            rowSpacing = 3
            rowFontSize = 12
            rowUnitCapacity = 3
            showsNotes = true
            watermarkSize = 164
            watermarkTrailingOffset = 22
            watermarkVerticalFraction = 0.68
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
                    dueEpochMs: nil,
                    description: nil
                )
            },
            date: entry.date,
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
    static let tdayTitleNight = Color(red: 168.0 / 255.0, green: 184.0 / 255.0, blue: 232.0 / 255.0)
    static let tdayFloaterGreen = Color(red: 77.0 / 255.0, green: 143.0 / 255.0, blue: 131.0 / 255.0)
    static let tdayLightSurface = Color.white
    static let tdayDarkSurface = Color(red: 0.09, green: 0.10, blue: 0.13)
    static let tdayPriorityHigh = Color(red: 1.0, green: 59.0 / 255.0, blue: 48.0 / 255.0)
    static let tdayPriorityMedium = Color(red: 1.0, green: 149.0 / 255.0, blue: 0.0)
    static let tdayPriorityLow = Color(red: 0.0, green: 122.0 / 255.0, blue: 1.0)
    static let tdayPriorityHighDark = Color(red: 1.0, green: 107.0 / 255.0, blue: 97.0 / 255.0)
    static let tdayPriorityMediumDark = Color(red: 1.0, green: 180.0 / 255.0, blue: 84.0 / 255.0)
    static let tdayPriorityLowDark = Color(red: 121.0 / 255.0, green: 184.0 / 255.0, blue: 1.0)
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
