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

    var body: some View {
        Button(action: action) {
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
        fullScreenCover(isPresented: isPresented) {
            TdayBottomSheetPresentationHost {
                content()
            }
        }
    }

    func tdayBottomSheetPresentation<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        fullScreenCover(item: item) { item in
            TdayBottomSheetPresentationHost {
                content(item)
            }
        }
    }
}

private struct TdayBottomSheetPresentationHost<SheetContent: View>: View {
    let content: SheetContent
    @State private var keyboardFrame: CGRect?
    @State private var contentHeight: CGFloat = 0

    init(@ViewBuilder content: () -> SheetContent) {
        self.content = content()
    }

    var body: some View {
        GeometryReader { proxy in
            let keyboardBottomInset = keyboardBottomInset(for: proxy, contentHeight: contentHeight)

            ZStack(alignment: .bottom) {
                Color.clear
                    .contentShape(Rectangle())
                    .ignoresSafeArea()

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
                    .offset(y: -keyboardBottomInset)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .ignoresSafeArea(.container, edges: .bottom)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .presentationBackground(.clear)
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
        Button(action: action) {
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
