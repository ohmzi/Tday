import SwiftUI

enum RootFeedTab: Hashable {
    case home
    case floater

    var title: String {
        switch self {
        case .home:
            return L("Home")
        case .floater:
            return L("Floater")
        }
    }

    var systemImage: String {
        switch self {
        case .home:
            return "house.fill"
        case .floater:
            return "leaf"
        }
    }
}

struct RootFeedDock: View {
    let activeTab: RootFeedTab
    let collapsed: Bool
    let accentColor: Color
    let onSelect: (RootFeedTab) -> Void

    private let tabs: [RootFeedTab] = [.home, .floater]
    @Environment(\.tdayColors) private var colors
    @State private var expandedByTap = false

    init(
        activeTab: RootFeedTab,
        collapsed: Bool,
        accentColor: Color = .tdayTodayBlue,
        onSelect: @escaping (RootFeedTab) -> Void
    ) {
        self.activeTab = activeTab
        self.collapsed = collapsed
        self.accentColor = accentColor
        self.onSelect = onSelect
    }

    private var activeIndex: Int {
        tabs.firstIndex(of: activeTab) ?? 0
    }

    // The dock expands when the feed is at the top, or when the user taps the collapsed pill.
    // Tap-to-expand keeps Home/Floater immediately pressable instead of relying on a
    // scroll-to-top to bring the dock back, which left it stuck as an icon if the feed didn't
    // scroll far enough. Mirrors the Android dock's `expandedByTap`.
    private var isExpanded: Bool {
        !collapsed || expandedByTap
    }

    var body: some View {
        ZStack {
            if isExpanded {
                expandedControl
                    .transition(
                        .scale(scale: 0.82, anchor: .leading)
                        .combined(with: .opacity)
                    )
            } else {
                collapsedButton
                    .transition(
                        .scale(scale: 0.82, anchor: .leading)
                        .combined(with: .opacity)
                    )
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.82), value: isExpanded)
        .onChange(of: collapsed) { _, isCollapsed in
            // Scrolling back to the top expands the dock on its own, so drop the tap override.
            if !isCollapsed {
                expandedByTap = false
            }
        }
        .task(id: expandedByTap) {
            // Auto-collapse a tap-expanded dock if no tab is chosen, matching the Android timeout.
            guard expandedByTap else { return }
            try? await Task.sleep(nanoseconds: 2_400_000_000)
            guard !Task.isCancelled else { return }
            expandedByTap = false
        }
    }

    // Icon-only pill shown when scrolled down. Tapping expands the dock in place so the
    // Home/Floater control becomes immediately pressable.
    private var collapsedButton: some View {
        Button {
            expandedByTap = true
        } label: {
            Group {
                if activeTab == .home {
                    Image("NavHouse")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22, height: 22)
                } else {
                    Image(systemName: activeTab.systemImage)
                        .font(.system(size: 22, weight: .semibold))
                        .scaleEffect(x: activeTab == .floater ? -1 : 1, y: 1)
                }
            }
            .foregroundStyle(accentColor)
            .frame(width: RootFeedDockMetrics.collapsedWidth, height: RootFeedDockMetrics.height)
            .background(colors.surfaceVariant.opacity(0.76), in: Capsule())
            .overlay(
                Capsule()
                    .stroke(colors.onSurface.opacity(0.15), lineWidth: 1)
            )
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: .black,
                pressedShadowOpacity: 0.08,
                normalShadowOpacity: 0.18
            )
        )
        .accessibilityLabel(activeTab.title)
    }

    private var expandedControl: some View {
        TdayNativeSegmentedControl(
            labels: tabs.map(\.title),
            selectedIndex: activeIndex,
            accentColor: accentColor,
            controlHeight: RootFeedDockMetrics.height,
            fontSize: RootFeedDockMetrics.fontSize,
            onSelect: { index in
                guard tabs.indices.contains(index) else {
                    return
                }
                onSelect(tabs[index])
            }
        )
        .frame(width: RootFeedDockMetrics.width)
        .frame(height: RootFeedDockMetrics.height)
    }
}

private enum RootFeedDockMetrics {
    static let width: CGFloat = 212
    static let collapsedWidth: CGFloat = 60
    static let height: CGFloat = 60
    static let fontSize: CGFloat = 14.5
}
