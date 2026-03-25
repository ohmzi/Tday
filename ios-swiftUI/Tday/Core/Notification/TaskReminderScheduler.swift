import Foundation
import UserNotifications

final class TaskReminderScheduler {
    private let reminderPreferenceStore: ReminderPreferenceStore
    private let notificationCenter = UNUserNotificationCenter.current()

    init(reminderPreferenceStore: ReminderPreferenceStore) {
        self.reminderPreferenceStore = reminderPreferenceStore
    }

    func requestAuthorization() async {
        _ = try? await notificationCenter.requestAuthorization(options: [.alert, .sound, .badge])
    }

    func reschedule(tasks: [TodoItem], defaultReminder: ReminderOption) async {
        let identifiers = tasks.map { notificationIdentifier(for: $0) }
        notificationCenter.removePendingNotificationRequests(withIdentifiers: identifiers)

        guard let offsetSeconds = defaultReminder.offsetSeconds else {
            return
        }

        for task in tasks where !task.completed {
            let triggerDate = task.due.addingTimeInterval(-offsetSeconds)
            guard triggerDate > Date(), !reminderPreferenceStore.hasNotified(taskID: task.id) else {
                continue
            }

            let content = UNMutableNotificationContent()
            content.title = task.title
            content.body = task.description ?? "Due soon"
            content.sound = .default
            content.userInfo = ["deepLink": "tday://todos/all?highlightTodoId=\(task.id)"]

            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: notificationIdentifier(for: task), content: content, trigger: trigger)
            try? await notificationCenter.add(request)
        }
    }

    private func notificationIdentifier(for task: TodoItem) -> String {
        "tday.todo.\(task.id)"
    }
}
