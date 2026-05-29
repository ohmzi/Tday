import SwiftUI

enum RootFeedTab: Hashable {
    case home
    case floater

    var title: String {
        switch self {
        case .home:
            return "Home"
        case .floater:
            return "Floater"
        }
    }

    var systemImage: String {
        switch self {
        case .home:
            return "house.fill"
        case .floater:
            return "tray.full.fill"
        }
    }
}

struct RootFeedDock: View {
    let activeTab: RootFeedTab
    let collapsed: Bool
    let onSelect: (RootFeedTab) -> Void

    @Environment(\.tdayColors) private var colors
    @State private var tapExpanded = false

    private let tabs: [RootFeedTab] = [.home, .floater]
    private let accentColor = Color(red: 125.0 / 255.0, green: 103.0 / 255.0, blue: 182.0 / 255.0)
    private let animation = Animation.interactiveSpring(response: 0.36, dampingFraction: 0.88, blendDuration: 0.04)

    private var isExpanded: Bool {
        !collapsed || tapExpanded
    }

    private var activeIndex: Int {
        tabs.firstIndex(of: activeTab) ?? 0
    }

    var body: some View {
        GeometryReader { proxy in
            let dockWidth = proxy.size.width
            let innerWidth = Swift.max(0, dockWidth - (RootFeedDockMetrics.innerPadding * 2))
            let innerHeight = RootFeedDockMetrics.height - (RootFeedDockMetrics.innerPadding * 2)
            let tabWidth = Swift.min(innerWidth, RootFeedDockMetrics.tabWidth)
            let selectorWidth = tabWidth
            let selectorX = activeIndex == 0 ? 0 : innerWidth - selectorWidth

            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: RootFeedDockMetrics.cornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .background(
                        RoundedRectangle(cornerRadius: RootFeedDockMetrics.cornerRadius, style: .continuous)
                            .fill(colors.surfaceVariant.opacity(colors.isDark ? 0.76 : 0.68))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: RootFeedDockMetrics.cornerRadius, style: .continuous)
                            .stroke(colors.isDark ? colors.onSurfaceVariant.opacity(0.12) : colors.surface.opacity(0.72), lineWidth: 1)
                    )

                ZStack(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: RootFeedDockMetrics.selectorCornerRadius, style: .continuous)
                        .fill(colors.isDark ? colors.background.opacity(0.90) : colors.surface.opacity(0.98))
                        .overlay(
                            RoundedRectangle(cornerRadius: RootFeedDockMetrics.selectorCornerRadius, style: .continuous)
                                .fill(accentColor.opacity(colors.isDark ? 0.04 : 0.06))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: RootFeedDockMetrics.selectorCornerRadius, style: .continuous)
                                .stroke(colors.isDark ? colors.onSurfaceVariant.opacity(0.24) : colors.onSurface.opacity(0.10), lineWidth: 1)
                        )
                        .shadow(color: accentColor.opacity(0.16), radius: 12, x: 0, y: 7)
                        .shadow(color: Color.black.opacity(0.12), radius: 9, x: 0, y: 5)
                        .frame(width: selectorWidth, height: innerHeight)
                        .offset(x: selectorX)

                    ForEach(Array(tabs.enumerated()), id: \.element) { index, tab in
                        let selected = tab == activeTab
                        let expandedX = CGFloat(index) * tabWidth
                        let hiddenX = index < activeIndex ? -tabWidth : innerWidth
                        let tabX = selected ? selectorX : (isExpanded ? expandedX : hiddenX)

                        Button {
                            if !isExpanded && selected {
                                tapExpanded = true
                            } else {
                                onSelect(tab)
                            }
                        } label: {
                            Text(tab.title)
                                .font(.tdayRounded(size: 13, weight: selected ? .black : .bold))
                                .foregroundStyle(selected ? colors.onSurface : colors.onSurfaceVariant.opacity(0.82))
                                .lineLimit(1)
                                .minimumScaleFactor(0.82)
                                .frame(width: tabWidth, height: innerHeight)
                                .contentShape(RoundedRectangle(cornerRadius: RootFeedDockMetrics.selectorCornerRadius, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .disabled(!isExpanded && !selected)
                        .opacity(selected ? 1 : (isExpanded ? 1 : 0))
                        .frame(width: tabWidth, height: innerHeight)
                        .offset(x: tabX)
                    }
                }
                .padding(RootFeedDockMetrics.innerPadding)
            }
            .clipShape(RoundedRectangle(cornerRadius: RootFeedDockMetrics.cornerRadius, style: .continuous))
        }
        .frame(width: isExpanded ? RootFeedDockMetrics.expandedWidth : RootFeedDockMetrics.collapsedWidth)
        .frame(height: RootFeedDockMetrics.height)
        .animation(animation, value: isExpanded)
        .animation(animation, value: activeTab)
        .contentShape(RoundedRectangle(cornerRadius: RootFeedDockMetrics.cornerRadius, style: .continuous))
        .onChange(of: collapsed) { _, newValue in
            if !newValue {
                tapExpanded = false
            }
        }
        .onChange(of: tapExpanded) { _, expanded in
            guard expanded else { return }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 2_400_000_000)
                if tapExpanded {
                    tapExpanded = false
                }
            }
        }
    }
}

private enum RootFeedDockMetrics {
    static let collapsedWidth: CGFloat = 112
    static let height: CGFloat = 52
    static let innerPadding: CGFloat = 5
    static let tabWidth: CGFloat = collapsedWidth - (innerPadding * 2)
    static let expandedWidth: CGFloat = (tabWidth * 2) + (innerPadding * 2)
    static let cornerRadius: CGFloat = 22
    static let selectorCornerRadius: CGFloat = 18
}
