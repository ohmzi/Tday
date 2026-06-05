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
        rowID: String,
        openRowID: Binding<String?>,
        enabled: Bool = true,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) -> some View {
        modifier(
            TodoTrailingSwipeActionsModifier(
                rowID: rowID,
                openRowID: openRowID,
                enabled: enabled,
                onEdit: onEdit,
                onDelete: onDelete
            )
        )
    }
}

private struct TodoTrailingSwipeActionsModifier: ViewModifier {
    let rowID: String
    @Binding var openRowID: String?
    let enabled: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    @State private var offsetX: CGFloat = 0
    @State private var isHinting = false

    private let revealWidth: CGFloat = 152
    private let openVelocityThreshold: CGFloat = -180

    private var revealProgress: CGFloat {
        min(1, max(0, -offsetX / revealWidth))
    }

    func body(content: Content) -> some View {
        ZStack(alignment: .trailing) {
            content
                .offset(x: offsetX)
                .contentShape(Rectangle())
                .background(
                    HorizontalSwipePanObserver(
                        enabled: enabled,
                        rowID: rowID,
                        openRowID: $openRowID,
                        revealWidth: revealWidth,
                        openVelocityThreshold: openVelocityThreshold,
                        offsetX: $offsetX
                    )
                )
                .onTapGesture {
                    guard enabled else { return }
                    if offsetX != 0 {
                        closeActions()
                    } else {
                        HapticManager.swipeReveal()
                        revealHint()
                    }
                }
                .onChange(of: openRowID) { _, activeID in
                    if activeID != rowID && offsetX != 0 {
                        closeActions(clearOpenRow: false)
                    }
                }
                .onChange(of: enabled) { _, isEnabled in
                    if !isEnabled {
                        closeActions()
                    }
                }

            HStack(spacing: 16) {
                Spacer()
                TodoSwipePillActionButton(
                    title: "Edit",
                    assetName: "ActionEdit",
                    tint: TaskSwipeActionTint.edit,
                    revealProgress: revealProgress,
                    revealDelay: 0.62
                ) {
                    HapticManager.buttonTap()
                    closeActions()
                    onEdit()
                }

                TodoSwipePillActionButton(
                    title: "Delete",
                    assetName: "ActionDelete",
                    tint: TaskSwipeActionTint.delete,
                    revealProgress: revealProgress,
                    revealDelay: 0.04
                ) {
                    HapticManager.buttonTap()
                    closeActions()
                    onDelete()
                }
            }
            .padding(.trailing, 2)
            .frame(maxWidth: .infinity)
        }
    }

    private func claimRow() {
        if openRowID != rowID {
            openRowID = rowID
        }
    }

    private func closeActions(clearOpenRow: Bool = true) {
        withAnimation(.interactiveSpring(response: 0.26, dampingFraction: 0.86)) {
            offsetX = 0
        }
        if clearOpenRow && openRowID == rowID {
            openRowID = nil
        }
    }

    private func revealHint() {
        guard !isHinting else { return }

        isHinting = true
        claimRow()
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
            if openRowID == rowID && offsetX == 0 {
                openRowID = nil
            }
        }
    }
}

private struct HorizontalSwipePanObserver: UIViewRepresentable {
    let enabled: Bool
    let rowID: String
    @Binding var openRowID: String?
    let revealWidth: CGFloat
    let openVelocityThreshold: CGFloat
    @Binding var offsetX: CGFloat

    func makeCoordinator() -> Coordinator {
        Coordinator(rowID: rowID, openRowID: $openRowID, offsetX: $offsetX)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.enabled = enabled
        context.coordinator.rowID = rowID
        context.coordinator.openRowID = $openRowID
        context.coordinator.revealWidth = revealWidth
        context.coordinator.openVelocityThreshold = openVelocityThreshold
        context.coordinator.offsetX = $offsetX
        DispatchQueue.main.async {
            context.coordinator.attach(to: uiView)
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var enabled = true
        var rowID: String
        var openRowID: Binding<String?>
        var revealWidth: CGFloat = 152
        var openVelocityThreshold: CGFloat = -180
        var offsetX: Binding<CGFloat>

        private weak var markerView: UIView?
        private weak var observedScrollView: UIScrollView?
        private var dragStartOffsetX: CGFloat = 0
        private lazy var panRecognizer: UIPanGestureRecognizer = {
            let recognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
            recognizer.cancelsTouchesInView = false
            recognizer.delaysTouchesBegan = false
            recognizer.delaysTouchesEnded = false
            recognizer.delegate = self
            return recognizer
        }()

        init(rowID: String, openRowID: Binding<String?>, offsetX: Binding<CGFloat>) {
            self.rowID = rowID
            self.openRowID = openRowID
            self.offsetX = offsetX
        }

        deinit {
            observedScrollView?.removeGestureRecognizer(panRecognizer)
        }

        func attach(to markerView: UIView) {
            self.markerView = markerView
            guard let scrollView = markerView.enclosingSwipeScrollView() else {
                return
            }
            guard observedScrollView !== scrollView else {
                return
            }

            observedScrollView?.removeGestureRecognizer(panRecognizer)
            observedScrollView = scrollView
            scrollView.addGestureRecognizer(panRecognizer)
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard enabled,
                  gestureRecognizer === panRecognizer,
                  let scrollView = observedScrollView,
                  let markerView else {
                return false
            }

            let location = panRecognizer.location(in: markerView)
            guard markerView.bounds.insetBy(dx: 0, dy: -4).contains(location) else {
                return false
            }

            let velocity = panRecognizer.velocity(in: scrollView)
            let horizontalVelocity = abs(velocity.x)
            let verticalVelocity = abs(velocity.y)
            return horizontalVelocity > 45 && horizontalVelocity > verticalVelocity + 28
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc private func handlePan(_ recognizer: UIPanGestureRecognizer) {
            guard enabled, let scrollView = observedScrollView else {
                return
            }

            switch recognizer.state {
            case .began:
                dragStartOffsetX = offsetX.wrappedValue
                if dragStartOffsetX != 0 {
                    openRowID.wrappedValue = rowID
                }
            case .changed:
                let translation = recognizer.translation(in: scrollView)
                let proposed = dragStartOffsetX + translation.x
                if proposed < 0 {
                    openRowID.wrappedValue = rowID
                    offsetX.wrappedValue = max(-revealWidth * 1.12, min(0, proposed))
                } else {
                    offsetX.wrappedValue = 0
                    if openRowID.wrappedValue == rowID {
                        openRowID.wrappedValue = nil
                    }
                }
            case .ended, .cancelled, .failed:
                let velocityX = recognizer.velocity(in: scrollView).x
                let shouldOpen = offsetX.wrappedValue < -(revealWidth * 0.32) ||
                    velocityX < openVelocityThreshold
                if shouldOpen {
                    openRowID.wrappedValue = rowID
                } else if openRowID.wrappedValue == rowID {
                    openRowID.wrappedValue = nil
                }
                withAnimation(.interactiveSpring(response: 0.34, dampingFraction: 0.82)) {
                    offsetX.wrappedValue = shouldOpen ? -revealWidth : 0
                }
                dragStartOffsetX = 0
            default:
                break
            }
        }
    }
}

private struct TodoSwipePillActionButton: View {
    let title: String
    /// Asset-catalog name of the lucide template glyph (shared with web/Android).
    let assetName: String
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
                    Image(assetName)
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 21, height: 21)
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
        .allowsHitTesting(easedReveal > 0.8)
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

private extension UIView {
    func enclosingSwipeScrollView() -> UIScrollView? {
        var view: UIView? = self
        while let current = view {
            if let scrollView = current as? UIScrollView {
                return scrollView
            }
            view = current.superview
        }
        return nil
    }
}
