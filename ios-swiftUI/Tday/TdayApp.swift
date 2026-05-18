import Sentry
import SwiftData
import SwiftUI

@main
struct TdayApp: App {
    @State private var appContainer = AppContainer.shared

    init() {
        TdayFont.applyGlobalAppearances()
        SentryConfiguration.start()
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(container: appContainer)
                .tdayAppTypography()
                .modelContainer(appContainer.modelContainer)
        }
    }
}
