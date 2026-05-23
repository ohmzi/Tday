import Foundation
import Observation
import UserNotifications

@MainActor
@Observable
final class NotificationDeepLinkRouter {
    static let shared = NotificationDeepLinkRouter()

    var pendingURL: URL?

    private init() {}

    func route(_ url: URL) {
        pendingURL = url
    }

    func clearPendingURL() {
        pendingURL = nil
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
        guard let url = Self.deepLinkURL(from: response.notification.request.content.userInfo) else {
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
