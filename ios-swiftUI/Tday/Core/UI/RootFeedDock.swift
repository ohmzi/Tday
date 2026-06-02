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
            return "circle.dotted"
        }
    }
}

struct RootFeedDock: View {
    let activeTab: RootFeedTab
    let collapsed: Bool
    let accentColor: Color
    let onSelect: (RootFeedTab) -> Void

    private let tabs: [RootFeedTab] = [.home, .floater]

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
        TdayNativeSegmentedControl(
            labels: tabs.map(\.title),
            selectedIndex: activeIndex,
            accentColor: accentColor,
            onSelect: { index in
                guard tabs.indices.contains(index) else {
                    return
                }
                onSelect(tabs[index])
            }
        )
        .frame(width: RootFeedDockMetrics.width)
        .frame(height: TdayNativeSegmentedControlMetrics.height)
    }
}

private enum RootFeedDockMetrics {
    static let width: CGFloat = 212
}
