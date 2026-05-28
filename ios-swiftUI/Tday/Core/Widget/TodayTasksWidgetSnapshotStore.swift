import Foundation

#if canImport(WidgetKit)
import WidgetKit
#endif

struct TodayTasksWidgetSnapshot: Codable, Equatable {
    let generatedAtEpochMs: Int64
    let title: String
    let taskCount: Int
    let tasks: [TodayTasksWidgetTaskSnapshot]
}

struct TodayTasksWidgetTaskSnapshot: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let dueEpochMs: Int64
    let priority: String
}

enum TodayTasksWidgetSnapshotStore {
    static let widgetKind = "TodayTasksWidget"
    static let appGroupSuiteName = "group.com.ohmz.tday"
    static let snapshotKey = "tday.widget.todayTasksSnapshot"

    static func makeSnapshot(
        from state: OfflineSyncState,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> TodayTasksWidgetSnapshot {
        let dayStart = calendar.startOfDay(for: now)
        let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart.addingTimeInterval(86_400)
        let dayStartEpochMs = Int64(dayStart.timeIntervalSince1970 * 1_000)
        let dayEndEpochMs = Int64(dayEnd.timeIntervalSince1970 * 1_000)

        let todayTasks = state.todos
            .filter {
                guard let dueEpochMs = $0.dueEpochMs else {
                    return false
                }
                return !$0.completed && dueEpochMs >= dayStartEpochMs && dueEpochMs < dayEndEpochMs
            }
            .sorted { left, right in
                let leftDue = left.dueEpochMs ?? Int64.max
                let rightDue = right.dueEpochMs ?? Int64.max
                if leftDue == rightDue {
                    return left.title.localizedStandardCompare(right.title) == .orderedAscending
                }
                return leftDue < rightDue
            }

        return TodayTasksWidgetSnapshot(
            generatedAtEpochMs: Int64(now.timeIntervalSince1970 * 1_000),
            title: "Today's Tasks",
            taskCount: todayTasks.count,
            tasks: todayTasks.prefix(8).map {
                TodayTasksWidgetTaskSnapshot(
                    id: $0.id,
                    title: $0.title,
                    dueEpochMs: $0.dueEpochMs ?? dayStartEpochMs,
                    priority: $0.priority
                )
            }
        )
    }

    static func saveTodayTasks(from state: OfflineSyncState) {
        let snapshot = makeSnapshot(from: state)
        guard let data = try? JSONEncoder().encode(snapshot) else {
            return
        }

        let stores = defaultsStores()
        stores.forEach { store in
            store.set(data, forKey: snapshotKey)
        }

        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
        #endif
    }

    static func loadSnapshot() -> TodayTasksWidgetSnapshot? {
        for store in defaultsStores() {
            guard let data = store.data(forKey: snapshotKey),
                  let snapshot = try? JSONDecoder().decode(TodayTasksWidgetSnapshot.self, from: data) else {
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
}
