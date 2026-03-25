import SwiftUI

struct PullToRefreshContainer<Content: View>: View {
    let action: @Sendable () async -> Void
    @ViewBuilder let content: Content

    var body: some View {
        content
            .refreshable {
                await action()
            }
    }
}
