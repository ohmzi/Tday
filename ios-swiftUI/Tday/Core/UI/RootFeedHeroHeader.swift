import Observation
import SwiftUI
import UIKit

/// Feed geometry the pinned root-feed header reacts to.
///
/// This is an `@Observable` box rather than plain `@State` on the screen on
/// purpose: the scroll offset changes every frame, and the screens that own the
/// header have very large bodies (the whole Scheduled board, the whole Floater
/// list). Keeping the offset here means a scroll frame invalidates only the
/// header, not the feed behind it.
@Observable
final class RootFeedHeaderScrollState {
    /// Feed scroll offset, clamped at 0. Drives the collapse morph.
    var offset: CGFloat = 0
    /// Pull-to-refresh pill state. The header draws the pill itself so it can
    /// ride in front of the title rather than behind the pinned toolbar.
    var refresh = TdayRefreshIndicatorState()
}

/// Geometry for the root-feed hero header shared by the Scheduled and Floater
/// home screens.
///
/// The header is pinned above the feed: the toolbar strip (`barHeight`) stays
/// put while the feed scrolls out of sight behind it. As the feed scrolls the
/// sun shrinks into the toolbar glyph, the title slides up from its centred
/// hero position to sit beside it, and the search field folds down into a round
/// button to make room for the title.
///
/// The three moves run on staggered curves, not one shared progress: the search
/// field and the sun clear out first, and the title only drops into the toolbar
/// row once it has finished travelling left. Sharing one curve makes the title
/// cut straight through the search field mid-scroll.
enum RootFeedHeroHeaderMetrics {
    static let horizontalPadding: CGFloat = 18
    static let topInset: CGFloat = 18
    static let barButtonSize: CGFloat = 56
    static let barButtonSpacing: CGFloat = 8
    static let searchIconSlot: CGFloat = 30
    static let searchLeadingPadding: CGFloat = 13

    /// Always-visible toolbar strip height, measured from the top safe area.
    static let barHeight: CGFloat = topInset + barButtonSize
    /// Extra height the hero title block claims while the feed sits at the top.
    static let heroTitleHeight: CGFloat = 78
    /// Total header height at rest — also the feed's top spacer height.
    static let expandedHeight: CGFloat = barHeight + heroTitleHeight
    /// Scroll distance over which the hero folds into the toolbar.
    static let collapseDistance: CGFloat = heroTitleHeight
    /// Overscan for the toolbar backdrop so it also covers the status bar.
    static let backdropOverscan: CGFloat = 200
    /// Gradient below the toolbar strip that dissolves rows as they pass under
    /// it, instead of the strip's edge guillotining them.
    static let contentFadeHeight: CGFloat = 24
    // Pull-to-refresh pill. It flies in from above the status bar and settles
    // hovering over the title — in front of it, not in place of it.
    static let refreshPillHiddenTop: CGFloat = -(TdayRefreshIndicatorMetrics.containerHeight + 28)
    static var refreshPillRestingTop: CGFloat {
        heroTitleCenterY - (TdayRefreshIndicatorMetrics.containerHeight / 2)
    }

    static let compactRowCenterY: CGFloat = topInset + (barButtonSize / 2)

    // Sun. Rendered once at the hero size and scaled *down*, so the glyph is
    // never resampled up and the morph is a GPU transform rather than a new
    // symbol rasterisation on every scroll frame.
    static let heroSunBox: CGFloat = 72
    static let compactSunBox: CGFloat = 30
    static let heroSunFontSize: CGFloat = 62
    static let sunLeading: CGFloat = horizontalPadding + 2
    static let heroSunCenterY: CGFloat = compactRowCenterY + 10

    // Title. Same trick: laid out at the hero size, scaled down to compact.
    static let heroTitleFontSize: CGFloat = 40
    static let maxCompactTitleScale: CGFloat = 0.8
    static let minTitleScale: CGFloat = 0.5
    static let heroTitleCenterY: CGFloat = barHeight + (heroTitleHeight / 2)
    static let titleGap: CGFloat = 8

    // Search field. Its trailing edge is fixed just inside the two round
    // buttons; only the leading edge travels, so it folds down into a button
    // in place instead of sliding across the toolbar.
    static let searchTrailingInset: CGFloat =
        horizontalPadding + (barButtonSize * 2) + (barButtonSpacing * 2)
    static let heroSearchLeading: CGFloat = sunLeading + heroSunBox + barButtonSpacing
    /// Capsule widths between which the "Search" placeholder fades in. The
    /// upper bound is where the whole word fits without the capsule's trailing
    /// cap clipping it.
    static let searchLabelFadeStart: CGFloat = 100
    static let searchLabelFadeEnd: CGFloat = 124

    // Staggered curve endpoints, as a fraction of `collapseDistance`. Solved by
    // search so that across every supported width and localised title the title
    // never crosses the sun, the search field or the buttons, and the rising
    // feed never clips it. Flatter easing widens the safe set — cubic admitted
    // 98 endpoint combinations, quintic 187, septic 255 — so each leg also runs
    // longer here than the cubic version could afford. The title's *vertical*
    // travel is deliberately not staggered at all: the feed rises 78pt while
    // the title only rises 67pt, so any delay there lets the first card cut
    // into the title's descenders.
    static let sunCollapseEnd: CGFloat = 0.70
    static let searchCollapseEnd: CGFloat = 0.50
    static let titleTravelEnd: CGFloat = 0.55

    static let searchMorph = Animation.spring(response: 0.30, dampingFraction: 0.86)

    static func collapseProgress(forScrollOffset offset: CGFloat) -> CGFloat {
        guard collapseDistance > 0 else { return 1 }
        return clamp(offset / collapseDistance)
    }

    static func clamp(_ value: CGFloat) -> CGFloat {
        min(max(value, 0), 1)
    }

    static func lerp(_ from: CGFloat, _ to: CGFloat, _ fraction: CGFloat) -> CGFloat {
        from + ((to - from) * fraction)
    }

    /// Fit-to-space caps for both ends of the title morph. A long localised
    /// title ("Fluttuante", "Запланировано") would otherwise sit under the sun
    /// while centred, and under the search button once docked beside it.
    static func titleScales(
        titleWidth: CGFloat,
        availableWidth: CGFloat
    ) -> (hero: CGFloat, compact: CGFloat) {
        guard titleWidth > 0 else { return (1, maxCompactTitleScale) }

        let heroRoom = availableWidth - (heroSearchLeading * 2)
        let hero = max(minTitleScale, min(1, heroRoom / titleWidth))

        let compactRoom = (availableWidth - searchTrailingInset - barButtonSize)
            - (sunLeading + compactSunBox + titleGap)
            - titleGap
        let compact = max(minTitleScale, min(maxCompactTitleScale * hero, compactRoom / titleWidth))

        return (hero, min(compact, hero))
    }

    /// Septic (7th-order) smootherstep over `[0, end]` of the raw collapse
    /// progress: `35t⁴ - 84t⁵ + 70t⁶ - 20t⁷`.
    ///
    /// Its derivative is `140t³(1-t)³`, so the first *three* derivatives are all
    /// zero at both ends — one order flatter than the usual quintic, which is
    /// itself two orders flatter than cubic smoothstep. That is what takes the
    /// sting out of the start and the stop: by 10% of the way through the title
    /// has moved 0.3% of its distance rather than quintic's 0.9%, and it is
    /// 99.7% settled at 90% rather than 99.1%. The peak is correspondingly
    /// quicker (2.19x mean speed against quintic's 1.88x), so the middle of the
    /// slide does not turn sluggish in exchange.
    static func stagger(_ progress: CGFloat, to end: CGFloat) -> CGFloat {
        guard end > 0 else { return progress > 0 ? 1 : 0 }
        let t = clamp(progress / end)
        return t * t * t * t * (35 + (t * (-84 + (t * (70 - (20 * t))))))
    }

    static func isDaytime(_ date: Date) -> Bool {
        (6..<18).contains(Calendar.current.component(.hour, from: date))
    }

    static func sunSymbolName(for date: Date) -> String {
        isDaytime(date) ? "sun.max.fill" : "moon.stars.fill"
    }

    static func sunColor(for date: Date) -> Color {
        isDaytime(date)
            ? Color(.sRGB, red: 244.0 / 255.0, green: 197.0 / 255.0, blue: 66.0 / 255.0, opacity: 1)
            : Color(.sRGB, red: 168.0 / 255.0, green: 184.0 / 255.0, blue: 232.0 / 255.0, opacity: 1)
    }
}

/// Frame of the search field, reported in the owning screen's coordinate space
/// so it can anchor a results overlay directly beneath it.
struct RootFeedSearchBarFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

private struct RootFeedHeroTitleWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// Which glyph the header leads with.
enum RootFeedHeroMark {
    /// Sun by day, moon by night — the Scheduled feed.
    case timeOfDay
    /// The Floater feed's leaf, drawn exactly as the dock's collapsed floater
    /// button draws it (mirrored, floater green) so the two read as one mark.
    case floaterLeaf
}

struct RootFeedHeroHeader: View {
    let title: String
    let mark: RootFeedHeroMark
    let scroll: RootFeedHeaderScrollState
    /// Coordinate space the search field frame is reported in.
    let coordinateSpaceName: String
    @Binding var searchExpanded: Bool
    @Binding var searchQuery: String
    var searchFieldFocused: FocusState<Bool>.Binding
    let onSearchClose: () -> Void
    let onCreateList: () -> Void
    let onOpenSettings: () -> Void
    /// Tapping the mark or the title returns the feed to the top, the way the
    /// iOS status bar does.
    let onScrollToTop: () -> Void

    @Environment(\.tdayColors) private var colors
    @State private var titleWidth: CGFloat = 0

    private typealias Metrics = RootFeedHeroHeaderMetrics

    var body: some View {
        // Read the observable geometry here, in the header's own body, so the
        // dependency is registered on this view and nothing above it.
        let progress = Metrics.collapseProgress(forScrollOffset: scroll.offset)
        let refresh = scroll.refresh

        GeometryReader { proxy in
            let width = proxy.size.width

            ZStack(alignment: .topLeading) {
                // Opaque toolbar strip. It deliberately stays hit-testable so
                // rows hidden behind it can't be tapped through, and overscans
                // upward in case the feed bleeds under the status bar.
                colors.background
                    .frame(height: Metrics.barHeight + Metrics.backdropOverscan)
                    .offset(y: -Metrics.backdropOverscan)

                LinearGradient(
                    colors: [colors.background, colors.background.opacity(0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: Metrics.contentFadeHeight)
                .offset(y: Metrics.barHeight)
                .allowsHitTesting(false)

                heroMark(progress: progress)
                heroTitle(width: width, progress: progress)
                trailingActions(width: width)
                searchField(width: width, progress: progress)
                refreshPill(width: width, refresh: refresh)
            }
            .frame(width: width, height: Metrics.expandedHeight, alignment: .topLeading)
        }
        .frame(height: Metrics.expandedHeight)
        .onPreferenceChange(RootFeedHeroTitleWidthKey.self) { width in
            titleWidth = width
        }
    }

    /// Drawn last so it hovers in front of the title, and positioned from the
    /// header's own origin so it flies down from above the status bar rather
    /// than appearing from behind the toolbar. The container's built-in
    /// indicator is switched off on these screens for exactly this reason — it
    /// lives inside the feed, which is painted underneath the header.
    private func refreshPill(width: CGFloat, refresh: TdayRefreshIndicatorState) -> some View {
        // TdayPullRefreshIndicator applies its own settle offset once the
        // refresh starts; cancel it so the pill lands on the title either way.
        let settle = refresh.isRefreshing ? TdayRefreshIndicatorMetrics.refreshingOffset : 0
        let top = Metrics.lerp(
            Metrics.refreshPillHiddenTop,
            Metrics.refreshPillRestingTop,
            Metrics.clamp(refresh.reveal)
        ) - settle

        return TdayPullRefreshIndicator(
            isRefreshing: refresh.isRefreshing,
            pullProgress: refresh.pullProgress
        )
        .position(
            x: width / 2,
            y: top + (TdayRefreshIndicatorMetrics.containerHeight / 2)
        )
        .allowsHitTesting(false)
    }

    private func heroMark(progress: CGFloat) -> some View {
        let collapse = Metrics.stagger(progress, to: Metrics.sunCollapseEnd)
        let box = Metrics.lerp(Metrics.heroSunBox, Metrics.compactSunBox, collapse)
        let centerY = Metrics.lerp(Metrics.heroSunCenterY, Metrics.compactRowCenterY, collapse)

        // Deliberately NOT hit-testable. The header is a sibling overlay above
        // the feed, not a descendant of it, so anything here that accepts a
        // touch is a dead zone for the scroll and pull-to-refresh pan — the
        // scroll view's recogniser never sees it. The title alone carries the
        // scroll-to-top tap; the mark's 72pt box would double that dead zone
        // over the corner people naturally drag from.
        return markGlyph
            .frame(width: Metrics.heroSunBox, height: Metrics.heroSunBox)
            // Rendered once at the hero size and scaled down: a GPU transform,
            // not a fresh symbol rasterisation on every scroll frame.
            .scaleEffect(box / Metrics.heroSunBox)
            .position(x: Metrics.sunLeading + (box / 2), y: centerY)
            .opacity(searchExpanded ? 0 : 1)
            .allowsHitTesting(false)
    }

    @ViewBuilder
    private var markGlyph: some View {
        switch mark {
        case .timeOfDay:
            TimelineView(.periodic(from: .now, by: 60)) { context in
                Image(systemName: RootFeedHeroHeaderMetrics.sunSymbolName(for: context.date))
                    .font(.system(size: Metrics.heroSunFontSize, weight: .regular))
                    .foregroundStyle(RootFeedHeroHeaderMetrics.sunColor(for: context.date))
            }
        case .floaterLeaf:
            Image(systemName: "leaf")
                .font(.system(size: Metrics.heroSunFontSize, weight: .semibold))
                .foregroundStyle(Color.tdayFloaterGreen)
                .scaleEffect(x: -1, y: 1)
        }
    }

    private func handleScrollToTop() {
        HapticManager.gentleTap()
        onScrollToTop()
    }

    private func heroTitle(width: CGFloat, progress: CGFloat) -> some View {
        let travel = Metrics.stagger(progress, to: Metrics.titleTravelEnd)
        let drop = Metrics.stagger(progress, to: 1)
        let fit = Metrics.titleScales(titleWidth: titleWidth, availableWidth: width)
        let scale = Metrics.lerp(fit.hero, fit.compact, travel)
        let compactCenterX = Metrics.sunLeading
            + Metrics.compactSunBox
            + Metrics.titleGap
            + ((titleWidth * fit.compact) / 2)
        let centerX = Metrics.lerp(width / 2, compactCenterX, travel)
        let centerY = Metrics.lerp(Metrics.heroTitleCenterY, Metrics.compactRowCenterY, drop)

        return Button(action: handleScrollToTop) {
            Text(title)
                .font(.tdayRounded(size: Metrics.heroTitleFontSize, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .fixedSize()
                .background {
                    GeometryReader { proxy in
                        Color.clear
                            .preference(key: RootFeedHeroTitleWidthKey.self, value: proxy.size.width)
                    }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .scaleEffect(scale)
        .position(x: centerX, y: centerY)
        .opacity(searchExpanded ? 0 : 1)
        .allowsHitTesting(!searchExpanded)
        .accessibilityLabel(Text(title))
        .accessibilityAddTraits(.isButton)
    }

    private func trailingActions(width: CGFloat) -> some View {
        let rowWidth = (Metrics.barButtonSize * 2) + Metrics.barButtonSpacing

        return HStack(spacing: Metrics.barButtonSpacing) {
            RootFeedHeaderCircleButton(icon: "NavListPlus") {
                HapticManager.buttonTap()
                onCreateList()
            }
            .accessibilityLabel("Create list")

            RootFeedHeaderCircleButton(icon: "NavEllipsis") {
                HapticManager.gentleTap()
                onOpenSettings()
            }
            .accessibilityLabel("More")
        }
        .frame(width: rowWidth, height: Metrics.barButtonSize)
        .position(
            x: width - Metrics.horizontalPadding - (rowWidth / 2),
            y: Metrics.compactRowCenterY
        )
        .opacity(searchExpanded ? 0 : 1)
        .allowsHitTesting(!searchExpanded)
    }

    private func searchField(width: CGFloat, progress: CGFloat) -> some View {
        let collapse = Metrics.stagger(progress, to: Metrics.searchCollapseEnd)
        let trailingX = width - Metrics.searchTrailingInset
        let heroWidth = max(Metrics.barButtonSize, trailingX - Metrics.heroSearchLeading)
        let restingWidth = Metrics.lerp(heroWidth, Metrics.barButtonSize, collapse)
        let fieldWidth = searchExpanded
            ? max(Metrics.barButtonSize, width - (Metrics.horizontalPadding * 2))
            : restingWidth
        let leadingX = searchExpanded ? Metrics.horizontalPadding : trailingX - restingWidth
        let labelOpacity = Metrics.clamp(
            (restingWidth - Metrics.searchLabelFadeStart)
                / max(1, Metrics.searchLabelFadeEnd - Metrics.searchLabelFadeStart)
        )

        // A clear base fixes the capsule's size and both states ride on top as
        // overlays. Putting them in a ZStack instead lets their intrinsic width
        // (the fixed icon slot plus a fixedSize label, ~100pt) become the
        // container's minimum, which at the 56pt collapsed width would centre
        // the oversized content and shove the magnifier off the capsule.
        return Color.clear
            .frame(width: fieldWidth, height: Metrics.barButtonSize)
            .overlay {
                searchRestingContent(labelOpacity: labelOpacity)
                    .opacity(searchExpanded ? 0 : 1)
                    .allowsHitTesting(!searchExpanded)
            }
            .overlay {
                searchActiveContent
                    .opacity(searchExpanded ? 1 : 0)
                    .allowsHitTesting(searchExpanded)
            }
            .clipShape(Capsule())
            .background(colors.surface, in: Capsule())
            .overlay(
                Capsule()
                    .stroke(colors.onSurface.opacity(0.26), lineWidth: 1)
            )
            // Only published while the field is open. The capsule's frame changes
            // on every scroll frame as it folds, and a preference that churns
            // would drag the owning screen's body into the scroll loop — the very
            // thing the observable scroll state exists to avoid.
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .preference(
                            key: RootFeedSearchBarFrameKey.self,
                            value: searchExpanded ? proxy.frame(in: .named(coordinateSpaceName)) : .zero
                        )
                }
            )
            .position(x: leadingX + (fieldWidth / 2), y: Metrics.compactRowCenterY)
            .animation(Metrics.searchMorph, value: searchExpanded)
    }

    private func searchRestingContent(labelOpacity: CGFloat) -> some View {
        Button {
            HapticManager.buttonTap()
            withAnimation(Metrics.searchMorph) {
                searchExpanded = true
            }
        } label: {
            Color.clear
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Leading overlay, not an HStack: the glyph must stay pinned at
                // searchLeadingPadding (which centres it once the capsule is a
                // 56pt button) while the placeholder simply runs off the end and
                // is clipped by the capsule.
                .overlay(alignment: .leading) {
                    HStack(spacing: 2) {
                        Image("NavSearch")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 22, height: 22)
                            .foregroundStyle(colors.onSurface)
                            .frame(width: Metrics.searchIconSlot, height: Metrics.barButtonSize)

                        Text("Search")
                            .font(.tdayRounded(size: 17, weight: .bold))
                            .foregroundStyle(colors.onSurfaceVariant)
                            .lineLimit(1)
                            .fixedSize()
                            .opacity(Double(labelOpacity))
                    }
                    .padding(.leading, Metrics.searchLeadingPadding)
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(TdayToolbarButtonStyle(shadowsEnabled: false))
        .accessibilityLabel("Search")
    }

    private var searchActiveContent: some View {
        HStack(spacing: 10) {  // sized by the capsule it overlays, never the reverse
            Image("NavSearch")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 20, height: 20)
                .foregroundStyle(colors.onSurface)
                .frame(width: Metrics.searchIconSlot, height: Metrics.searchIconSlot)

            TextField("", text: $searchQuery, prompt: Text("Search").foregroundStyle(colors.onSurfaceVariant))
                .focused(searchFieldFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.tdayRounded(size: 18, weight: .bold))
                .foregroundStyle(colors.onSurface)
                .tint(colors.primary)
                .disabled(!searchExpanded)

            Button {
                HapticManager.sheetDismiss()
                onSearchClose()
            } label: {
                Image("NavClose")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                    .foregroundStyle(colors.onSurfaceVariant.opacity(0.78))
            }
            .buttonStyle(
                TdayPressButtonStyle(
                    shadowColor: Color.black,
                    pressedShadowOpacity: 0,
                    normalShadowOpacity: 0
                )
            )
            .accessibilityLabel("Cancel search")
        }
        .padding(.horizontal, 14)
    }
}

private struct RootFeedHeaderCircleButton: View {
    /// Asset-catalog name of the lucide template glyph (shared with web/Android).
    let icon: String
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(icon)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 22, height: 22)
                .foregroundStyle(colors.onSurface)
                .frame(
                    width: RootFeedHeroHeaderMetrics.barButtonSize,
                    height: RootFeedHeroHeaderMetrics.barButtonSize
                )
                .background(colors.surface)
                .clipShape(Circle())
                .overlay {
                    Circle()
                        .stroke(colors.onSurface.opacity(0.34), lineWidth: 1)
                }
        }
        .buttonStyle(TdayToolbarButtonStyle())
    }
}

/// Publishes the feed's scroll offset into `RootFeedHeaderScrollState`, and
/// calls back only when the root dock's collapse threshold is crossed — the
/// per-frame offset must not touch the owning screen's `@State`.
struct RootFeedHeaderScrollObserver: UIViewRepresentable {
    let state: RootFeedHeaderScrollState
    let collapseThreshold: CGFloat
    let onCollapsedChange: (Bool) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(state: state, collapseThreshold: collapseThreshold, onCollapsedChange: onCollapsedChange)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.collapseThreshold = collapseThreshold
        context.coordinator.onCollapsedChange = onCollapsedChange
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator {
        let state: RootFeedHeaderScrollState
        var collapseThreshold: CGFloat
        var onCollapsedChange: (Bool) -> Void

        private weak var observedScrollView: UIScrollView?
        private var observation: NSKeyValueObservation?
        private var lastCollapsed: Bool?

        init(
            state: RootFeedHeaderScrollState,
            collapseThreshold: CGFloat,
            onCollapsedChange: @escaping (Bool) -> Void
        ) {
            self.state = state
            self.collapseThreshold = collapseThreshold
            self.onCollapsedChange = onCollapsedChange
        }

        func attach(to view: UIView) {
            guard let scrollView = view.rootFeedEnclosingScrollView() else {
                return
            }
            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView = scrollView
            observation = scrollView.observe(\.contentOffset, options: [.initial, .new]) { [weak self] scrollView, _ in
                let offset = max(scrollView.contentOffset.y + scrollView.adjustedContentInset.top, 0)
                if Thread.isMainThread {
                    self?.publish(offset)
                } else {
                    DispatchQueue.main.async {
                        self?.publish(offset)
                    }
                }
            }
        }

        private func publish(_ offset: CGFloat) {
            if abs(state.offset - offset) > 0.01 {
                state.offset = offset
            }

            let collapsed = offset > collapseThreshold
            guard lastCollapsed != collapsed else { return }
            lastCollapsed = collapsed
            onCollapsedChange(collapsed)
        }
    }
}

private extension UIView {
    func rootFeedEnclosingScrollView() -> UIScrollView? {
        var current: UIView? = self
        while let view = current {
            if let scrollView = view as? UIScrollView {
                return scrollView
            }
            current = view.superview
        }
        return nil
    }
}
