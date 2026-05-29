import SwiftUI

private let defaultTaskFabFillColor = Color(red: 110.0 / 255.0, green: 168.0 / 255.0, blue: 225.0 / 255.0)

struct TaskFloatingActionButton: View {
    var fillColor = defaultTaskFabFillColor
    var pressedShadowOpacity = 0.14
    var normalShadowOpacity = 0.24
    let action: () -> Void

    private var borderColor: Color {
        taskFabBlend(fillColor, with: .black, amount: 0.18)
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(fillColor)
                .overlay(
                    Circle()
                        .stroke(borderColor.opacity(0.58), lineWidth: 1)
                )
                .clipShape(Circle())
        }
        .buttonStyle(
            TdayPressButtonStyle(
                pressedShadowOpacity: pressedShadowOpacity,
                normalShadowOpacity: normalShadowOpacity
            )
        )
        .accessibilityLabel("Create Task")
    }
}

private func taskFabBlend(_ color: Color, with other: Color, amount: CGFloat) -> Color {
    let lhs = UIColor(color)
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

struct TaskFloatingActionButtonDock: View {
    var fillColor = defaultTaskFabFillColor
    var pressedShadowOpacity = 0.14
    var normalShadowOpacity = 0.24
    let action: () -> Void

    var body: some View {
        HStack {
            Spacer()
            TaskFloatingActionButton(
                fillColor: fillColor,
                pressedShadowOpacity: pressedShadowOpacity,
                normalShadowOpacity: normalShadowOpacity,
                action: action
            )
                .padding(.trailing, 18)
                .padding(.vertical, 8)
        }
    }
}

struct TdayPressButtonStyle: ButtonStyle {
    var shadowColor = Color.black
    var pressedShadowOpacity = 0.14
    var normalShadowOpacity = 0.24

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayPressEffect(
                isPressed: configuration.isPressed,
                shadowColor: shadowColor,
                pressedShadowOpacity: pressedShadowOpacity,
                normalShadowOpacity: normalShadowOpacity
            )
    }
}

struct TdayToolbarButtonStyle: ButtonStyle {
    var shadowsEnabled = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayToolbarButtonEffect(
                isPressed: configuration.isPressed,
                shadowsEnabled: shadowsEnabled
            )
    }
}

extension View {
    func tdayPressEffect(
        isPressed: Bool,
        shadowColor: Color = Color.black,
        pressedShadowOpacity: Double = 0.14,
        normalShadowOpacity: Double = 0.24
    ) -> some View {
        modifier(
            TdayPressEffectModifier(
                isPressed: isPressed,
                shadowColor: shadowColor,
                pressedShadowOpacity: pressedShadowOpacity,
                normalShadowOpacity: normalShadowOpacity
            )
        )
    }

    func tdayToolbarButtonEffect(
        isPressed: Bool,
        shadowsEnabled: Bool = true
    ) -> some View {
        modifier(
            TdayToolbarButtonEffectModifier(
                isPressed: isPressed,
                shadowsEnabled: shadowsEnabled
            )
        )
    }

    func tdayRippleEffect(
        isPressed: Bool,
        rippleColor: Color? = nil
    ) -> some View {
        modifier(
            TdayRippleEffectModifier(
                isPressed: isPressed,
                rippleColor: rippleColor
            )
        )
    }
}

private struct TdayToolbarButtonEffectModifier: ViewModifier {
    let isPressed: Bool
    let shadowsEnabled: Bool

    func body(content: Content) -> some View {
        content
            .tdayRippleEffect(isPressed: isPressed)
            .scaleEffect(isPressed ? 0.95 : 1)
            .offset(y: isPressed ? 1 : 0)
            .shadow(
                color: Color.black.opacity(ambientShadowOpacity),
                radius: ambientShadowRadius,
                x: 0,
                y: ambientShadowOffsetY
            )
            .shadow(
                color: Color.black.opacity(keyShadowOpacity),
                radius: keyShadowRadius,
                x: 0,
                y: keyShadowOffsetY
            )
            .animation(.easeOut(duration: 0.14), value: isPressed)
    }

    private var ambientShadowOpacity: Double {
        guard shadowsEnabled else { return 0 }
        return isPressed ? 0.035 : 0.08
    }

    private var keyShadowOpacity: Double {
        guard shadowsEnabled else { return 0 }
        return isPressed ? 0.03 : 0.045
    }

    private var ambientShadowRadius: CGFloat {
        guard shadowsEnabled else { return 0 }
        return isPressed ? 8 : 24
    }

    private var keyShadowRadius: CGFloat {
        guard shadowsEnabled else { return 0 }
        return isPressed ? 3 : 6
    }

    private var ambientShadowOffsetY: CGFloat {
        guard shadowsEnabled else { return 0 }
        return isPressed ? 4 : 12
    }

    private var keyShadowOffsetY: CGFloat {
        guard shadowsEnabled else { return 0 }
        return isPressed ? 2 : 4
    }
}

private struct TdayPressEffectModifier: ViewModifier {
    let isPressed: Bool
    let shadowColor: Color
    let pressedShadowOpacity: Double
    let normalShadowOpacity: Double

    func body(content: Content) -> some View {
        content
            .tdayRippleEffect(isPressed: isPressed)
            .scaleEffect(isPressed ? 0.93 : 1)
            .offset(y: isPressed ? 2 : 0)
            .shadow(
                color: shadowColor.opacity(isPressed ? pressedShadowOpacity : normalShadowOpacity),
                radius: isPressed ? 8 : 16,
                x: 0,
                y: isPressed ? 4 : 10
            )
            .animation(.easeOut(duration: 0.14), value: isPressed)
    }
}

private struct TdayRippleEffectModifier: ViewModifier {
    let isPressed: Bool
    let rippleColor: Color?

    @Environment(\.colorScheme) private var colorScheme
    @State private var rippleScale: CGFloat = 0.08
    @State private var rippleOpacity: Double = 0

    private var defaultRippleColor: Color {
        colorScheme == .dark ? .white : .black
    }

    private var startingRippleOpacity: Double {
        colorScheme == .dark ? 0.18 : 0.12
    }

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { proxy in
                    let diameter = max(proxy.size.width, proxy.size.height) * 2.2

                    Circle()
                        .fill((rippleColor ?? defaultRippleColor).opacity(rippleOpacity))
                        .frame(width: diameter, height: diameter)
                        .scaleEffect(rippleScale)
                        .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
                        .allowsHitTesting(false)
                }
                .mask(content)
            }
            .onAppear {
                if isPressed {
                    triggerRipple()
                }
            }
            .onChange(of: isPressed) { _, newValue in
                if newValue {
                    triggerRipple()
                }
            }
    }

    private func triggerRipple() {
        var resetTransaction = Transaction()
        resetTransaction.disablesAnimations = true

        withTransaction(resetTransaction) {
            rippleScale = 0.08
            rippleOpacity = startingRippleOpacity
        }

        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.38)) {
                rippleScale = 1
                rippleOpacity = 0
            }
        }
    }
}
