import SwiftUI

enum TaskSwipeActionTint {
    static let edit = Color(.sRGB, red: 76.0 / 255.0, green: 125.0 / 255.0, blue: 222.0 / 255.0, opacity: 1)
    static let delete = Color(.sRGB, red: 255.0 / 255.0, green: 69.0 / 255.0, blue: 58.0 / 255.0, opacity: 1)
}

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

    func todoTrailingSwipeActions(
        enabled: Bool = true,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) -> some View {
        modifier(
            TodoTrailingSwipeActionsModifier(
                enabled: enabled,
                onEdit: onEdit,
                onDelete: onDelete
            )
        )
    }
}

private struct TodoTrailingSwipeActionsModifier: ViewModifier {
    let enabled: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    @State private var offsetX: CGFloat = 0
    @State private var isHinting = false
    @State private var dragStartOffsetX: CGFloat?
    @State private var isHorizontalDragging = false

    private let revealWidth: CGFloat = 152

    private var revealProgress: CGFloat {
        min(1, max(0, -offsetX / revealWidth))
    }

    func body(content: Content) -> some View {
        ZStack(alignment: .trailing) {
            HStack(spacing: 16) {
                Spacer()
                TodoSwipePillActionButton(
                    title: "Edit",
                    systemImage: "square.and.pencil",
                    tint: TaskSwipeActionTint.edit,
                    revealProgress: revealProgress,
                    revealDelay: 0.62
                ) {
                    closeActions()
                    onEdit()
                }

                TodoSwipePillActionButton(
                    title: "Delete",
                    systemImage: "trash",
                    tint: TaskSwipeActionTint.delete,
                    revealProgress: revealProgress,
                    revealDelay: 0.04
                ) {
                    closeActions()
                    onDelete()
                }
            }
            .padding(.trailing, 2)
            .frame(maxWidth: .infinity)

            content
                .offset(x: offsetX)
                .contentShape(Rectangle())
                .simultaneousGesture(
                    DragGesture(minimumDistance: 6)
                        .onChanged { value in
                            guard enabled else { return }
                            guard abs(value.translation.width) > abs(value.translation.height) else { return }
                            if !isHorizontalDragging {
                                dragStartOffsetX = offsetX
                                isHorizontalDragging = true
                            }
                            let proposed = (dragStartOffsetX ?? offsetX) + value.translation.width
                            if proposed < 0 {
                                offsetX = max(-revealWidth * 1.12, min(0, proposed))
                            } else {
                                offsetX = 0
                            }
                        }
                        .onEnded { value in
                            defer {
                                dragStartOffsetX = nil
                                isHorizontalDragging = false
                            }
                            guard enabled, isHorizontalDragging else { return }
                            let velocity = value.predictedEndTranslation.width - value.translation.width
                            let shouldOpen = offsetX < -(revealWidth * 0.32) || velocity < -200
                            withAnimation(.spring(response: 0.34, dampingFraction: 0.78)) {
                                offsetX = shouldOpen ? -revealWidth : 0
                            }
                        }
                )
                .onTapGesture {
                    guard enabled else { return }
                    if offsetX != 0 {
                        closeActions()
                    } else {
                        revealHint()
                    }
                }
        }
    }

    private func closeActions() {
        withAnimation(.spring(response: 0.26, dampingFraction: 0.8)) {
            offsetX = 0
        }
    }

    private func revealHint() {
        guard !isHinting else { return }

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

private struct TodoSwipePillActionButton: View {
    let title: String
    let systemImage: String
    let tint: Color
    let revealProgress: CGFloat
    let revealDelay: CGFloat
    let action: () -> Void

    private var easedReveal: CGFloat {
        let normalized = max(0, min(1, (revealProgress - revealDelay) / (1 - revealDelay)))
        return normalized * normalized * (3 - (2 * normalized))
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack {
                    RoundedRectangle(cornerRadius: 17, style: .continuous)
                        .fill(tint)
                    Image(systemName: systemImage)
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 56, height: 34)

                Text(title)
                    .font(.tdayRounded(size: 12, weight: .bold))
                    .foregroundStyle(Color(uiColor: .secondaryLabel).opacity(0.82))
                    .lineLimit(1)
            }
            .frame(minWidth: 60)
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0,
                normalShadowOpacity: 0
            )
        )
        .opacity(Double(easedReveal))
        .scaleEffect(0.38 + (0.62 * easedReveal))
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
