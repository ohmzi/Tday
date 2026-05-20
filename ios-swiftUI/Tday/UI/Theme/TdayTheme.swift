import SwiftUI
import UIKit

struct TdayColors {
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

    static let `default` = TdayColors(
        background: .tdayLightBackground,
        surface: .tdayLightSurface,
        surfaceVariant: .tdayLightSurfaceVariant,
        primary: .tdayLightAccent,
        secondary: .tdayLightSecondary,
        tertiary: .tdayLightWarm,
        error: .tdayLightError,
        onPrimary: .white,
        onSurface: .tdayLightForeground,
        onSurfaceVariant: .tdayLightMuted
    )
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

struct TdayTheme {
    static let backgroundGradient = LinearGradient(
        colors: [
            Color.tdayLightBackground,
            Color.tdayLightBackground,
            Color.tdayLightBackground
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
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

    var body: some View {
        ZStack {
            TdayTheme.backgroundGradient
                .ignoresSafeArea()
            content
        }
        .environment(\.tdayColors, .default)
    }
}

struct TdayCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.white.opacity(0.45), lineWidth: 1)
            )
    }
}

extension View {
    func tdayCard() -> some View {
        modifier(TdayCardModifier())
    }

    func tdayAppTypography() -> some View {
        font(.tdayRounded(.body, weight: .bold))
    }

    @ViewBuilder
    func tdayBottomSheetSizing() -> some View {
        if #available(iOS 18.0, *) {
            presentationSizing(.page)
        } else {
            self
        }
    }
}
