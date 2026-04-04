import SwiftUI

struct UpdateRequiredView: View {
    let versionCheckResult: VersionCheckResult
    let onRetry: () -> Void
    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(spacing: 20) {
                switch versionCheckResult {
                case let .appUpdateRequired(requiredVersion):
                    Image(systemName: "arrow.down.app.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(colors.primary)

                    Text("Update Required")
                        .font(.title2.bold())
                        .foregroundStyle(colors.onSurface)

                    Text("An update is required to continue. Please update to v\(requiredVersion).")
                        .font(.subheadline)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)

                    Button("Download Update") {
                        if let url = URL(string: "https://github.com/ohmzi/Tday/releases/tag/v\(requiredVersion)") {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.borderedProminent)

                    Button("Retry") {
                        onRetry()
                    }
                    .buttonStyle(.bordered)

                case let .serverUpdateRequired(serverVersion):
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(colors.error)

                    Text("Server Update Needed")
                        .font(.title2.bold())
                        .foregroundStyle(colors.onSurface)

                    Text("Your app requires a newer server version. The server is on v\(serverVersion). Please contact your administrator to update the server.")
                        .font(.subheadline)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)

                    Button("Retry") {
                        onRetry()
                    }
                    .buttonStyle(.bordered)

                case .compatible:
                    EmptyView()
                }
            }
            .padding(28)
            .frame(maxWidth: 420)
            .background(colors.surface.opacity(0.98))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .shadow(color: .black.opacity(0.2), radius: 30, x: 0, y: 24)
            .padding(24)
        }
        .interactiveDismissDisabled()
    }
}
