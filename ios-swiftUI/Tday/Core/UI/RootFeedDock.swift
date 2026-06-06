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

    var body: some View {
        ZStack {
            if collapsed {
                collapsedButton
                    .transition(
                        .scale(scale: 0.82, anchor: .leading)
                        .combined(with: .opacity)
                    )
            } else {
                expandedControl
                    .transition(
                        .scale(scale: 0.82, anchor: .leading)
                        .combined(with: .opacity)
                    )
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.82), value: collapsed)
    }

    // Icon-only pill shown when scrolled down.
    // Tapping calls onSelect(activeTab), which triggers scroll-to-top in the parent,
    // naturally returning scroll offset to 0 and expanding the dock back.
    private var collapsedButton: some View {
        Button {
            onSelect(activeTab)
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
