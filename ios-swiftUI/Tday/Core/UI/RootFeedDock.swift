import SwiftUI

enum RootFeedTab: Hashable {
    case scheduledTaskHome
    case floaterTaskHome

    var title: String {
        switch self {
        case .scheduledTaskHome:
            return L("Scheduled")
        case .floaterTaskHome:
            return L("Floater")
        }
    }

    var systemImage: String {
        switch self {
        case .scheduledTaskHome:
            return "house.fill"
        case .floaterTaskHome:
            return "leaf"
        }
    }

    /// The API/cache-persisted value for the "Default home screen" preference.
    var defaultHomeScreenApiValue: String {
        switch self {
        case .scheduledTaskHome:
            return "scheduled"
        case .floaterTaskHome:
            return "floater"
        }
    }
}

/// Inverse of `RootFeedTab.defaultHomeScreenApiValue`; unrecognized/absent values fall back to
/// Scheduled.
func rootFeedTabFromDefaultHomeScreenApiValue(_ value: String?) -> RootFeedTab {
    value == "floater" ? .floaterTaskHome : .scheduledTaskHome
}

struct RootFeedDock: View {
    let activeTab: RootFeedTab
    let collapsed: Bool
    let accentColor: Color
    let onSelect: (RootFeedTab) -> Void

    private let tabs: [RootFeedTab] = [.scheduledTaskHome, .floaterTaskHome]
    @Environment(\.tdayColors) private var colors
    @Environment(\.tdayAnimation) private var tdayAnimation
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
    // Tap-to-expand keeps Scheduled/Floater immediately pressable instead of relying on a
    // scroll-to-top to bring the dock back, which left it stuck as an icon if the feed didn't
    // scroll far enough. Mirrors the Android dock's `expandedByTap`.
    private var isExpanded: Bool {
        !collapsed || expandedByTap
    }

    /// How the collapsed pill and the expanded control replace each other.
    ///
    /// The scale is anchored `.leading` because the dock grows rightwards out of the
    /// icon rather than out of its own centre; 0.82 is an eighteen-percent size change,
    /// which is well past the amplitude a crossfade is a substitute FOR rather than a
    /// substitute for nothing. So Reduce Motion keeps the opacity and drops the scale:
    /// the two controls cross over in place, at the size each of them is.
    ///
    /// Built once and handed to both branches, because an asymmetry here would be a
    /// typo rather than a decision — the expanded control appearing is the collapsed
    /// one disappearing, and they are the same event seen from two `if` arms.
    private var swapTransition: AnyTransition {
        tdayAnimation.transition(
            .scale(scale: 0.82, anchor: .leading).combined(with: .opacity),
            reduced: .opacity
        )
    }

    var body: some View {
        ZStack {
            if isExpanded {
                expandedControl
                    .transition(swapTransition)
            } else {
                collapsedButton
                    .transition(swapTransition)
            }
        }
        // 0.34 / 0.82 was the Gesture spring written out — the same numbers, and the
        // same intent: a control continuing under its own momentum after a finger has
        // let go, which is what expand-on-tap and collapse-on-scroll both are. Naming
        // the token is not a retiming; what it buys is the gate beside it.
        //
        // Under Reduce Motion the spring is the wrong shape whatever its length, so the
        // substitute is a plain crossfade on Quick — the rung for the app answering a
        // finger that is on it, which this still is. It is not `nil`: the two halves of
        // this ZStack are DIFFERENT views at different widths, so a cut would replace a
        // 56 pt pill with a full segmented control between two frames, in the corner of
        // the screen the user is least likely to be looking at.
        .animation(
            tdayAnimation(
                TdayMotion.gesture,
                reduced: TdayMotion.standard(duration: TdayMotion.Durations.quick)
            ),
            value: isExpanded
        )
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
    // Scheduled/Floater control becomes immediately pressable.
    private var collapsedButton: some View {
        Button {
            expandedByTap = true
        } label: {
            Group {
                if activeTab == .scheduledTaskHome {
                    Image("NavHouse")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22, height: 22)
                } else {
                    Image(systemName: activeTab.systemImage)
                        .font(.system(size: 22, weight: .semibold))
                        .scaleEffect(x: activeTab == .floaterTaskHome ? -1 : 1, y: 1)
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
