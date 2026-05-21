import SwiftUI

struct OfflineBanner: View {
    let visible: Bool
    let pendingMutationCount: Int

    @Environment(\.tdayColors) private var colors
    @GestureState private var dragOffsetY: CGFloat = 0
    @State private var isPresented = false
    @State private var dismissalTask: Task<Void, Never>?

    var body: some View {
        Group {
            if isPresented {
                bannerContent
            }
        }
        .animation(.spring(response: 0.26, dampingFraction: 0.86), value: isPresented)
        .onAppear {
            updatePresentation(visible)
        }
        .onChange(of: visible) { _, newValue in
            updatePresentation(newValue)
        }
    }

    private var bannerContent: some View {
        HStack(spacing: 12) {
            iconContent
            textContent
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(colors.surfaceVariant.opacity(0.72), lineWidth: 1)
        }
        .shadow(color: Color.black.opacity(0.10), radius: 14, x: 0, y: 8)
        .padding(.horizontal, 16)
        .offset(y: dragOffsetY)
        .scaleEffect(1 - (dragProgress * 0.025))
        .opacity(1 - (dragProgress * 0.50))
        .contentShape(Rectangle())
        .onTapGesture {
            dismissNotice()
        }
        .gesture(dragDismissGesture)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    private var iconContent: some View {
        ZStack {
            Circle()
                .fill(colors.primary.opacity(0.14))
            Image(systemName: "wifi.slash")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(colors.primary)
        }
        .frame(width: 38, height: 38)
    }

    private var textContent: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("Offline mode")
                .font(.tdayRounded(.subheadline, weight: .heavy))
                .foregroundStyle(colors.onSurface)
            Text(subtitle)
                .font(.tdayRounded(.caption, weight: .bold))
                .foregroundStyle(colors.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var dragDismissGesture: some Gesture {
        DragGesture(minimumDistance: 4)
            .updating($dragOffsetY) { value, state, _ in
                state = min(value.translation.height, 0)
            }
            .onEnded { value in
                if value.translation.height < 0 {
                    dismissNotice()
                }
            }
    }

    private var subtitle: String {
        if pendingMutationCount == 1 {
            return "1 change waiting to sync"
        }
        if pendingMutationCount > 1 {
            return "\(pendingMutationCount) changes waiting to sync"
        }
        return "Changes will sync when connection returns."
    }

    private var dragProgress: CGFloat {
        min(abs(min(dragOffsetY, 0)) / 88, 1)
    }

    private func updatePresentation(_ shouldShow: Bool) {
        dismissalTask?.cancel()

        guard shouldShow else {
            dismissNotice(cancelTimer: false)
            return
        }

        isPresented = true
        dismissalTask = Task {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                dismissNotice(cancelTimer: false)
            }
        }
    }

    private func dismissNotice(cancelTimer: Bool = true) {
        if cancelTimer {
            dismissalTask?.cancel()
        }
        dismissalTask = nil
        isPresented = false
    }
}
