import Foundation
import Sentry

enum SentryConfiguration {
    static func start() {
        SentrySDK.start { options in
            options.dsn = Bundle.main.infoDictionary?["SENTRY_DSN"] as? String ?? ""
            options.environment = ProcessInfo.processInfo.environment["SENTRY_ENVIRONMENT"] ?? "production"

            let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
            let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
            options.releaseName = "tday-ios@\(version)"
            options.dist = build

            options.sendDefaultPii = false
            options.enableAutoSessionTracking = true
            options.tracesSampleRate = 1.0

            options.beforeSend = { event in
                event.user?.ipAddress = nil
                return event
            }
        }
    }
}
