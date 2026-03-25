import SwiftUI

struct ErrorRetryView: View {
    let message: String
    let retryTitle: String
    let onRetry: () -> Void
    @Environment(\.tdayColors) private var colors

    init(message: String, retryTitle: String = "Retry", onRetry: @escaping () -> Void) {
        self.message = message
        self.retryTitle = retryTitle
        self.onRetry = onRetry
    }

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 30))
                .foregroundStyle(colors.tertiary)
            Text(message)
                .font(.body)
                .foregroundStyle(colors.onSurface)
                .multilineTextAlignment(.center)
            Button(retryTitle, action: onRetry)
                .buttonStyle(.borderedProminent)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding(20)
    }
}
