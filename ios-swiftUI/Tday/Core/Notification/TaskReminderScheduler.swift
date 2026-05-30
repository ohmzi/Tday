import Foundation
import UserNotifications

final class TaskReminderScheduler {
    private let reminderPreferenceStore: ReminderPreferenceStore

    init(reminderPreferenceStore: ReminderPreferenceStore) {
        self.reminderPreferenceStore = reminderPreferenceStore
    }

    func requestAuthorization() async {
        guard let notificationCenter else {
            return
        }
        _ = try? await notificationCenter.requestAuthorization(options: [.alert, .sound, .badge])
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

        for task in tasks where !task.completed {
            guard let due = task.due else {
                continue
            }
            let triggerDate = due.addingTimeInterval(-offsetSeconds)
            guard triggerDate > Date(), !reminderPreferenceStore.hasNotified(taskID: task.id) else {
                continue
            }

            let content = UNMutableNotificationContent()
            content.title = task.title
            content.body = task.description ?? "Due soon"
            content.sound = .default
            content.userInfo = ["deepLink": Self.deepLinkURLString(for: task.id)]

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
