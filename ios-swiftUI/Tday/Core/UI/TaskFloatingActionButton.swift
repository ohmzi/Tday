import SwiftUI

private let taskFabFillColor = Color(red: 110.0 / 255.0, green: 168.0 / 255.0, blue: 225.0 / 255.0)
private let taskFabBorderColor = Color(red: 61.0 / 255.0, green: 127.0 / 255.0, blue: 234.0 / 255.0)

struct TaskFloatingActionButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(taskFabFillColor)
                .overlay(
                    Circle()
                        .stroke(taskFabBorderColor.opacity(0.58), lineWidth: 1)
                )
                .clipShape(Circle())
        }
        .buttonStyle(TdayPressButtonStyle())
        .accessibilityLabel("Create Task")
    }
}

struct TaskFloatingActionButtonDock: View {
    let action: () -> Void

    var body: some View {
        HStack {
            Spacer()
            TaskFloatingActionButton(action: action)
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
}

private struct TdayPressEffectModifier: ViewModifier {
    let isPressed: Bool
    let shadowColor: Color
    let pressedShadowOpacity: Double
    let normalShadowOpacity: Double

    func body(content: Content) -> some View {
        content
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
