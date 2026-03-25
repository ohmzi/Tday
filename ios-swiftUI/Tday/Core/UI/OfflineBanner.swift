import SwiftUI

struct OfflineBanner: View {
    let visible: Bool
    let pendingMutationCount: Int
    @Environment(\.tdayColors) private var colors

    var body: some View {
        if visible {
            HStack(spacing: 10) {
                Image(systemName: "wifi.slash")
                Text(pendingMutationCount > 0 ? "Offline. \(pendingMutationCount) changes waiting to sync." : "Offline mode")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(colors.onPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(colors.primary)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}
