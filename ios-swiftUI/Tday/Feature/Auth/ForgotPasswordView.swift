import SwiftUI

/// Self-service password reset. Mirrors the web app's ForgotPasswordFlow:
///   1. enter username -> fetch the two security questions
///   2. answer both questions + choose a new password -> reset
/// On `reset_locked` the user can request a reset from an administrator.
struct ForgotPasswordView: View {
    enum Step: Equatable {
        case username
        case challenge
        case locked
        case requested
    }

    let authViewModel: AuthViewModel
    let initialUsername: String
    let onDismiss: () -> Void
    /// Called after a successful reset; passes the normalized username so the
    /// caller can prefill the sign-in field.
    let onResetComplete: (String) -> Void

    @Environment(\.tdayColors) private var colors

    @State private var step: Step = .username
    @State private var username = ""
    @State private var questions: [SecurityQuestion] = []
    @State private var answer1 = ""
    @State private var answer2 = ""
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var errorMessage: String?
    @State private var infoMessage: String?
    @State private var isBusy = false
    @State private var failedAttempts = 0

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    header

                    switch step {
                    case .username:
                        usernameStep
                    case .challenge:
                        challengeStep
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
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(colors.background.ignoresSafeArea())
            .navigationTitle(Text(L("Reset password")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("Back to sign in")) {
                        onDismiss()
                    }
                }
            }
        }
        .onAppear {
            if username.isEmpty {
                username = initialUsername
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(colors.primary)
                Text(L("Reset your password"))
                    .font(.tdayRounded(size: 20, weight: .heavy))
                    .foregroundStyle(colors.onSurface)
            }

            if let stepSubtitle {
                Text(L(stepSubtitle))
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.62))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var stepSubtitle: String? {
        switch step {
        case .username:
            return nil
        case .challenge:
            return nil
        case .locked:
            return "Too many incorrect attempts. You can request a password reset from an administrator."
        case .requested:
            return "Your request has been sent. An administrator will reset your password and share a temporary one with you."
        }
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

    private var challengeStep: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let first = questions.first {
                Text(first.text)
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

            if questions.count > 1 {
                Text(questions[1].text)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(colors.onSurface)
                ForgotPasswordField(
                    title: "Answer",
                    text: $answer2,
                    autocapitalization: .never,
                    disableAutocorrection: true,
                    submitLabel: .next
                )
            }

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
                enabled: challengeFieldsFilled && !isBusy
            ) {
                Task { await submitReset() }
            }
        }
    }

    private var lockedStep: some View {
        ForgotPasswordPrimaryButton(
            title: isBusy ? "Sending..." : "Request reset from an administrator",
            enabled: !isBusy,
            tint: colors.error
        ) {
            Task { await requestAdminReset() }
        }
    }

    private var requestedStep: some View {
        ForgotPasswordPrimaryButton(
            title: "Back to sign in",
            enabled: true
        ) {
            onDismiss()
        }
    }

    private var challengeFieldsFilled: Bool {
        !answer1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !answer2.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !newPassword.isEmpty &&
            !confirmPassword.isEmpty
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
        guard let fetched = await authViewModel.fetchQuestionsForUsername(normalized) else {
            errorMessage = "Unable to load security questions."
            return
        }
        guard fetched.count >= 2 else {
            errorMessage = "Security questions are unavailable for this account."
            return
        }
        questions = fetched
        username = normalized
        step = .challenge
    }

    private func submitReset() async {
        errorMessage = nil
        infoMessage = nil
        guard questions.count >= 2 else {
            errorMessage = "Security questions are unavailable for this account."
            return
        }
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

        let answers = [
            SecurityAnswerInput(questionId: questions[0].id, answer: answer1.trimmingCharacters(in: .whitespacesAndNewlines)),
            SecurityAnswerInput(questionId: questions[1].id, answer: answer2.trimmingCharacters(in: .whitespacesAndNewlines)),
        ]

        let result = await authViewModel.resetPassword(
            username: username,
            answers: answers,
            newPassword: newPassword
        )
        switch result {
        case .success:
            onResetComplete(username)
        case .locked:
            step = .locked
        case let .failed(message):
            failedAttempts += 1
            let base = message.isEmpty ? "Those answers didn't match. Please try again." : message
            // After more than two failed attempts, point the user to an admin reset.
            errorMessage = failedAttempts > 2
                ? "\(base) Please contact an administrator to reset your password."
                : base
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
