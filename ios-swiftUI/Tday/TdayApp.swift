import Sentry
import SwiftData
import SwiftUI

@main
struct TdayApp: App {
    @State private var appContainer: AppContainer?
    @State private var isLaunchSplashHeld = false

    init() {
        TdayFont.applyGlobalAppearances()
        SentryConfiguration.start()
        NotificationDeepLinkDelegate.shared.install()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if let appContainer, !isLaunchSplashHeld {
                    AppRootView(container: appContainer)
                        .modelContainer(appContainer.modelContainer)
                } else {
                    AppLaunchSplashView(isHeld: $isLaunchSplashHeld)
                }
            }
            .tdayAppTypography()
            .task {
                guard appContainer == nil else {
                    return
                }
                await MainActor.run {
                    appContainer = AppContainer.shared
                }
            }
        }
    }
}
