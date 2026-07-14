import Foundation
import UserNotifications

final class TaskReminderScheduler {
    static let reminderCategoryID = "TASK_REMINDER"
    static let snoozeHourActionID = "TDAY_SNOOZE_1H"
    static let tonightActionID = "TDAY_MOVE_TONIGHT"

    private let reminderPreferenceStore: ReminderPreferenceStore

    init(reminderPreferenceStore: ReminderPreferenceStore) {
        self.reminderPreferenceStore = reminderPreferenceStore
    }

    func requestAuthorization() async {
        guard let notificationCenter else {
            return
        }
        _ = try? await notificationCenter.requestAuthorization(options: [.alert, .sound, .badge])
        registerReminderCategory()
    }

    /// Snooze / Tonight actions shown on every task-reminder notification.
    private func registerReminderCategory() {
        guard let notificationCenter else {
            return
        }
        let snooze = UNNotificationAction(
            identifier: Self.snoozeHourActionID,
            title: L("Snooze 1h")
        )
        let tonight = UNNotificationAction(
            identifier: Self.tonightActionID,
            title: L("Tonight")
        )
        let category = UNNotificationCategory(
            identifier: Self.reminderCategoryID,
            actions: [snooze, tonight],
            intentIdentifiers: []
        )
        notificationCenter.setNotificationCategories([category])
    }

    /// Re-presents a delivered reminder after [interval]. Clears the
    /// notified marker first — without that the next reschedule pass would
    /// silently suppress the task's reminders.
    func snooze(taskID: String, content: UNNotificationContent, interval: TimeInterval) async {
        guard let notificationCenter else {
            return
        }
        reminderPreferenceStore.clearNotified(taskID: taskID)
        guard let snoozedContent = content.mutableCopy() as? UNMutableNotificationContent else {
            return
        }
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        let request = UNNotificationRequest(
            identifier: "tday.todo.\(taskID).snoozed",
            content: snoozedContent,
            trigger: trigger
        )
        try? await notificationCenter.add(request)
        TdayTelemetry.addBreadcrumb("reminder.snooze", data: ["intervalSeconds": Int(interval)])
    }

    func reschedule(tasks: [TodoItem], defaultReminder: ReminderOption) async {
        guard let notificationCenter else {
            return
        }
        TdayTelemetry.addBreadcrumb(
            "reminder.reschedule",
            data: ["taskCount": tasks.count, "enabled": defaultReminder.offsetSeconds != nil]
        )
        let identifiers = tasks.map { notificationIdentifier(for: $0) }
        notificationCenter.removePendingNotificationRequests(withIdentifiers: identifiers)

        guard let offsetSeconds = defaultReminder.offsetSeconds else {
            return
        }

        let quietHours = QuietHoursStore()
        for task in tasks where !task.completed {
            guard let due = task.due else {
                continue
            }
            var triggerDate = due.addingTimeInterval(-offsetSeconds)
            // Quiet hours: shift a reminder that would fire inside the held window to the
            // window end, so nothing buzzes overnight.
            if quietHours.isEnabled {
                let comps = Calendar.current.dateComponents([.hour, .minute], from: triggerDate)
                let minuteOfDay = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
                let shift = QuietHoursMath.minutesUntilWindowEnd(
                    minuteOfDay, quietHours.startMinute, quietHours.endMinute
                )
                if shift > 0 {
                    triggerDate = triggerDate.addingTimeInterval(Double(shift) * 60)
                }
            }
            guard triggerDate > Date(), !reminderPreferenceStore.hasNotified(taskID: task.id) else {
                continue
            }

            let content = UNMutableNotificationContent()
            content.title = task.title
            content.body = task.description ?? "Due soon"
            content.sound = .default
            content.categoryIdentifier = Self.reminderCategoryID
            content.userInfo = [
                "deepLink": Self.deepLinkURLString(for: task.id),
                "taskId": task.id,
            ]

            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: notificationIdentifier(for: task), content: content, trigger: trigger)
            try? await notificationCenter.add(request)
        }
    }

    private func notificationIdentifier(for task: TodoItem) -> String {
        "tday.todo.\(task.id)"
    }

    private static func deepLinkURLString(for taskID: String) -> String {
        var components = URLComponents()
        components.scheme = "tday"
        components.host = "todos"
        components.path = "/all"
        components.queryItems = [URLQueryItem(name: "highlightTodoId", value: taskID)]
        return components.url?.absoluteString ?? "tday://todos/all"
    }

    private var notificationCenter: UNUserNotificationCenter? {
        guard Bundle.main.bundleURL.pathExtension == "app" else {
            return nil
        }
        return UNUserNotificationCenter.current()
    }
}

/// Local "hold reminders between HH:MM and HH:MM" setting (UserDefaults). Times are
/// minute-of-day (0..1439). Off by default.
struct QuietHoursStore {
    private let defaults = UserDefaults.standard
    private static let enabledKey = "quietHours.enabled"
    private static let startKey = "quietHours.startMinute"
    private static let endKey = "quietHours.endMinute"

    var isEnabled: Bool {
        get { defaults.bool(forKey: Self.enabledKey) }
        nonmutating set { defaults.set(newValue, forKey: Self.enabledKey) }
    }

    var startMinute: Int {
        get { defaults.object(forKey: Self.startKey) as? Int ?? 22 * 60 }
        nonmutating set { defaults.set(newValue, forKey: Self.startKey) }
    }

    var endMinute: Int {
        get { defaults.object(forKey: Self.endKey) as? Int ?? 7 * 60 }
        nonmutating set { defaults.set(newValue, forKey: Self.endKey) }
    }
}

/// Swift twin of the shared Kotlin QuietHours math. Kept in sync by hand + tests.
enum QuietHoursMath {
    static let minutesPerDay = 24 * 60

    static func contains(_ minuteOfDay: Int, _ startMinute: Int, _ endMinute: Int) -> Bool {
        if startMinute == endMinute { return false }
        let t = ((minuteOfDay % minutesPerDay) + minutesPerDay) % minutesPerDay
        if startMinute < endMinute {
            return t >= startMinute && t < endMinute
        }
        return t >= startMinute || t < endMinute
    }

    static func minutesUntilWindowEnd(_ minuteOfDay: Int, _ startMinute: Int, _ endMinute: Int) -> Int {
        if !contains(minuteOfDay, startMinute, endMinute) { return 0 }
        let t = ((minuteOfDay % minutesPerDay) + minutesPerDay) % minutesPerDay
        let delta = (endMinute - t + minutesPerDay) % minutesPerDay
        return delta == 0 ? minutesPerDay : delta
    }
}
