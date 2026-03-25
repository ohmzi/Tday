#if canImport(WidgetKit) && canImport(SwiftUI)
import SwiftUI
import WidgetKit

private struct TodayTasksEntry: TimelineEntry {
    let date: Date
    let title: String
    let tasks: [String]
}

private struct TodayTasksProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayTasksEntry {
        TodayTasksEntry(date: Date(), title: "Today", tasks: ["Open Tday on iPhone", "Finish widget App Group wiring"])
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayTasksEntry) -> Void) {
        completion(placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayTasksEntry>) -> Void) {
        let entry = placeholder(in: context)
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date()) ?? Date().addingTimeInterval(1800)
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }
}

private struct TodayTasksWidgetView: View {
    let entry: TodayTasksEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(entry.title)
                .font(.headline)
            ForEach(entry.tasks.prefix(4), id: \.self) { task in
                Label(task, systemImage: "circle")
                    .font(.caption)
            }
            Spacer(minLength: 0)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

struct TodayTasksWidget: Widget {
    let kind = "TodayTasksWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TodayTasksProvider()) { entry in
            TodayTasksWidgetView(entry: entry)
                .widgetURL(URL(string: "tday://todos/today"))
        }
        .configurationDisplayName("Today's Tasks")
        .description("Shows the current Tday tasks for today.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
#endif
