import SwiftUI

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
            Color.tdayLightBackground.opacity(0.8),
            Color.tdayLightSurfaceVariant.opacity(0.45)
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
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
}
