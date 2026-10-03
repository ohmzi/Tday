import SwiftUI

/// The "help fix crashes?" card: the question asked once the workspace is open, for every install
/// the wizard did not ask it of.
///
/// Drawn with the wizard's card geometry, its buttons and the same disclosure — the wizard's last
/// step is this same question, and this is what it falls back to for an install that reaches the
/// workspace without a wizard: one already signed in at launch, one restarted mid-step, or one
/// whose sign-in ended under a gate (a required update, security questions, the app lock). It sits
/// in `AppRootView`'s overlay stack below every gate that has to come first and only ever shows
/// while none of them is up and the wizard is not holding the step itself. Two buttons of equal
/// weight, so that neither answer is the path of least resistance, and as plain as a question about
/// a setting can be: what is sent, what never is.
struct TelemetryConsentCard: View {
    let onShare: () -> Void
    let onDecline: () -> Void
    /// "Read the full FAQ" is not an answer: the caller holds the card back and opens the guide.
    let onReadFAQ: () -> Void
    /// VoiceOver's escape gesture. Like the FAQ link it only holds the card back until the next
    /// launch; it never answers for the person.
    let onDefer: () -> Void

    @Environment(\.tdayColors) private var colors

    private typealias Metrics = OnboardingWizardOverlay.Metrics

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()

            GeometryReader { proxy in
                ScrollView(showsIndicators: false) {
                    VStack {
                        Spacer(minLength: Metrics.overlayPadding)
                        card
                        Spacer(minLength: Metrics.overlayPadding)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: proxy.size.height)
                    .padding(.horizontal, Metrics.overlayPadding)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: Metrics.sectionSpacing) {
            TelemetryConsentDisclosure()

            VStack(spacing: 10) {
                WizardPrimaryButton(title: "Share reports", enabled: true, action: onShare)
                WizardPrimaryButton(title: "Not now", enabled: true, action: onDecline)
            }

            Button(action: onReadFAQ) {
                Text(L("Read the full FAQ"))
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.primary)
            }
            .buttonStyle(WizardTextButtonStyle())
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .frame(maxWidth: Metrics.cardMaxWidth, alignment: .leading)
        .padding(Metrics.cardPadding)
        .background {
            RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                .fill(colors.background)
                .overlay(
                    RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                        .stroke(colors.onSurface.opacity(colors.isDark ? 0.12 : 0.08), lineWidth: 1)
                )
        }
        .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 14, x: 0, y: 10)
        // The card is modal: VoiceOver should not wander off into the screen it covers.
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onDefer)
    }
}

/// Everything the person is told before they answer — and nothing else: no card around it, no
/// buttons under it.
///
/// Its own view because two places ask the same question now: the wizard's last step, which draws
/// it inside the wizard's card, and the standalone card above, which draws it with its own. They
/// have to read word for word the same, and one view is the only way that stays true — the copy and
/// the two lists live here, once.
struct TelemetryConsentDisclosure: View {
    @Environment(\.tdayColors) private var colors

    private typealias Metrics = OnboardingWizardOverlay.Metrics

    var body: some View {
        VStack(alignment: .leading, spacing: Metrics.sectionSpacing) {
            HStack(spacing: 12) {
                Image("LucideActivity")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(colors.primary)
                    .frame(width: 42, height: 42)
                    .background(colors.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityHidden(true)

                Text(L("Help fix crashes?"))
                    .font(.tdayRounded(size: 22, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Text(L("T'Day can send a short technical report only when something goes wrong, such as a crash, a freeze or an unexpected error. It helps the developer reproduce the problem on a similar device."))
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.72))
                .fixedSize(horizontal: false, vertical: true)

            detail(
                label: "What's included",
                text: "App version, device model, OS version, what failed and where."
            )
            detail(
                label: "Never included",
                text: "Your name or account, IP address, location, server address, or any task or list content."
            )

            Text(L("Off by default. Change it any time in Settings → Privacy."))
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func detail(label: String, text: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L(label))
                .font(.tdayRounded(size: 13, weight: .heavy))
                .foregroundStyle(colors.onSurface)

            Text(L(text))
                .font(.tdayRounded(size: 14, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
