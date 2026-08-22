import SwiftUI

/// Geometry for the root-feed hero header shared by the Scheduled and Floater
/// home screens.
///
/// The header is pinned above the feed: the toolbar strip (`barHeight`) — logo
/// mark, title, search, create-list and settings — stays put while the feed
/// scrolls out of sight behind it. `collapseProgress` (0 at the top of the
/// feed, 1 once the feed has scrolled `collapseDistance`) morphs the sun from a
/// large top-left mark down to the compact toolbar glyph while the title slides
/// from its centred hero position up beside it.
enum RootFeedHeroHeaderMetrics {
    static let horizontalPadding: CGFloat = 18
    static let topInset: CGFloat = 18
    static let barButtonSize: CGFloat = 56
    static let barButtonSpacing: CGFloat = 8
    static let compactIconSize: CGFloat = 30

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

    static let compactSunBox: CGFloat = 30
    static let heroSunBox: CGFloat = 72
    static let compactSunFontSize: CGFloat = 26
    static let heroSunFontSize: CGFloat = 62
    static let compactTitleFontSize: CGFloat = 32
    /// Hero title is drawn at the compact size and scaled, so its measured
    /// width stays valid for both ends of the morph.
    static let heroTitleScale: CGFloat = 1.25
    static let sunLeading: CGFloat = horizontalPadding + 2
    static let titleGap: CGFloat = 8
    static let compactRowCenterY: CGFloat = topInset + (barButtonSize / 2)
    static let heroSunCenterY: CGFloat = compactRowCenterY + 10
    static let heroTitleCenterY: CGFloat = barHeight + (heroTitleHeight / 2)

    static func collapseProgress(forScrollOffset offset: CGFloat) -> CGFloat {
        guard collapseDistance > 0 else { return 1 }
        return min(max(offset / collapseDistance, 0), 1)
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

    static func lerp(_ from: CGFloat, _ to: CGFloat, _ fraction: CGFloat) -> CGFloat {
        from + ((to - from) * fraction)
    }
}

/// Frame of the search capsule, reported in the owning screen's coordinate
/// space so it can anchor a results overlay directly beneath it.
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

struct RootFeedHeroHeader: View {
    let title: String
    /// 0 = hero layout, 1 = compact toolbar.
    let collapseProgress: CGFloat
    /// Coordinate space the search capsule frame is reported in.
    let coordinateSpaceName: String
    @Binding var searchExpanded: Bool
    @Binding var searchQuery: String
    var searchFieldFocused: FocusState<Bool>.Binding
    let onSearchClose: () -> Void
    let onCreateList: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.tdayColors) private var colors
    @State private var titleWidth: CGFloat = 0

    private typealias Metrics = RootFeedHeroHeaderMetrics

    private var progress: CGFloat {
        min(max(collapseProgress, 0), 1)
    }

    /// 1 while the feed is at the top, 0 once the header has folded away.
    private var expansion: CGFloat {
        1 - progress
    }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width

            ZStack(alignment: .topLeading) {
                // Opaque toolbar strip. It deliberately stays hit-testable so
                // rows hidden behind it can't be tapped through, and overscans
                // upward in case the feed bleeds under the status bar.
                colors.background
                    .frame(height: Metrics.barHeight + Metrics.backdropOverscan)
                    .offset(y: -Metrics.backdropOverscan)

                heroSun
                heroTitle(width: width)
                actionRow(width: width)
            }
            .frame(width: width, height: Metrics.expandedHeight, alignment: .topLeading)
        }
        .frame(height: Metrics.expandedHeight)
        .onPreferenceChange(RootFeedHeroTitleWidthKey.self) { width in
            titleWidth = width
        }
    }

    private var heroSun: some View {
        let box = Metrics.lerp(Metrics.compactSunBox, Metrics.heroSunBox, expansion)
        let fontSize = Metrics.lerp(Metrics.compactSunFontSize, Metrics.heroSunFontSize, expansion)
        let centerY = Metrics.lerp(Metrics.compactRowCenterY, Metrics.heroSunCenterY, expansion)

        return TimelineView(.periodic(from: .now, by: 60)) { context in
            Image(systemName: RootFeedHeroHeaderMetrics.sunSymbolName(for: context.date))
                .font(.system(size: fontSize, weight: .regular))
                .foregroundStyle(RootFeedHeroHeaderMetrics.sunColor(for: context.date))
                .frame(width: box, height: box)
        }
        .frame(width: box, height: box)
        .position(x: Metrics.sunLeading + (box / 2), y: centerY)
        .opacity(searchExpanded ? 0 : 1)
        .allowsHitTesting(false)
    }

    private func heroTitle(width: CGFloat) -> some View {
        let scale = Metrics.lerp(1, Metrics.heroTitleScale, expansion)
        let compactCenterX = Metrics.sunLeading
            + Metrics.compactSunBox
            + Metrics.titleGap
            + (titleWidth / 2)
        let centerX = Metrics.lerp(compactCenterX, width / 2, expansion)
        let centerY = Metrics.lerp(Metrics.compactRowCenterY, Metrics.heroTitleCenterY, expansion)

        return Text(title)
            .font(.tdayRounded(size: Metrics.compactTitleFontSize, weight: .heavy))
            .foregroundStyle(colors.onSurface)
            .lineLimit(1)
            .fixedSize()
            .background {
                GeometryReader { proxy in
                    Color.clear
                        .preference(key: RootFeedHeroTitleWidthKey.self, value: proxy.size.width)
                }
            }
            .scaleEffect(scale)
            .position(x: centerX, y: centerY)
            .opacity(searchExpanded ? 0 : 1)
            .allowsHitTesting(false)
    }

    private func actionRow(width: CGFloat) -> some View {
        let buttonSize = Metrics.barButtonSize
        let gap = Metrics.barButtonSpacing
        let rowWidth = max(buttonSize, width - (Metrics.horizontalPadding * 2))
        let actionCount: CGFloat = 2
        let collapsedSearchOffset = -((buttonSize * actionCount) + (gap * actionCount))

        return ZStack(alignment: .trailing) {
            HStack(spacing: gap) {
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
            .opacity(searchExpanded ? 0 : 1)
            .allowsHitTesting(!searchExpanded)

            searchCapsule
                .frame(width: searchExpanded ? rowWidth : buttonSize, height: buttonSize)
                .background(colors.surface, in: Capsule())
                .overlay(
                    Capsule()
                        .stroke(colors.onSurface.opacity(0.26), lineWidth: 1)
                )
                .offset(x: searchExpanded ? 0 : collapsedSearchOffset)
                .background(
                    GeometryReader { proxy in
                        Color.clear
                            .preference(
                                key: RootFeedSearchBarFrameKey.self,
                                value: proxy.frame(in: .named(coordinateSpaceName))
                            )
                    }
                )
                .zIndex(2)
                .animation(.spring(response: 0.28, dampingFraction: 0.86), value: searchExpanded)
        }
        .frame(width: rowWidth, height: buttonSize)
        .position(x: width / 2, y: Metrics.compactRowCenterY)
    }

    private var searchCapsule: some View {
        ZStack {
            Button {
                HapticManager.buttonTap()
                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                    searchExpanded = true
                }
            } label: {
                Image("NavSearch")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(colors.onSurface)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .buttonStyle(TdayToolbarButtonStyle())
            .opacity(searchExpanded ? 0 : 1)
            .allowsHitTesting(!searchExpanded)
            .accessibilityLabel("Search")

            HStack(spacing: 10) {
                Image("NavSearch")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                    .foregroundStyle(colors.onSurface)
                    .frame(width: Metrics.compactIconSize, height: Metrics.compactIconSize)

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
            .opacity(searchExpanded ? 1 : 0)
            .allowsHitTesting(searchExpanded)
        }
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
