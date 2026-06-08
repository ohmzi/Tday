import SwiftUI

/// Self-service password reset. Mirrors the web app's ForgotPasswordFlow:
///   1. enter username -> fetch the two security questions
///   2. answer both questions + choose a new password -> reset
/// On `reset_locked` the user can request a reset from an administrator.
struct ForgotPasswordView: View {
    enum Step: Equatable {
        case username
        case challenge
        case password
        case success
        case locked
        case requested
    }

    let authViewModel: AuthViewModel
    let initialUsername: String
    let onDismiss: () -> Void
    /// Called after a successful reset; passes the normalized username so the
    /// caller can prefill the sign-in field.
    let onResetComplete: (String) -> Void
    /// When true the content is rendered bare (no card/scrim) so it can be dropped into
    /// the login wizard's own card — reusing the login dialog, like the create-account
    /// panel does. Standalone (Settings) usage keeps its own card.
    var embedded: Bool = false

    @Environment(\.tdayColors) private var colors

    @State private var step: Step = .username
    @State private var username = ""
    @State private var questions: [SecurityQuestion] = []
    @State private var shown: [SecurityQuestion] = []
    @State private var verifiedAnswers: [SecurityAnswerInput] = []
    @State private var answer1 = ""
    @State private var answer2 = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var errorMessage: String?
    @State private var infoMessage: String?
    @State private var isBusy = false
    @State private var failedAttempts = 0
    @State private var didComplete = false

    var body: some View {
        Group {
            if embedded {
                // Dropped straight into the login wizard's card — no own chrome.
                content
            } else {
                ZStack {
                    Color.black.opacity(0.18).ignoresSafeArea()
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 0) {
                            Spacer(minLength: 18)
                            card
                            Spacer(minLength: 18)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 18)
                    }
                    .scrollBounceBehavior(.basedOnSize)
                }
            }
        }
        .onAppear {
            if username.isEmpty {
                username = initialUsername
            }
        }
    }

    // The reset content, designed to live inside a card — either the login wizard's
    // (embedded) or this view's own card (standalone, from Settings).
    private var content: some View {
        VStack(alignment: .leading, spacing: 16) {
            header

            switch step {
            case .username:
                usernameStep
            case .challenge:
                challengeStep
            case .password:
                passwordStep
            case .success:
                successStep
            case .locked:
                lockedStep
            case .requested:
                requestedStep
            }

            if let message = errorMessage {
                Text(message)
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(colors.error)
                    .fixedSize(horizontal: false, vertical: true)
            } else if let infoMessage {
                Text(infoMessage)
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(colors.tertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if step != .requested && step != .success {
                Button {
                    onDismiss()
                } label: {
                    Text(L("Back to sign in"))
                        .font(.tdayRounded(size: 14, weight: .heavy))
                        .foregroundStyle(colors.primary)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: step)
    }

    private var card: some View {
        content
            .frame(maxWidth: 430, alignment: .leading)
            .padding(18)
            .background {
                RoundedRectangle(cornerRadius: 34, style: .continuous)
                    .fill(colors.background)
                    .overlay(
                        RoundedRectangle(cornerRadius: 34, style: .continuous)
                            .fill(Color.white.opacity(colors.isDark ? 0.035 : 0.34))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 34, style: .continuous)
                            .stroke(colors.onSurface.opacity(colors.isDark ? 0.12 : 0.08), lineWidth: 1)
                    )
            }
            .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 14, x: 0, y: 10)
    }

    // Same red hero tile the Sign in panel uses, so the reset flow reads as the same dialog.
    private var header: some View {
        ForgotPasswordHeroTile(
            title: "Reset your password",
            systemImage: "lock.shield.fill",
            tint: Color(red: 0.79, green: 0.47, blue: 0.50)
        )
    }

    private var usernameStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForgotPasswordField(
                title: "Username",
                text: $username,
                textContentType: .username,
                autocapitalization: .never,
                disableAutocorrection: true,
                submitLabel: .go,
                onSubmit: { Task { await lookupQuestions() } }
            )

            ForgotPasswordPrimaryButton(
                title: isBusy ? "Loading..." : "Continue",
                enabled: !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isBusy
            ) {
                Task { await lookupQuestions() }
            }
        }
    }

    // Step 2: answer the two security questions only. Verified before the password step.
    private var challengeStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            if shown.indices.contains(0) {
                Text(shown[0].text)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                ForgotPasswordField(
                    title: "Answer",
                    text: $answer1,
                    autocapitalization: .never,
                    disableAutocorrection: true,
                    submitLabel: .next
                )
            }

            if shown.indices.contains(1) {
                Text(shown[1].text)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                ForgotPasswordField(
                    title: "Answer",
                    text: $answer2,
                    autocapitalization: .never,
                    disableAutocorrection: true,
                    submitLabel: .done,
                    onSubmit: { Task { await verifyChallenge() } }
                )
            }

            ForgotPasswordPrimaryButton(
                title: isBusy ? "Checking..." : "Verify answers",
                enabled: challengeFilled && !isBusy
            ) {
                Task { await verifyChallenge() }
            }
        }
    }

    // Step 3: only reachable once the answers verify.
    private var passwordStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForgotPasswordField(
                title: "New password",
                text: $newPassword,
                isSecure: true,
                textContentType: .newPassword,
                submitLabel: .next
            )

            ForgotPasswordField(
                title: "Confirm new password",
                text: $confirmPassword,
                isSecure: true,
                textContentType: .newPassword,
                submitLabel: .done,
                onSubmit: { Task { await submitReset() } }
            )

            ForgotPasswordPrimaryButton(
                title: isBusy ? "Resetting..." : "Reset password",
                enabled: passwordFilled && !isBusy
            ) {
                Task { await submitReset() }
            }
        }
    }

    private var lockedStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L("Too many incorrect attempts. You can request a password reset from an administrator."))
                .font(.tdayRounded(size: 14, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)

            ForgotPasswordPrimaryButton(
                title: isBusy ? "Sending..." : "Request reset from an administrator",
                enabled: !isBusy,
                tint: colors.error
            ) {
                Task { await requestAdminReset() }
            }
        }
    }

    private var requestedStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(L("Your request has been sent. An administrator will reset your password and share a temporary one with you."))
                .font(.tdayRounded(size: 14, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)

            ForgotPasswordPrimaryButton(
                title: "Back to sign in",
                enabled: true
            ) {
                onDismiss()
            }
        }
    }

    // Step 4: confirmation. Auto-returns to sign in after a short pause; OK skips the wait.
    private var successStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(colors.primary)
                Text(L("Password changed"))
                    .font(.tdayRounded(size: 16, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
            }

            ForgotPasswordPrimaryButton(title: "OK", enabled: true) {
                completeReset()
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            completeReset()
        }
    }

    // Fires the reset-complete callback exactly once, whether by the 2s timer or the OK button.
    private func completeReset() {
        guard !didComplete else { return }
        didComplete = true
        onResetComplete(username)
    }

    private var challengeFilled: Bool {
        !answer1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !answer2.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var passwordFilled: Bool {
        !newPassword.isEmpty && !confirmPassword.isEmpty
    }

    private func lookupQuestions() async {
        errorMessage = nil
        infoMessage = nil
        let normalized = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalized.isEmpty else {
            errorMessage = "Please enter your username"
            return
        }
        isBusy = true
        defer { isBusy = false }
        switch await authViewModel.lookupQuestions(normalized) {
        case let .found(fetched):
            guard fetched.count >= 2 else {
                errorMessage = "Security questions are unavailable for this account."
                return
            }
            questions = fetched
            shown = Array(fetched.prefix(2))
            answer1 = ""
            answer2 = ""
            username = normalized
            step = .challenge
        case .notFound:
            errorMessage = "We couldn't find an account with that username."
        case let .error(message):
            errorMessage = message.isEmpty ? "Unable to load security questions." : message
        }
    }

    private func verifyChallenge() async {
        errorMessage = nil
        guard shown.count >= 2 else { return }
        isBusy = true
        defer { isBusy = false }
        let answers = [
            SecurityAnswerInput(questionId: shown[0].id, answer: answer1.trimmingCharacters(in: .whitespacesAndNewlines)),
            SecurityAnswerInput(questionId: shown[1].id, answer: answer2.trimmingCharacters(in: .whitespacesAndNewlines)),
        ]
        switch await authViewModel.verifyAnswers(username: username, answers: answers) {
        case .valid:
            verifiedAnswers = answers
            newPassword = ""
            confirmPassword = ""
            step = .password
        case let .invalid(results):
            let wrongIds = Set(results.filter { !$0.correct }.map { $0.questionId })
            cycleFailedQuestions(wrongIds: wrongIds.isEmpty ? Set(shown.map { $0.id }) : wrongIds)
            failedAttempts += 1
            errorMessage = failedAttempts > 2
                ? "Those answers didn't match. Please contact an administrator to reset your password."
                : "Those answers didn't match. Please try again."
        case .locked:
            step = .locked
        case let .error(message):
            errorMessage = message.isEmpty ? "Unable to verify your answers." : message
        }
    }

    // Swap each wrongly-answered question for a random not-yet-shown one (cycling the 3rd
    // question in). With only two stored questions there's nothing to swap.
    private func cycleFailedQuestions(wrongIds: Set<Int>) {
        var pool = questions.filter { q in !shown.contains(where: { $0.id == q.id }) }
        answer1 = ""
        answer2 = ""
        guard !pool.isEmpty else { return }
        shown = shown.map { question in
            if wrongIds.contains(question.id), let idx = pool.indices.randomElement() {
                return pool.remove(at: idx)
            }
            return question
        }
    }

    private func submitReset() async {
        errorMessage = nil
        infoMessage = nil
        guard newPassword.count >= 8 else {
            errorMessage = "Password must be at least 8 characters"
            return
        }
        guard newPassword.contains(where: \.isUppercase) else {
            errorMessage = "Password must include at least one uppercase letter"
            return
        }
        guard newPassword.contains(where: { !$0.isLetter && !$0.isNumber }) else {
            errorMessage = "Password must include at least one special character"
            return
        }
        guard newPassword == confirmPassword else {
            errorMessage = "Passwords do not match"
            return
        }

        isBusy = true
        defer { isBusy = false }

        let result = await authViewModel.resetPassword(
            username: username,
            answers: verifiedAnswers,
            newPassword: newPassword
        )
        switch result {
        case .success:
            step = .success
        case .locked:
            step = .locked
        case .failed:
            // Answers no longer line up (e.g. cycled mid-flight) — back to the questions.
            step = .challenge
            answer1 = ""
            answer2 = ""
            errorMessage = "Please re-enter your security answers."
        case let .error(message):
            errorMessage = message.isEmpty ? "Unable to reset password. Please try again." : message
        }
    }

    private func requestAdminReset() async {
        errorMessage = nil
        infoMessage = nil
        isBusy = true
        defer { isBusy = false }
        let didRequest = await authViewModel.requestAdminReset(username)
        if didRequest {
            step = .requested
        } else {
            errorMessage = "Unable to send request. Please try again."
        }
    }
}

// Mirrors the login wizard's WizardHeroTile so the reset flow shares the same dialog look.
private struct ForgotPasswordHeroTile: View {
    let title: String
    let systemImage: String
    let tint: Color

    var body: some View {
        ZStack(alignment: .trailing) {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(tint)
                .overlay(
                    RadialGradient(
                        colors: [Color.white.opacity(0.24), Color.white.opacity(0.08), .clear],
                        center: UnitPoint(x: 0.18, y: 0.18),
                        startRadius: 0,
                        endRadius: 210
                    )
                )

            Image(systemName: systemImage)
                .font(.system(size: 82, weight: .regular))
                .foregroundStyle(Color.white.opacity(0.2))
                .offset(x: 20, y: 12)

            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 23, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 42, height: 42)
                    .background(Color.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                Text(L(title))
                    .font(.tdayRounded(size: 21, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 78)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .shadow(color: tint.opacity(0.16), radius: 9, x: 0, y: 7)
    }
}

private struct ForgotPasswordField: View {
    let title: String
    @Binding var text: String
    var isSecure = false
    var textContentType: UITextContentType?
    var autocapitalization: TextInputAutocapitalization? = .sentences
    var disableAutocorrection = false
    var submitLabel: SubmitLabel = .done
    var onSubmit: (() -> Void)? = nil

    @Environment(\.tdayColors) private var colors
    @FocusState private var isFocused: Bool

    var body: some View {
        Group {
            if isSecure {
                SecureField(
                    "",
                    text: $text,
                    prompt: Text(L(title)).foregroundStyle(colors.onSurface.opacity(0.42))
                )
                .textContentType(textContentType)
            } else {
                TextField(
                    "",
                    text: $text,
                    prompt: Text(L(title)).foregroundStyle(colors.onSurface.opacity(0.42))
                )
                .textContentType(textContentType)
                .textInputAutocapitalization(autocapitalization)
                .keyboardType(.default)
                .autocorrectionDisabled(disableAutocorrection)
            }
        }
        .focused($isFocused)
        .submitLabel(submitLabel)
        .onSubmit { onSubmit?() }
        .font(.tdayRounded(size: 15, weight: .bold))
        .foregroundStyle(colors.onSurface)
        .tint(colors.primary)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 54)
        .background {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(colors.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(
                            isFocused ? colors.primary.opacity(0.82) : colors.onSurface.opacity(0.14),
                            lineWidth: isFocused ? 1.1 : 1
                        )
                )
        }
        .accessibilityLabel(L(title))
    }
}

private struct ForgotPasswordPrimaryButton: View {
    let title: String
    let enabled: Bool
    var tint: Color?
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Text(L(title))
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(enabled ? colors.onPrimary : colors.onSurfaceVariant.opacity(0.65))
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background {
                    Capsule(style: .continuous)
                        .fill(enabled ? (tint ?? colors.primary) : colors.surfaceVariant.opacity(0.95))
                }
        }
        .buttonStyle(.plain)
        .opacity(enabled ? 1 : 0.72)
        .disabled(!enabled)
    }
}
