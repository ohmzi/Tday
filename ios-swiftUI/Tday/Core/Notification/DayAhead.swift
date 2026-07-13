import Foundation
import UserNotifications

/// The Day Ahead digest setting: one quiet morning notification with the
/// day's counts, or off. A single picker (off / hour) keeps it simple.
enum DayAheadOption: String, CaseIterable, Identifiable {
    case off
    case h7
    case h8
    case h9

    var id: String { rawValue }

    var hour: Int? {
        switch self {
        case .off: return nil
        case .h7: return 7
        case .h8: return 8
        case .h9: return 9
        }
    }

    var label: String {
        switch self {
        case .off: return L("Off")
        case .h7: return "7:00"
        case .h8: return "8:00"
        case .h9: return "9:00"
        }
    }
}

final class DayAheadStore {
    private let defaults = UserDefaults.standard
    private let key = "dayAhead.option"

    func getOption() -> DayAheadOption {
        guard let raw = defaults.string(forKey: key), let option = DayAheadOption(rawValue: raw) else {
            return .off
        }
        return option
    }

    func setOption(_ option: DayAheadOption) {
        defaults.set(option.rawValue, forKey: key)
    }
}

/// Schedules the Day Ahead digest as a repeating morning notification.
/// The counts are computed from the local cache at scheduling time — every
/// reminder-reschedule pass (app foreground, sync, setting change) refreshes
/// them, which is as fresh as a fully on-device digest can be. The counts
/// look at TOMORROW morning's perspective: tasks due that day, plus
/// everything still open from before it (carried over). Deep-links into
/// Today, or straight into Morning Sweep when something is carried over.
final class DayAheadScheduler {
    static let identifier = "tday.dayAhead"

    private let store: DayAheadStore

    init(store: DayAheadStore) {
        self.store = store
    }

    func reschedule(tasks: [TodoItem]) async {
        guard let notificationCenter else { return }
        notificationCenter.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        guard let hour = store.getOption().hour else { return }

        let calendar = Calendar.current
        let startOfTomorrow = calendar.date(
            byAdding: .day,
            value: 1,
            to: calendar.startOfDay(for: Date())
        ) ?? Date()
        let endOfTomorrow = calendar.date(byAdding: .day, value: 1, to: startOfTomorrow) ?? startOfTomorrow

        // Recurring templates carry a stale template due; count one-offs only.
        let open = tasks.filter { !$0.completed && !$0.isRecurring && $0.instanceDate == nil }
        let dayCount = open.filter { todo in
            guard let due = todo.due else { return false }
            return due >= startOfTomorrow && due < endOfTomorrow
        }.count
        let carriedCount = open.filter { todo in
            guard let due = todo.due else { return false }
            return due < startOfTomorrow
        }.count
        guard dayCount > 0 || carriedCount > 0 else { return }

        let content = UNMutableNotificationContent()
        content.title = L("Your day ahead")
        content.body = carriedCount > 0
            ? String(format: L("%1$d tasks today, %2$d carried over"), dayCount, carriedCount)
            : String(format: L("%d tasks today"), dayCount)
        content.sound = nil
        content.userInfo = [
            "deepLink": carriedCount > 0 ? "tday://morning-sweep" : "tday://todos/today",
        ]

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: DateComponents(hour: hour, minute: 0),
            repeats: true
        )
        let request = UNNotificationRequest(identifier: Self.identifier, content: content, trigger: trigger)
        try? await notificationCenter.add(request)
    }

    private var notificationCenter: UNUserNotificationCenter? {
        guard Bundle.main.bundleURL.pathExtension == "app" else {
            return nil
        }
        return UNUserNotificationCenter.current()
    }
}
