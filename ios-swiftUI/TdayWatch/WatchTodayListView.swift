import SwiftUI

/// The wrist view: today's tasks, mirrored from the phone. Read-only — the watch
/// is a glanceable companion, not an editor (R6-4 keeps scope tight).
struct WatchTodayListView: View {
    let snapshot: WatchTodaySnapshot

    var body: some View {
        NavigationStack {
            Group {
                switch snapshot.status {
                case .setup:
                    WatchEmptyState(
                        systemImage: "iphone",
                        message: "Open T'Day on your iPhone to get started."
                    )
                case .empty:
                    WatchEmptyState(
                        systemImage: "checkmark.circle",
                        message: "Nothing left for today."
                    )
                case .tasks:
                    List(snapshot.tasks) { task in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.title)
                                .font(.body)
                                .lineLimit(2)
                            Text(dueLabel(task.dueEpochMs))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            .navigationTitle(snapshot.title)
        }
    }

    private func dueLabel(_ epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
        return date.formatted(date: .omitted, time: .shortened)
    }
}

struct WatchEmptyState: View {
    let systemImage: String
    let message: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(.secondary)
            Text(message)
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
