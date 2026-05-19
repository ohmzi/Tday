import SwiftUI

struct TodoRowAction {
    let title: String
    let systemImage: String
    let tint: Color
    let role: ButtonRole?
    let action: () -> Void
}

extension View {
    func todoSwipeActions(_ actions: [TodoRowAction]) -> some View {
        swipeActions {
            ForEach(Array(actions.enumerated()), id: \.offset) { entry in
                let item = entry.element
                Button(role: item.role, action: item.action) {
                    Label(item.title, systemImage: item.systemImage)
                }
                .tint(item.tint)
            }
        }
    }

    func swipeRevealHintOnTap(enabled: Bool = true) -> some View {
        modifier(SwipeRevealHintModifier(enabled: enabled))
    }
}

private struct SwipeRevealHintModifier: ViewModifier {
    let enabled: Bool

    @State private var offsetX: CGFloat = 0
    @State private var isHinting = false

    func body(content: Content) -> some View {
        content
            .offset(x: offsetX)
            .contentShape(Rectangle())
            .onTapGesture {
                guard enabled, !isHinting else {
                    return
                }

                isHinting = true
                Task { @MainActor in
                    withAnimation(.spring(response: 0.26, dampingFraction: 0.78)) {
                        offsetX = -28
                    }
                    try? await Task.sleep(nanoseconds: 150_000_000)
                    withAnimation(.spring(response: 0.38, dampingFraction: 0.68)) {
                        offsetX = 0
                    }
                    try? await Task.sleep(nanoseconds: 340_000_000)
                    isHinting = false
                }
            }
    }
}
