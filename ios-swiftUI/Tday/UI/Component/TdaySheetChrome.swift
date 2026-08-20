import SwiftUI
import UIKit

enum TdaySheetMetrics {
    static let maximumScreenHeightFraction: CGFloat = 0.80
    static let horizontalPadding: CGFloat = 18
    static let verticalPadding: CGFloat = 14
    static let sectionSpacing: CGFloat = 14
    static let actionSize: CGFloat = 56
    static let actionIconSize: CGFloat = 22
    static let cardCornerRadius: CGFloat = 28
    static let overlayCornerRadius: CGFloat = 30
    static let selectorCornerRadius: CGFloat = 32
    static let sheetCornerRadius: CGFloat = 34
    static let closeAccent = Color(red: 227.0 / 255.0, green: 90.0 / 255.0, blue: 90.0 / 255.0)
    static let confirmAccent = Color(red: 47.0 / 255.0, green: 163.0 / 255.0, blue: 91.0 / 255.0)
}

struct TdaySheetHeader: View {
    let title: String
    var closeSystemName = "xmark"
    var closeAccessibilityLabel = "Close"
    var confirmSystemName: String? = "checkmark"
    var confirmAccessibilityLabel = "Done"
    var isConfirmEnabled = true
    let onClose: () -> Void
    var onConfirm: () -> Void = {}

    @Environment(\.tdayColors) private var colors

    var body: some View {
        HStack {
            TdaySheetActionButton(
                systemName: closeSystemName,
                accessibilityLabel: closeAccessibilityLabel,
                accentColor: TdaySheetMetrics.closeAccent,
                isEnabled: true,
                action: onClose
            )

            Spacer(minLength: 0)

            Text(title)
                .font(.tdayRounded(size: 24, weight: .heavy))
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.78)

            Spacer(minLength: 0)

            if let confirmSystemName {
                TdaySheetActionButton(
                    systemName: confirmSystemName,
                    accessibilityLabel: confirmAccessibilityLabel,
                    accentColor: TdaySheetMetrics.confirmAccent,
                    isEnabled: isConfirmEnabled,
                    action: onConfirm
                )
            } else {
                Color.clear
                    .frame(width: TdaySheetMetrics.actionSize, height: TdaySheetMetrics.actionSize)
            }
        }
        .padding(.horizontal, TdaySheetMetrics.horizontalPadding)
        .padding(.top, TdaySheetMetrics.verticalPadding)
        .padding(.bottom, TdaySheetMetrics.verticalPadding)
        .background(colors.bottomSheetBackground)
    }
}

struct TdaySheetActionButton: View {
    let systemName: String
    let accessibilityLabel: String
    let accentColor: Color
    let isEnabled: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    private var isConfirm: Bool {
        accentColor == TdaySheetMetrics.confirmAccent
    }

    var body: some View {
        Button {
            if isConfirm {
                HapticManager.sheetConfirm()
            } else {
                HapticManager.sheetDismiss()
            }
            action()
        } label: {
            Image(systemName: systemName)
                .font(.system(size: TdaySheetMetrics.actionIconSize, weight: .semibold))
                .foregroundStyle(colors.onSurface.opacity(isEnabled ? 1 : 0.55))
                .frame(width: TdaySheetMetrics.actionSize, height: TdaySheetMetrics.actionSize)
                .background(colors.bottomSheetControlSurface, in: Circle())
                .overlay {
                    Circle()
                        .stroke(accentColor.opacity(isEnabled ? 0.55 : 0.3), lineWidth: 1.5)
                }
                .contentShape(Circle())
        }
        .buttonStyle(
            TdayPressButtonStyle(
                shadowColor: Color.black,
                pressedShadowOpacity: 0.04,
                normalShadowOpacity: isEnabled ? 0.16 : 0.06
            )
        )
        .disabled(!isEnabled)
        .accessibilityLabel(accessibilityLabel)
    }
}

struct TdaySheetSectionTitle: View {
    let text: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Text(text)
            .font(.tdayRounded(size: 22, weight: .bold))
            .foregroundStyle(colors.onSurfaceVariant)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }
}

struct TdaySheetCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: TdaySheetMetrics.cardCornerRadius, style: .continuous)
                    .fill(colors.bottomSheetSurface)
            )
            .clipShape(RoundedRectangle(cornerRadius: TdaySheetMetrics.cardCornerRadius, style: .continuous))
    }
}

struct TdaySheetOverlayCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        content
            .background(
                colors.bottomSheetSurface,
                in: RoundedRectangle(cornerRadius: TdaySheetMetrics.overlayCornerRadius, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: TdaySheetMetrics.overlayCornerRadius, style: .continuous)
                    .stroke(colors.cardStroke, lineWidth: 1)
            }
            .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 24, x: 0, y: 12)
    }
}

struct TdayCenteredSelectorCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    @Environment(\.tdayColors) private var colors

    var body: some View {
        TdaySheetOverlayCard {
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurfaceVariant)
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 12)

                content
            }
            .padding(.bottom, 14)
            .frame(maxWidth: 330)
        }
    }
}

extension View {
    func tdayBottomSheetPresentation<SheetContent: View>(
        isPresented: Binding<Bool>,
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        modifier(TdayBottomSheetPresentationModifier(isPresented: isPresented, sheetContent: content))
    }

    func tdayBottomSheetPresentation<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        modifier(TdayBottomSheetItemPresentationModifier(item: item, sheetContent: content))
    }
}

/// Timing for the sheet's entrance and exit, kept in one place so the card
/// animation and the deferred teardown can't drift apart.
private enum TdayBottomSheetMotion {
    static let exitDuration: TimeInterval = 0.24

    static let scrimIn = Animation.easeOut(duration: 0.22)
    static let scrimOut = Animation.easeIn(duration: 0.2)
    static let cardIn = Animation.spring(response: 0.4, dampingFraction: 0.86)
    static let cardOut = Animation.easeIn(duration: exitDuration)
}

/// Applies a state change with SwiftUI animations suppressed, so a
/// `fullScreenCover` driven by that state appears/disappears with no
/// container transition of its own.
private func withoutPresentationAnimation(_ body: () -> Void) {
    var transaction = Transaction()
    transaction.disablesAnimations = true
    withTransaction(transaction, body)
}

/// Re-entrancy guard for the deferred dismissal both presentation modifiers
/// use. A dismissal can be requested from several directions at once (the
/// caller clearing its binding *and* SwiftUI writing `false` through the
/// cover's binding); only the first may start the exit animation.
private struct TdayBottomSheetDismissal {
    /// Incremented to ask the presented host to animate itself out.
    private(set) var requestID = 0
    private var isDismissing = false

    mutating func begin() {
        guard !isDismissing else {
            return
        }
        isDismissing = true
        requestID += 1
    }

    mutating func reset() {
        isDismissing = false
    }
}

/// Drives the bottom sheet's own entrance/exit instead of letting
/// `fullScreenCover` animate its container.
///
/// The container animation is the problem being avoided: it slides the *whole*
/// presented view, and the dim scrim lives inside that view, so the dim
/// arrives and leaves as a hard-edged band travelling up and down the screen.
/// Presenting and tearing down with animations suppressed means there is
/// nothing to slide — the cover simply exists, and
/// `TdayBottomSheetPresentationHost` fades the scrim in place while only the
/// card travels, which is how a native `.sheet()` reads.
///
/// Two details matter:
/// - Presentation is triggered from `onChange` of the caller's binding, not
///   from a wrapper `Binding`'s setter. Call sites flip their own `@State`
///   directly (`showingCreateList = true`), which never routes through a
///   wrapper's `set`, so a wrapper-based hook would silently never run.
/// - Teardown is deferred. Every dismissal request — including
///   `@Environment(\.dismiss)` from inside sheet content, which writes `false`
///   through the cover's binding — is intercepted so the host can animate out
///   first; the (by then invisible) cover is removed afterwards.
private struct TdayBottomSheetPresentationModifier<SheetContent: View>: ViewModifier {
    @Binding var isPresented: Bool
    @ViewBuilder let sheetContent: () -> SheetContent

    @State private var isCoverPresented = false
    @State private var dismissal = TdayBottomSheetDismissal()

    private var coverBinding: Binding<Bool> {
        Binding(
            get: { isCoverPresented },
            set: { newValue in
                if newValue {
                    isCoverPresented = true
                } else {
                    dismissal.begin()
                }
            }
        )
    }

    func body(content: Content) -> some View {
        content
            .onChange(of: isPresented) { _, newValue in
                if newValue {
                    dismissal.reset()
                    withoutPresentationAnimation { isCoverPresented = true }
                } else if isCoverPresented {
                    dismissal.begin()
                }
            }
            .fullScreenCover(isPresented: coverBinding) {
                TdayBottomSheetPresentationHost(
                    dismissRequestID: dismissal.requestID,
                    onDismissAnimationCompleted: finishDismissal
                ) {
                    sheetContent()
                }
            }
    }

    private func finishDismissal() {
        withoutPresentationAnimation { isCoverPresented = false }
        dismissal.reset()
        isPresented = false
    }
}

/// `item`-driven counterpart of `TdayBottomSheetPresentationModifier`.
private struct TdayBottomSheetItemPresentationModifier<Item: Identifiable, SheetContent: View>: ViewModifier {
    @Binding var item: Item?
    @ViewBuilder let sheetContent: (Item) -> SheetContent

    // Held separately from `item` so the sheet still has content to render
    // while it animates out after the caller has already cleared `item`.
    @State private var presentedItem: Item?
    @State private var dismissal = TdayBottomSheetDismissal()

    private var coverBinding: Binding<Item?> {
        Binding(
            get: { presentedItem },
            set: { newValue in
                if let newValue {
                    presentedItem = newValue
                } else {
                    dismissal.begin()
                }
            }
        )
    }

    func body(content: Content) -> some View {
        content
            .onChange(of: item?.id) { _, _ in
                if let item {
                    dismissal.reset()
                    withoutPresentationAnimation { presentedItem = item }
                } else if presentedItem != nil {
                    dismissal.begin()
                }
            }
            .fullScreenCover(item: coverBinding) { presented in
                TdayBottomSheetPresentationHost(
                    dismissRequestID: dismissal.requestID,
                    onDismissAnimationCompleted: finishDismissal
                ) {
                    sheetContent(presented)
                }
            }
    }

    private func finishDismissal() {
        withoutPresentationAnimation { presentedItem = nil }
        dismissal.reset()
        item = nil
    }
}

private struct TdayBottomSheetPresentationHost<SheetContent: View>: View {
    /// Bumped by the presenting modifier to ask for an animated exit. The
    /// cover is only torn down once `onDismissAnimationCompleted` fires.
    let dismissRequestID: Int
    let onDismissAnimationCompleted: () -> Void
    let content: SheetContent

    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @State private var keyboardFrame: CGRect?
    @State private var contentHeight: CGFloat = 0
    // The cover is presented and torn down with animations suppressed (see
    // TdayBottomSheetPresentationModifier), so these two flags supply the
    // entire visible entrance and exit: the scrim fades where it already is,
    // and only the card travels.
    @State private var isScrimVisible = false
    @State private var isCardRaised = false

    init(
        dismissRequestID: Int,
        onDismissAnimationCompleted: @escaping () -> Void,
        @ViewBuilder content: () -> SheetContent
    ) {
        self.dismissRequestID = dismissRequestID
        self.onDismissAnimationCompleted = onDismissAnimationCompleted
        self.content = content()
    }

    var body: some View {
        GeometryReader { proxy in
            let keyboardBottomInset = keyboardBottomInset(for: proxy, contentHeight: contentHeight)

            ZStack(alignment: .bottom) {
                // Dimmed backdrop so the sheet reads as a modal layer above the
                // screen behind it. Tapping it dismisses, matching the platform's
                // standard bottom-sheet behavior.
                colors.bottomSheetScrim
                    .opacity(isScrimVisible ? 1 : 0)
                    .contentShape(Rectangle())
                    .ignoresSafeArea()
                    .onTapGesture { dismissSheet() }

                content
                    .frame(maxWidth: .infinity, alignment: .bottom)
                    .background {
                        GeometryReader { contentProxy in
                            Color.clear.preference(
                                key: TdayBottomSheetContentHeightPreferenceKey.self,
                                value: contentProxy.size.height
                            )
                        }
                    }
                    // Parked a full screen height down until raised, so the card
                    // starts (and ends) fully offscreen without needing a
                    // measured height on the very first render.
                    .offset(y: (isCardRaised ? 0 : proxy.size.height) - keyboardBottomInset)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .ignoresSafeArea(.container, edges: .bottom)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .presentationBackground(.clear)
        .onAppear {
            withAnimation(TdayBottomSheetMotion.scrimIn) {
                isScrimVisible = true
            }
            withAnimation(TdayBottomSheetMotion.cardIn) {
                isCardRaised = true
            }
        }
        .onChange(of: dismissRequestID) { _, _ in
            animateOut()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { notification in
            updateKeyboardFrame(from: notification)
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { notification in
            updateKeyboardFrame(from: notification)
        }
        .onPreferenceChange(TdayBottomSheetContentHeightPreferenceKey.self) { height in
            contentHeight = height
        }
    }

    private func dismissSheet() {
        HapticManager.sheetDismiss()
        // Drop the keyboard first so it doesn't linger during the dismiss.
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
        // Routed through `dismiss()` rather than animating here, so a scrim tap
        // takes exactly the same path as a `dismiss()` from inside the sheet:
        // the presenting modifier intercepts it and drives `animateOut()`.
        dismiss()
    }

    /// Fades the scrim out where it stands and slides only the card away, then
    /// lets the presenting modifier remove the (by then invisible) cover.
    private func animateOut() {
        withAnimation(TdayBottomSheetMotion.scrimOut) {
            isScrimVisible = false
        }
        withAnimation(TdayBottomSheetMotion.cardOut) {
            isCardRaised = false
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + TdayBottomSheetMotion.exitDuration) {
            onDismissAnimationCompleted()
        }
    }

    private func keyboardBottomInset(for proxy: GeometryProxy, contentHeight: CGFloat) -> CGFloat {
        guard let keyboardFrame else {
            return 0
        }
        let hostFrame = proxy.frame(in: .global)
        let overlap = hostFrame.maxY - keyboardFrame.minY
        let requestedInset = min(max(overlap, 0), hostFrame.height)
        guard contentHeight > 0 else {
            return 0
        }
        let minimumSheetTop = hostFrame.height * (1 - TdaySheetMetrics.maximumScreenHeightFraction)
        let currentSheetTop = hostFrame.height - contentHeight
        let maximumInsetBeforeExceedingSheetLimit = max(currentSheetTop - minimumSheetTop, 0)
        return min(requestedInset, maximumInsetBeforeExceedingSheetLimit)
    }

    private func updateKeyboardFrame(from notification: Notification) {
        guard let endFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return
        }
        let screenMaxY = UIScreen.main.bounds.maxY
        let isHidden = endFrame.minY >= screenMaxY
        let duration = notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double ?? 0.25

        withAnimation(.easeOut(duration: duration)) {
            keyboardFrame = isHidden ? nil : endFrame
        }
    }
}

private struct TdayBottomSheetContentHeightPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

struct TdayCenteredSelectorRow: View {
    let title: String
    let swatchColor: Color
    let selected: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button {
            HapticManager.gentleTap()
            action()
        } label: {
            HStack(spacing: 14) {
                Circle()
                    .fill(swatchColor)
                    .frame(width: 10, height: 10)

                Text(title)
                    .font(.tdayRounded(size: 18, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)

                Spacer(minLength: 12)

                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(colors.primary)
                } else {
                    Color.clear
                        .frame(width: 18, height: 18)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct TdaySheetDivider: View {
    var horizontalPadding: CGFloat = 18
    var opacity: Double = 0.18

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Rectangle()
            .fill(colors.onSurfaceVariant.opacity(opacity))
            .frame(height: 1)
            .padding(.horizontal, horizontalPadding)
    }
}
