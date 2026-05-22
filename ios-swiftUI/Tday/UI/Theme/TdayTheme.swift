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

struct EmptyTaskWatermark: View {
    let systemName: String
    let accentColor: Color

    @Environment(\.tdayColors) private var colors
    private let iconSize: CGFloat = 194
    private let trailingOffset: CGFloat = 26

    private var watermarkColor: Color {
        colors.onSurfaceVariant.tdayBlended(with: accentColor, amount: 0.36).opacity(0.10)
    }

    var body: some View {
        GeometryReader { proxy in
            Image(systemName: systemName)
                .font(.system(size: iconSize, weight: .regular))
                .foregroundStyle(watermarkColor)
                .rotationEffect(.degrees(-7))
                .frame(width: iconSize, height: iconSize)
                .position(
                    x: proxy.size.width - (iconSize / 2) + trailingOffset,
                    y: proxy.size.height * (2.0 / 3.0)
                )
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

struct EmptyTaskBackgroundMessage: View {
    let message: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(message)
            .font(.tdayRounded(size: 28, weight: .bold))
            .foregroundStyle(colors.onSurfaceVariant.opacity(0.66))
            .multilineTextAlignment(.center)
            .lineLimit(2)
            .minimumScaleFactor(0.82)
            .padding(.horizontal, 32)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            .offset(x: 24)
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
            .tint(colors.primary)
            .background(colors.backgroundGradient.ignoresSafeArea())
            .preferredColorScheme(themeMode.colorScheme)
    }
}

extension View {
    func tdayCard() -> some View {
        modifier(TdayCardModifier())
    }

    func tdayAppTheme(themeMode: AppThemeMode) -> some View {
        modifier(TdayAppThemeModifier(themeMode: themeMode))
    }

    func tdayAppTypography() -> some View {
        font(.tdayRounded(.body, weight: .bold))
    }
}

enum TdayNativeSegmentedControlMetrics {
    static let height: CGFloat = 52
}

struct TdayNativeSegmentedControl: UIViewRepresentable {
    let labels: [String]
    let selectedIndex: Int
    let accentColor: Color
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
            height: TdayNativeSegmentedControlMetrics.height
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
        control.overrideUserInterfaceStyle = colors.isDark ? .dark : .light
        control.backgroundColor = UIColor(colors.surfaceVariant.opacity(0.76))
        control.selectedSegmentTintColor = UIColor(colors.surface)
        control.tintColor = UIColor(accentColor)
        control.setTitleTextAttributes(
            [
                .foregroundColor: UIColor(colors.onSurfaceVariant),
                .font: TdayFont.uiFont(size: 13, weight: .bold)
            ],
            for: .normal
        )
        control.setTitleTextAttributes(
            [
                .foregroundColor: UIColor(colors.onSurface),
                .font: TdayFont.uiFont(size: 13, weight: .bold)
            ],
            for: .selected
        )
        control.invalidateIntrinsicContentSize()
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
        override var intrinsicContentSize: CGSize {
            let baseSize = super.intrinsicContentSize
            return CGSize(width: baseSize.width, height: TdayNativeSegmentedControlMetrics.height)
        }

        override func sizeThatFits(_ size: CGSize) -> CGSize {
            var fittingSize = super.sizeThatFits(size)
            fittingSize.height = TdayNativeSegmentedControlMetrics.height
            return fittingSize
        }
    }
}
