import SwiftUI
import UIKit

struct TdayColors {
    let isDark: Bool
    let background: Color
    let surface: Color
    let surfaceVariant: Color
    let primary: Color
    let secondary: Color
    let tertiary: Color
    let error: Color
    let onPrimary: Color
    let onSurface: Color
    let onSurfaceVariant: Color

    static let light = TdayColors(
        isDark: false,
        background: .tdayLightBackground,
        surface: .tdayLightSurface,
        surfaceVariant: .tdayLightSurfaceVariant,
        primary: .tdayLightAccent,
        secondary: .tdayLightSecondary,
        tertiary: .tdayLightWarm,
        error: .tdayLightError,
        onPrimary: .tdayLightOnPrimary,
        onSurface: .tdayLightForeground,
        onSurfaceVariant: .tdayLightMuted
    )

    static let dark = TdayColors(
        isDark: true,
        background: .tdayDarkBackground,
        surface: .tdayDarkSurface,
        surfaceVariant: .tdayDarkSurfaceVariant,
        primary: .tdayDarkAccent,
        secondary: .tdayDarkSecondary,
        tertiary: .tdayDarkWarm,
        error: .tdayDarkError,
        onPrimary: .tdayDarkOnPrimary,
        onSurface: .tdayDarkForeground,
        onSurfaceVariant: .tdayDarkMuted
    )

    static let `default` = TdayColors.light

    static func palette(for colorScheme: ColorScheme) -> TdayColors {
        colorScheme == .dark ? .dark : .light
    }

    var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [background, background, background],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    var cardStroke: Color {
        isDark ? Color.white.opacity(0.10) : Color.white.opacity(0.45)
    }

    var bottomSheetBackground: Color {
        isDark ? background.tdayBlended(with: surfaceVariant, amount: 0.34) : background
    }

    var bottomSheetSurface: Color {
        isDark ? surface.tdayBlended(with: surfaceVariant, amount: 0.18) : surface
    }

    var bottomSheetControlSurface: Color {
        surfaceVariant
    }

    var bottomSheetScrim: Color {
        Color.black.opacity(isDark ? 0.68 : 0.40)
    }
}

private struct TdayColorsKey: EnvironmentKey {
    static let defaultValue = TdayColors.default
}

extension EnvironmentValues {
    var tdayColors: TdayColors {
        get { self[TdayColorsKey.self] }
        set { self[TdayColorsKey.self] = newValue }
    }
}

private extension Color {
    func tdayBlended(with other: Color, amount: CGFloat) -> Color {
        let lhs = UIColor(self)
        let rhs = UIColor(other)
        var lhsRed: CGFloat = 0
        var lhsGreen: CGFloat = 0
        var lhsBlue: CGFloat = 0
        var lhsAlpha: CGFloat = 0
        var rhsRed: CGFloat = 0
        var rhsGreen: CGFloat = 0
        var rhsBlue: CGFloat = 0
        var rhsAlpha: CGFloat = 0

        lhs.getRed(&lhsRed, green: &lhsGreen, blue: &lhsBlue, alpha: &lhsAlpha)
        rhs.getRed(&rhsRed, green: &rhsGreen, blue: &rhsBlue, alpha: &rhsAlpha)

        let mix = Swift.min(Swift.max(amount, 0), 1)
        return Color(
            uiColor: UIColor(
                red: lhsRed + ((rhsRed - lhsRed) * mix),
                green: lhsGreen + ((rhsGreen - lhsGreen) * mix),
                blue: lhsBlue + ((rhsBlue - lhsBlue) * mix),
                alpha: lhsAlpha + ((rhsAlpha - lhsAlpha) * mix)
            )
        )
    }
}

/// The corner radii this UI draws with, named once.
///
/// The rung is the same one the other two clients draw — Android's
/// `TdayDimens.RadiusCard` (26.dp), which its own wizard's tiles wear too, and
/// web's `rounded-[26px]`, written out at each of its tile sites rather than named
/// and the same value on its board's tiles and its wizard's alike — so the three
/// clients draw one corner from one number. This client is the only one where the
/// rung and the drawing sites have been made to agree in one place; web's is a
/// value its sites share by hand, which is the drift this enum exists to stop
/// here. It lives here rather than at a
/// call site because a corner more than one shape has to agree on is a token: the
/// home board's tiles, the Today card, the scheduled board's list rows, the
/// Anytime feed's two cards, the onboarding wizard's two tiles, the auth flow's
/// hero tile and the shape `ZoomNavigation` grows a pushed screen out of are all
/// this corner, and before this enum six sites across four files spelled it
/// themselves with nothing binding them together.
enum TdayRadius {
    /// The card a section is drawn on — the tiles that group rows, and the hero tiles
    /// the wizard and the reset flow open with, never the rows themselves.
    static let card: CGFloat = 26
}

enum TdayFont {
    static func font(size: CGFloat, weight: Font.Weight) -> Font {
        .custom(postScriptName(for: weight), size: size)
    }

    static func font(_ textStyle: Font.TextStyle, weight: Font.Weight) -> Font {
        .custom(postScriptName(for: weight), size: pointSize(for: textStyle), relativeTo: textStyle)
    }

    static func uiFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
        UIFont(name: postScriptName(for: weight), size: size)
            ?? UIFont.systemFont(ofSize: size, weight: weight)
    }

    static func applyGlobalAppearances() {
        let defaultFont = uiFont(size: 17, weight: .bold)
        UILabel.appearance().font = defaultFont
        UITextField.appearance().font = defaultFont
        UITextView.appearance().font = defaultFont

        UINavigationBar.appearance().titleTextAttributes = [
            .font: uiFont(size: 17, weight: .bold)
        ]
        UINavigationBar.appearance().largeTitleTextAttributes = [
            .font: uiFont(size: 32, weight: .heavy)
        ]
        UIBarButtonItem.appearance().setTitleTextAttributes([
            .font: uiFont(size: 17, weight: .bold)
        ], for: .normal)
        UIBarButtonItem.appearance().setTitleTextAttributes([
            .font: uiFont(size: 17, weight: .bold)
        ], for: .highlighted)
        UISegmentedControl.appearance().setTitleTextAttributes([
            .font: uiFont(size: 13, weight: .bold)
        ], for: .normal)
        UISegmentedControl.appearance().setTitleTextAttributes([
            .font: uiFont(size: 13, weight: .bold)
        ], for: .selected)
    }

    private static func pointSize(for textStyle: Font.TextStyle) -> CGFloat {
        switch textStyle {
        case .largeTitle:
            return 34
        case .title:
            return 28
        case .title2:
            return 22
        case .title3:
            return 20
        case .headline, .body:
            return 17
        case .callout:
            return 16
        case .subheadline:
            return 15
        case .footnote:
            return 13
        case .caption:
            return 12
        case .caption2:
            return 11
        @unknown default:
            return 17
        }
    }

    private static func postScriptName(for weight: Font.Weight) -> String {
        switch weight {
        case .black:
            return "Nunito-Black"
        case .heavy, .bold:
            return "Nunito-ExtraBold"
        case .semibold:
            return "Nunito-Bold"
        case .medium:
            return "Nunito-SemiBold"
        default:
            return "Nunito-Bold"
        }
    }

    private static func postScriptName(for weight: UIFont.Weight) -> String {
        if weight.rawValue >= UIFont.Weight.black.rawValue {
            return "Nunito-Black"
        }
        if weight.rawValue >= UIFont.Weight.heavy.rawValue {
            return "Nunito-ExtraBold"
        }
        if weight.rawValue >= UIFont.Weight.bold.rawValue {
            return "Nunito-ExtraBold"
        }
        if weight.rawValue >= UIFont.Weight.semibold.rawValue {
            return "Nunito-Bold"
        }
        if weight.rawValue >= UIFont.Weight.medium.rawValue {
            return "Nunito-SemiBold"
        }
        return "Nunito-Bold"
    }
}

extension Font {
    static func tdayRounded(size: CGFloat, weight: Font.Weight = .bold) -> Font {
        TdayFont.font(size: size, weight: weight)
    }

    static func tdayRounded(_ textStyle: Font.TextStyle, weight: Font.Weight = .bold) -> Font {
        TdayFont.font(textStyle, weight: weight)
    }
}

struct TdayBackground<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        ZStack {
            colors.backgroundGradient
                .ignoresSafeArea()
            content
        }
    }
}

// `tdayLucideListAsset` and its iconKey→Lucide table moved to `TdayListAccent.swift` — not
// for tidiness, but because the TdayWidget extension has to resolve a list's glyph too, and
// this file is unshareable with it (SwiftUI environments, a UIViewRepresentable, global
// UIKit appearance proxies). The function below is the same one, not a copy.

/// Renders a list icon as a tintable template image (replaces SF Symbol list icons).
///
/// `listName` is how a list that was never given a glyph gets one anyway: pass it and the
/// icon falls back to `tdayInferredListIconKey` before it falls back to the inbox. Pass it
/// at every site that is drawing a LIST — a row's trailing mark, a list card, the header —
/// and omit it at sites drawing a picker OPTION, where the key under the finger is the whole
/// subject and there is no list yet to have a name.
///
/// Defaulted to nil rather than made required so that the omission is the safe direction:
/// forgetting it costs one list its guess, whereas a required parameter tempts the next
/// caller to hand over whatever string is nearest.
struct TdayListIcon: View {
    let iconKey: String?
    var listName: String? = nil
    var size: CGFloat = 24

    var body: some View {
        Image(tdayLucideListAsset(tdayResolvedListIconKey(iconKey, listName: listName)))
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
    }
}

struct EmptyTaskWatermark: View {
    let systemName: String
    let accentColor: Color
    /// Optional asset-catalog name of a lucide template glyph; when set it is used
    /// instead of the SF Symbol so screens match the web tile icons.
    var assetName: String? = nil
    /// The watermark's mark as a drawing rather than a glyph, for a screen whose
    /// mark is more than one glyph — the Completion history's is three stacked.
    /// Drawn exactly where and at exactly the size `assetName` would have been,
    /// so the two paths cannot drift.
    ///
    /// A composite leaves its own layers untinted and is drawn in the
    /// environment's foreground style, which is the `watermarkColor` below. The
    /// alternative is a call site re-deriving `onSurfaceVariant` blended 36%
    /// toward the accent in order to say what colour it is standing in.
    var markContent: AnyView? = nil

    @Environment(\.tdayColors) private var colors
    /// The box the watermark's glyph is drawn in — 194pt, the native twin of
    /// Android's `WatermarkGlyphSize` (212dp). Public because a caller handing in
    /// a drawing through `markContent` has to draw it in this box: the slot
    /// centres whatever it is given, so a mark built at any other size lands at
    /// the wrong scale inside the same rotation and offset.
    static let markGlyphSize: CGFloat = 194
    private var iconSize: CGFloat { Self.markGlyphSize }
    private let trailingOffset: CGFloat = 26

    private var watermarkColor: Color {
        colors.onSurfaceVariant.tdayBlended(with: accentColor, amount: 0.36).opacity(0.10)
    }

    @ViewBuilder
    private var watermarkContent: some View {
        if let markContent {
            markContent
        } else {
            watermarkImage
        }
    }

    @ViewBuilder
    private var watermarkImage: some View {
        if let assetName {
            Image(assetName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
        } else {
            Image(systemName: systemName)
                .font(.system(size: iconSize, weight: .regular))
                .scaleEffect(x: systemName == "leaf" ? -1 : 1, y: 1)
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let screenBounds = UIScreen.main.bounds
            let frame = proxy.frame(in: .global)
            let targetX = screenBounds.width - (iconSize / 2) + trailingOffset - frame.minX
            let targetY = (screenBounds.height * (2.0 / 3.0)) - frame.minY

            watermarkContent
                .foregroundStyle(watermarkColor)
                .rotationEffect(.degrees(-7))
                .frame(width: iconSize, height: iconSize)
                .position(
                    x: targetX,
                    y: targetY
                )
        }
        .ignoresSafeArea(.keyboard)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}



struct TdayCardModifier: ViewModifier {
    @Environment(\.tdayColors) private var colors

    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(colors.cardStroke, lineWidth: 1)
            )
    }
}

private struct TdayAppThemeModifier: ViewModifier {
    let themeMode: AppThemeMode

    /// The in-app Reduce Motion preference, passed down beside the theme mode and for the same
    /// reason: both are root decisions a `UIViewRepresentable`'s separate window cannot inherit.
    let reduceMotion: Bool

    @Environment(\.colorScheme) private var systemColorScheme

    private var resolvedColorScheme: ColorScheme {
        themeMode.colorScheme ?? systemColorScheme
    }

    private var colors: TdayColors {
        TdayColors.palette(for: resolvedColorScheme)
    }

    func body(content: Content) -> some View {
        content
            .environment(\.tdayColors, colors)
            // The motion gate rides with the palette because it answers the same shape of
            // question — one root decision every surface below reads — and because this
            // modifier is already the one wrapper both window roots go through.
            .tdayResolvedMotion(reduceMotion: reduceMotion)
            .tint(colors.primary)
            .background(colors.backgroundGradient.ignoresSafeArea())
            .preferredColorScheme(themeMode.colorScheme)
    }
}

extension View {
    func tdayCard() -> some View {
        modifier(TdayCardModifier())
    }

    func tdayAppTheme(themeMode: AppThemeMode, reduceMotion: Bool) -> some View {
        modifier(TdayAppThemeModifier(themeMode: themeMode, reduceMotion: reduceMotion))
    }

    func tdayAppTypography() -> some View {
        font(.tdayRounded(.body, weight: .bold))
    }
}

enum TdayNativeSegmentedControlMetrics {
    static let height: CGFloat = 52

    /// How far the selected capsule sits inside the track, below iOS 26.
    ///
    /// Matched by eye against iOS 26's own drawing of the same control rather than derived: the
    /// system publishes no metric for it, and the only honest source is the thing being matched.
    /// Not a motion token — it is a static inset, not a duration, curve or spring.
    static let selectionInset: CGFloat = 2
}

struct TdayNativeSegmentedControl: UIViewRepresentable {
    let labels: [String]
    let selectedIndex: Int
    let accentColor: Color
    var controlHeight = TdayNativeSegmentedControlMetrics.height
    var fontSize: CGFloat = 13
    let onSelect: (Int) -> Void

    @Environment(\.tdayColors) private var colors

    func makeCoordinator() -> Coordinator {
        Coordinator(onSelect: onSelect)
    }

    func makeUIView(context: Context) -> ThickSegmentedControl {
        let control = ThickSegmentedControl(items: labels)
        control.selectedSegmentIndex = boundedSelectedIndex
        control.apportionsSegmentWidthsByContent = false
        control.addTarget(context.coordinator, action: #selector(Coordinator.didChange(_:)), for: .valueChanged)
        applySizingAndTint(to: control)
        return control
    }

    func updateUIView(_ control: ThickSegmentedControl, context: Context) {
        context.coordinator.onSelect = onSelect
        updateLabels(on: control)
        if control.selectedSegmentIndex != boundedSelectedIndex {
            control.selectedSegmentIndex = boundedSelectedIndex
        }
        applySizingAndTint(to: control)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: ThickSegmentedControl, context: Context) -> CGSize? {
        CGSize(
            width: proposal.width ?? uiView.intrinsicContentSize.width,
            height: controlHeight
        )
    }

    private var boundedSelectedIndex: Int {
        guard labels.indices.contains(selectedIndex) else {
            return UISegmentedControl.noSegment
        }
        return selectedIndex
    }

    private func updateLabels(on control: ThickSegmentedControl) {
        guard control.numberOfSegments == labels.count else {
            control.removeAllSegments()
            for (index, label) in labels.enumerated() {
                control.insertSegment(withTitle: label, at: index, animated: false)
            }
            return
        }

        for (index, label) in labels.enumerated() where control.titleForSegment(at: index) != label {
            control.setTitle(label, forSegmentAt: index)
        }
    }

    private func applySizingAndTint(to control: ThickSegmentedControl) {
        control.preferredHeight = controlHeight
        control.overrideUserInterfaceStyle = colors.isDark ? .dark : .light
        control.backgroundColor = UIColor(colors.surfaceVariant.opacity(0.76))
        control.selectedSegmentTintColor = UIColor(colors.surface)
        control.tintColor = UIColor(accentColor)
        applyCapsuleSelectionIfNeeded(to: control)
        control.setTitleTextAttributes(
            [
                .foregroundColor: UIColor(colors.onSurfaceVariant),
                .font: TdayFont.uiFont(size: fontSize, weight: .bold)
            ],
            for: .normal
        )
        control.setTitleTextAttributes(
            [
                .foregroundColor: UIColor(accentColor),
                .font: TdayFont.uiFont(size: fontSize, weight: .bold)
            ],
            for: .selected
        )
        control.invalidateIntrinsicContentSize()
    }

    /// Draws the selected segment as a CAPSULE on the systems that do not draw one themselves.
    ///
    /// Nothing in this file styles the segment's corner — `UISegmentedControl` owns it, and it
    /// changed its mind: iOS 26 draws the selected segment as a full capsule inset from the track,
    /// while iOS 18 and earlier draw a modest-radius rounded rectangle sitting nearly flush. Same
    /// code, same colours, same height; only the radius differs, and that single radius is why the
    /// older systems read blockier than the design they are meant to share.
    ///
    /// So the radius stops being the system's to choose below iOS 26. A background image per state
    /// is the public lever for that — `setBackgroundImage(_:for:barMetrics:)` has existed since
    /// iOS 5 — rather than reaching into the control's private subviews to round whichever one
    /// looks selected, which is undocumented and would fail silently the first time Apple renames a
    /// layer. The images are generated from the same `colors` the modern path uses, so light and
    /// dark need no second answer.
    ///
    /// iOS 26 and later return immediately and keep the system's own drawing: matching it is the
    /// whole point, and re-implementing it there would mean maintaining a copy that drifts.
    private func applyCapsuleSelectionIfNeeded(to control: ThickSegmentedControl) {
        if #available(iOS 26.0, *) {
            return
        }

        let track = Self.capsuleImage(
            color: UIColor(colors.surfaceVariant.opacity(0.76)),
            height: controlHeight,
            inset: 0
        )
        // The selected capsule is inset so the track reads as a groove around it, which is what
        // iOS 26 draws and what the flush rectangle below it never did.
        let selected = Self.capsuleImage(
            color: UIColor(colors.surface),
            height: controlHeight,
            inset: TdayNativeSegmentedControlMetrics.selectionInset
        )

        control.setBackgroundImage(track, for: .normal, barMetrics: .default)
        control.setBackgroundImage(selected, for: .selected, barMetrics: .default)
        control.setBackgroundImage(selected, for: [.selected, .highlighted], barMetrics: .default)
        // The 1pt separators belong to the rectangular look; a capsule that floats in a groove has
        // nothing to be separated from. An empty image rather than nil: nil restores the default.
        for left in [UIControl.State.normal, .selected] {
            for right in [UIControl.State.normal, .selected] {
                control.setDividerImage(
                    UIImage(),
                    forLeftSegmentState: left,
                    rightSegmentState: right,
                    barMetrics: .default
                )
            }
        }
        // The track image carries the fill now; leaving the colour on as well double-draws it at
        // the corners, where the image is transparent and the view's own layer is not.
        control.backgroundColor = .clear
    }

    /// A horizontally stretchable capsule: two round caps and a 1pt middle that resizes.
    ///
    /// Drawn at `height` so the radius is exactly half the control's height — the definition of a
    /// capsule, and the thing the older systems get wrong.
    private static func capsuleImage(color: UIColor, height: CGFloat, inset: CGFloat) -> UIImage {
        let radius = max(0, (height - inset * 2) / 2)
        let cap = radius + inset
        let size = CGSize(width: cap * 2 + 1, height: height)
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: inset, dy: inset)
            color.setFill()
            UIBezierPath(roundedRect: rect, cornerRadius: radius).fill()
        }
        return image.resizableImage(
            withCapInsets: UIEdgeInsets(top: 0, left: cap, bottom: 0, right: cap),
            resizingMode: .stretch
        )
    }

    final class Coordinator: NSObject {
        var onSelect: (Int) -> Void

        init(onSelect: @escaping (Int) -> Void) {
            self.onSelect = onSelect
        }

        @objc func didChange(_ sender: UISegmentedControl) {
            onSelect(sender.selectedSegmentIndex)
        }
    }

    final class ThickSegmentedControl: UISegmentedControl {
        var preferredHeight = TdayNativeSegmentedControlMetrics.height {
            didSet {
                if preferredHeight != oldValue {
                    invalidateIntrinsicContentSize()
                }
            }
        }

        override var intrinsicContentSize: CGSize {
            let baseSize = super.intrinsicContentSize
            return CGSize(width: baseSize.width, height: preferredHeight)
        }

        override func sizeThatFits(_ size: CGSize) -> CGSize {
            var fittingSize = super.sizeThatFits(size)
            fittingSize.height = preferredHeight
            return fittingSize
        }
    }
}
