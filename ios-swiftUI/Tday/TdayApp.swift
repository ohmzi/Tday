import Sentry
import SwiftData
import SwiftUI

@main
struct TdayApp: App {
    @State private var appContainer = AppContainer.shared

    init() {
        SentryConfiguration.start()
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(container: appContainer)
                .modelContainer(appContainer.modelContainer)
        }
    }
}
