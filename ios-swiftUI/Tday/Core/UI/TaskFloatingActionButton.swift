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
        .buttonStyle(TaskFloatingActionButtonStyle())
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

private struct TaskFloatingActionButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .offset(y: configuration.isPressed ? 2 : 0)
            .shadow(
                color: taskFabFillColor.opacity(configuration.isPressed ? 0.18 : 0.28),
                radius: configuration.isPressed ? 8 : 16,
                x: 0,
                y: configuration.isPressed ? 4 : 10
            )
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
}
