import SwiftUI
import WidgetKit

// A watch-face complication showing how many tasks are left today. It reads the
// snapshot the watch app persisted into the shared App Group (fed from the phone
// over WatchConnectivity — see TdayWatch/TdayWatchApp.swift), so it needs no
// network of its own.

private enum WatchComplicationStore {
    static let suiteName = "group.com.ohmz.tday"
    static let key = "tday.watch.todaySnapshot"

    static func remainingCount() -> Int {
        guard let defaults = UserDefaults(suiteName: suiteName),
              let data = defaults.data(forKey: key),
              let snapshot = try? JSONDecoder().decode(WatchComplicationSnapshot.self, from: data) else {
            return 0
        }
        return snapshot.taskCount
    }
}

/// Minimal mirror — only the field the complication renders.
private struct WatchComplicationSnapshot: Codable {
    let taskCount: Int
}

struct TdayComplicationEntry: TimelineEntry {
    let date: Date
    let remaining: Int
}

struct TdayComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> TdayComplicationEntry {
        TdayComplicationEntry(date: Date(), remaining: 3)
    }

    func getSnapshot(in context: Context, completion: @escaping (TdayComplicationEntry) -> Void) {
        completion(TdayComplicationEntry(date: Date(), remaining: WatchComplicationStore.remainingCount()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TdayComplicationEntry>) -> Void) {
        let entry = TdayComplicationEntry(date: Date(), remaining: WatchComplicationStore.remainingCount())
        // The watch app pushes a reload when a fresh snapshot arrives; a slow
        // hourly refresh is a safe backstop.
        let next = Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date().addingTimeInterval(3600)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

struct TdayComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TdayComplicationEntry

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 0) {
                    Text("\(entry.remaining)")
                        .font(.title2.bold())
                    Text("left")
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                }
            }
        case .accessoryInline:
            Text(entry.remaining == 0 ? "T'Day: all done" : "T'Day: \(entry.remaining) left")
        default:
            HStack(spacing: 4) {
                Image(systemName: "checklist")
                Text(entry.remaining == 0 ? "All done" : "\(entry.remaining) left")
            }
        }
    }
}

@main
struct TdayWatchComplication: Widget {
    private let kind = "TdayWatchComplication"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TdayComplicationProvider()) { entry in
            TdayComplicationView(entry: entry)
        }
        .configurationDisplayName("Tasks Left")
        .description("How many T'Day tasks are left today.")
        .supportedFamilies([.accessoryCircular, .accessoryInline, .accessoryRectangular])
    }
}
