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
        _ = TdayFullScreenCoverTransitionSwizzle.installOnce
        let markedBinding = Binding<Bool>(
            get: { isPresented.wrappedValue },
            set: { newValue in
                if newValue {
                    TdayFullScreenCoverTransitionSwizzle.pendingUnanimatedPresentation = true
                }
                isPresented.wrappedValue = newValue
            }
        )
        return fullScreenCover(isPresented: markedBinding) {
            TdayBottomSheetPresentationHost {
                content()
            }
        }
    }

    func tdayBottomSheetPresentation<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        _ = TdayFullScreenCoverTransitionSwizzle.installOnce
        let markedBinding = Binding<Item?>(
            get: { item.wrappedValue },
            set: { newValue in
                if newValue != nil {
                    TdayFullScreenCoverTransitionSwizzle.pendingUnanimatedPresentation = true
                }
                item.wrappedValue = newValue
            }
        )
        return fullScreenCover(item: markedBinding) { item in
            TdayBottomSheetPresentationHost {
                content(item)
            }
        }
    }
}

private struct TdayBottomSheetPresentationHost<SheetContent: View>: View {
    let content: SheetContent
    @Environment(\.tdayColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @State private var keyboardFrame: CGRect?
    @State private var contentHeight: CGFloat = 0
    @State private var scrimVisible = false
    // fullScreenCover's default UIKit transition slides the whole presented
    // container — scrim included — up from the bottom, so the dim layer
    // visibly rides along with the card instead of just fading (very
    // noticeable in light mode against a bright background behind it).
    // TdayFullScreenCoverTransitionSwizzle neutralizes that container slide;
    // this offset supplies the card's own slide-up motion instead, so only
    // the card moves and the scrim purely fades, matching native .sheet().
    @State private var contentOffset: CGFloat = UIScreen.main.bounds.height

    init(@ViewBuilder content: () -> SheetContent) {
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
                    .opacity(scrimVisible ? 1 : 0)
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
                    .offset(y: contentOffset - keyboardBottomInset)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .ignoresSafeArea(.container, edges: .bottom)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .presentationBackground(.clear)
        .onAppear {
            withAnimation(.easeOut(duration: 0.22)) {
                scrimVisible = true
            }
            withAnimation(.spring(response: 0.4, dampingFraction: 0.86)) {
                contentOffset = 0
            }
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
        withAnimation(.easeIn(duration: 0.18)) {
            scrimVisible = false
        }
        withAnimation(.easeIn(duration: 0.22)) {
            contentOffset = UIScreen.main.bounds.height
        }
        dismiss()
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

/// SwiftUI gives `fullScreenCover` no way to opt out of its default
/// `coverVertical` modal transition, which slides the whole presented view
/// controller — our scrim included — up from the bottom, so the dim layer
/// arrives as a hard-edged band climbing the screen instead of a fade.
///
/// Setting `modalTransitionStyle = .crossDissolve` does NOT fix this: SwiftUI
/// presents transparent-background covers through its own presentation path
/// (the controller carries `legacyPresentationWantsTransparentBackground` /
/// `bridgedPresentationWantsTransparentBackground`), and that path ignores
/// `modalTransitionStyle` — verified on device, the band survived it.
///
/// So instead of trying to pick a different container animation, this removes
/// the container animation entirely: present with `animated: false`, so the
/// cover simply exists, with the scrim already at opacity 0 and the card
/// already parked offscreen via `contentOffset`. There is no container slide
/// for the scrim to ride on, and `TdayBottomSheetPresentationHost.onAppear`
/// then does all the visible work — the scrim fades in place while only the
/// card travels upward, which is how native `.sheet()` reads.
///
/// The presentation is identified by a flag set synchronously the instant our
/// own binding flips to true/non-nil in `tdayBottomSheetPresentation`, which
/// happens before SwiftUI's next run-loop pass actually calls `present()`.
/// Matching on the presented controller instead is not viable: SwiftUI
/// type-erases the content to `PresentationHostingController<AnyView>` and
/// buries the real view value behind view-graph plumbing `Mirror` can't
/// reach in any bounded walk. The flag is consumed on first use so it cannot
/// leak onto an unrelated presentation.
///
/// Dismissal is deliberately left animated — the container's downward slide
/// is masked by the scrim's own 0.18s fade-out, so it reads correctly and
/// still covers dismissals that bypass `dismissSheet()` (e.g. a caller just
/// setting `isPresented = false` after saving).
private enum TdayFullScreenCoverTransitionSwizzle {
    static var pendingUnanimatedPresentation = false

    static let installOnce: Void = {
        guard
            let originalMethod = class_getInstanceMethod(
                UIViewController.self,
                #selector(UIViewController.present(_:animated:completion:))
            ),
            let swizzledMethod = class_getInstanceMethod(
                UIViewController.self,
                #selector(UIViewController.tdayBottomSheet_present(_:animated:completion:))
            )
        else {
            return
        }
        method_exchangeImplementations(originalMethod, swizzledMethod)
    }()
}

extension UIViewController {
    @objc fileprivate func tdayBottomSheet_present(
        _ viewControllerToPresent: UIViewController,
        animated: Bool,
        completion: (() -> Void)?
    ) {
        var animated = animated
        if TdayFullScreenCoverTransitionSwizzle.pendingUnanimatedPresentation {
            TdayFullScreenCoverTransitionSwizzle.pendingUnanimatedPresentation = false
            animated = false
        }
        // Swapped via method_exchangeImplementations, so this recurses into
        // the original present(_:animated:completion:), not itself.
        tdayBottomSheet_present(viewControllerToPresent, animated: animated, completion: completion)
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
