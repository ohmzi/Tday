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

    /// The SF Symbol the collapsed pill draws — only for the tabs that have one.
    ///
    /// Scheduled returns nil because it wears the bundled `calendar-check` glyph
    /// instead, which is what `docs/ICONS.md` asks of a shared product surface; the
    /// pill must never quietly fall back to a symbol for it. Returning nil rather
    /// than a name is the point — a caller that ignores the optional gets nothing
    /// drawn rather than the old house.
    ///
    /// Floater still returns `"leaf"`, and that is a KNOWN GAP rather than an
    /// endorsement: `LucideLeaf` is vendored and the pill already mirrors whatever
    /// it draws, so the swap is one line — it is left out of this change because it
    /// redraws a tab nobody reported, and this comment is not allowed to claim a
    /// rule the branch below it does not follow.
    var systemImage: String? {
        switch self {
        case .scheduledTaskHome:
            return nil
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
                    Image("LucideCalendarCheck")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22, height: 22)
                } else if let systemImage = activeTab.systemImage {
                    Image(systemName: systemImage)
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

/// Where a root feed's dock folds down to its pill, and where it opens back up again.
///
/// Two thresholds and not one. A single comparison flips on its own boundary point, so a
/// feed resting exactly at `collapseThreshold` — which is where a feed near the top of its
/// content ends up, a scroll view settling a point either way out of its own deceleration,
/// or a finger parked there — strobes the dock between `RootFeedDockMetrics.collapsedWidth`
/// and `RootFeedDockMetrics.width` for as long as it rests. The 20 points between the two
/// numbers below are the dead band that swallows that hover. It costs a deliberate scroll
/// back to the top nothing: such a scroll passes both edges inside one gesture.
///
/// `collapseThreshold` is not ours alone. Android declares the same 44 at
/// `RootFeedDockCollapse.CollapseThreshold` in
/// `android-compose/app/src/main/java/com/ohmz/tday/compose/ui/component/RootFeedDock.kt`,
/// and web at `ROOT_DOCK_COLLAPSE_PX` in `tday-web/src/lib/rootDockCollapse.ts`. Moving it
/// here moves one client of three, and a dock that folds at three different distances is
/// three docks. It is declared once per client for the same reason: the two root feeds each
/// carried a copy of the literal, which is how the number was one fold point on paper and two
/// the moment anybody touched one of them.
enum RootFeedDockCollapse {

    /// How far a feed has to travel before its dock gives up its labels.
    static let collapseThreshold: CGFloat = 44

    /// How far back up it has to come before the dock gets them back.
    static let expandThreshold: CGFloat = 24

    /// The dock's next folded state, given the one it is already in.
    ///
    /// `previous` is what makes the dead band a dead band rather than a second threshold
    /// nobody reaches: it picks which edge is being tested. Ask this without it — with a
    /// standalone comparison, the way both feeds used to — and the band has no effect at
    /// all, because the answer at any offset is then the same whichever side the dock
    /// arrived from.
    ///
    /// Android's object takes the feed's first visible index as well, since a lazy list
    /// reports the offset within that item rather than the distance travelled and a long
    /// scroll would otherwise read as a short one. A `UIScrollView`'s content offset is the
    /// travel itself, so there is nothing here to correct for.
    ///
    /// The clamp is for the call sites rather than for the arithmetic: both edges are
    /// positive, so a rubber-banded offset above the top loses either comparison with or
    /// without it. It is here so that the callers that were each spelling `max(offset, 0)`
    /// in front of their own comparison have one less thing to keep in step.
    static func next(previous: Bool, offset: CGFloat) -> Bool {
        let travelled = max(offset, 0)
        return previous ? travelled > expandThreshold : travelled > collapseThreshold
    }
}
