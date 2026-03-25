import SwiftData
import SwiftUI

@main
struct TdayApp: App {
    @State private var appContainer = AppContainer.shared

    var body: some Scene {
        WindowGroup {
            AppRootView(container: appContainer)
                .modelContainer(appContainer.modelContainer)
        }
    }
}
