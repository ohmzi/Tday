import Foundation
import Observation
import UserNotifications

/// A reminder-notification action that needs the app's data layer (the
/// delegate handles snooze itself; moving a task's due date does not belong
/// in a notification callback).
enum PendingReminderAction: Equatable {
    case moveTonight(taskID: String)
}

@MainActor
@Observable
final class NotificationDeepLinkRouter {
    static let shared = NotificationDeepLinkRouter()

    var pendingURL: URL?
    var pendingReminderAction: PendingReminderAction?

    private init() {}

    func route(_ url: URL) {
        pendingURL = url
    }

    func clearPendingURL() {
        pendingURL = nil
    }

    func route(_ action: PendingReminderAction) {
        pendingReminderAction = action
    }

    func clearPendingReminderAction() {
        pendingReminderAction = nil
    }
}

final class NotificationDeepLinkDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDeepLinkDelegate()

    private override init() {}

    func install() {
        UNUserNotificationCenter.current().delegate = self
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let content = response.notification.request.content
        let taskID = content.userInfo["taskId"] as? String

        switch response.actionIdentifier {
        case TaskReminderScheduler.snoozeHourActionID:
            guard let taskID else { return }
            // Self-contained: re-post the same content in an hour. The store
            // is a plain UserDefaults wrapper, safe to instantiate here.
            let scheduler = TaskReminderScheduler(reminderPreferenceStore: ReminderPreferenceStore())
            await scheduler.snooze(taskID: taskID, content: content, interval: 3600)
            return
        case TaskReminderScheduler.tonightActionID:
            guard let taskID else { return }
            await MainActor.run {
                NotificationDeepLinkRouter.shared.route(.moveTonight(taskID: taskID))
            }
            return
        default:
            break
        }

        guard let url = Self.deepLinkURL(from: content.userInfo) else {
            return
        }

        await MainActor.run {
            NotificationDeepLinkRouter.shared.route(url)
        }
    }

    static func deepLinkURL(from userInfo: [AnyHashable: Any]) -> URL? {
        guard let deepLink = userInfo["deepLink"] as? String else {
            return nil
        }
        return URL(string: deepLink)
    }
}
