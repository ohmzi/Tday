import SwiftUI

enum OnboardingStep {
    case server
    case login
}

struct OnboardingWizardOverlay: View {
    fileprivate enum Metrics {
        static let overlayPadding: CGFloat = 18
        static let cardMaxWidth: CGFloat = 460
        static let cardCornerRadius: CGFloat = 32
        static let cardPadding: CGFloat = 18
        static let sectionSpacing: CGFloat = 12
        static let chipSpacing: CGFloat = 10
        static let chipCornerRadius: CGFloat = 20
        static let inputHeight: CGFloat = 56
        static let inputCornerRadius: CGFloat = 8
        static let buttonHeight: CGFloat = 40
        static let watermarkSize: CGFloat = 130
    }

    let initialServerURL: String?
    let serverErrorMessage: String?
    let serverCanResetTrust: Bool
    let pendingApprovalMessage: String?
    let authViewModel: AuthViewModel
    let onConnectServer: (String) async -> Result<Void, MessageError>
    let onResetServerTrust: (String) async -> Result<Void, MessageError>
    let onLogin: (String, String) async -> Bool
    let onRegister: (String, String, String) async -> Bool
    let onClearAuthStatus: () -> Void

    @Environment(\.tdayColors) private var colors
    @State private var step: OnboardingStep = .server
    @State private var serverURL = ""
    @State private var email = ""
    @State private var password = ""
    @State private var firstName = ""
    @State private var registerPassword = ""
    @State private var confirmPassword = ""
    @State private var isCreatingAccount = false
    @State private var localError: String?
    @State private var isConnecting = false

    var body: some View {
        ZStack {
            Color.clear
                .ignoresSafeArea()
                .overlay(
                    LinearGradient(
                        colors: [
                            Color.black.opacity(0.04),
                            Color.black.opacity(0.08),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea()
                )

            ViewThatFits(in: .vertical) {
                centeredCardLayout

                ScrollView(showsIndicators: false) {
                    VStack {
                        wizardCard
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, Metrics.overlayPadding)
                    .padding(.vertical, Metrics.overlayPadding)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
        }
        .onAppear {
            serverURL = initialServerURL ?? ""
            email = authViewModel.savedEmail
            step = (initialServerURL?.isEmpty == false) ? .login : .server
        }
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: step)
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: isCreatingAccount)
        .animation(.easeInOut(duration: 0.2), value: isConnecting)
        .animation(.easeInOut(duration: 0.2), value: authViewModel.isLoading)
    }

    private var centeredCardLayout: some View {
        VStack {
            Spacer(minLength: Metrics.overlayPadding)
            wizardCard
            Spacer(minLength: Metrics.overlayPadding)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, Metrics.overlayPadding)
        .padding(.vertical, Metrics.overlayPadding)
    }

    private var wizardCard: some View {
        VStack(alignment: .leading, spacing: Metrics.sectionSpacing) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Set Up T'Day")
                    .font(.system(size: 23, weight: .bold))
                    .foregroundStyle(colors.onSurface)

                Text("Secure onboarding wizard")
                    .font(.system(size: 12, weight: .regular))
                    .foregroundStyle(colors.onSurface.opacity(0.6))
            }

            HStack(spacing: Metrics.chipSpacing) {
                WizardStepChip(
                    title: "Server",
                    systemImage: "globe",
                    tint: Color(red: 0.43, green: 0.66, blue: 0.88),
                    active: step == .server
                )

                WizardStepChip(
                    title: "Login",
                    systemImage: "person.fill",
                    tint: Color(red: 0.83, green: 0.54, blue: 0.55),
                    active: isLoginStep
                )
            }

            Group {
                if isConnecting {
                    WizardLoadingPanel(
                        systemImage: "globe.americas.fill",
                        title: "Connecting to server",
                        subtitle: "Validating your secure T'Day endpoint."
                    )
                } else if authViewModel.isLoading {
                    WizardLoadingPanel(
                        systemImage: isCreatingAccount ? "person.badge.plus.fill" : "lock.fill",
                        title: isCreatingAccount ? "Creating account" : "Authenticating",
                        subtitle: isCreatingAccount
                            ? "Preparing your new T'Day account."
                            : "Completing encrypted sign in."
                    )
                } else if step == .server {
                    serverStepContent
                } else {
                    loginStepContent
                }
            }
        }
        .frame(maxWidth: Metrics.cardMaxWidth, alignment: .leading)
        .padding(Metrics.cardPadding)
        .background {
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                    .fill(colors.surface.opacity(1))
                    .overlay(
                        RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                            .fill(Color.white.opacity(0.18))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                            .stroke(Color.white.opacity(0.88), lineWidth: 1)
                    )

                LinearGradient(
                    colors: [
                        Color.white.opacity(0.2),
                        colors.onSurface.opacity(0.025),
                        .clear,
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .clipShape(RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous))

                Image(systemName: step == .server ? "globe.americas.fill" : "lock.fill")
                    .font(.system(size: Metrics.watermarkSize, weight: .regular))
                    .foregroundStyle(colors.primary.opacity(0.18))
                    .padding(.trailing, 18)
                    .padding(.bottom, isLoginStep ? 18 : 10)
            }
        }
        .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 8)
    }

    private var serverStepContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            WizardInputField(
                title: "Server URL",
                text: $serverURL,
                keyboardType: .URL,
                autocapitalization: .never,
                disableAutocorrection: true,
                submitLabel: .go,
                onSubmit: {
                    Task { await connectServer() }
                }
            )

            if let message = currentMessage {
                Text(message)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(currentMessageColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if serverCanResetTrust, currentMessageIsError {
                Button(isConnecting ? "Resetting..." : "Reset saved server trust") {
                    Task {
                        await resetServerTrust()
                    }
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(colors.primary)
            }

            WizardPrimaryButton(
                title: isConnecting ? "Connecting..." : "Connect",
                enabled: !serverURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isConnecting
            ) {
                Task {
                    await connectServer()
                }
            }
        }
    }

    private var loginStepContent: some View {
        VStack(alignment: .leading, spacing: 11) {
            if isCreatingAccount {
                WizardInputField(title: "First name", text: $firstName, autocapitalization: .words, submitLabel: .next)
            }

            WizardInputField(
                title: "Email",
                text: $email,
                keyboardType: .emailAddress,
                autocapitalization: .never,
                disableAutocorrection: true,
                submitLabel: .next
            )

            Group {
                if isCreatingAccount {
                    WizardInputField(
                        title: "Password",
                        text: $registerPassword,
                        isSecure: true,
                        submitLabel: .next
                    )
                } else {
                    WizardInputField(
                        title: "Password",
                        text: $password,
                        isSecure: true,
                        submitLabel: .done,
                        onSubmit: {
                            Task { await submitAuth() }
                        }
                    )
                }
            }

            if isCreatingAccount {
                WizardInputField(
                    title: "Confirm password",
                    text: $confirmPassword,
                    isSecure: true,
                    submitLabel: .done,
                    onSubmit: {
                        Task { await submitAuth() }
                    }
                )
            }

            if let message = currentMessage {
                Text(message)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(currentMessageColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            WizardPrimaryButton(
                title: isCreatingAccount ? "Create account" : "Sign in",
                enabled: primaryAuthActionEnabled
            ) {
                Task {
                    await submitAuth()
                }
            }

            HStack(alignment: .center) {
                Button(isCreatingAccount ? "I already have an account" : "Create account") {
                    localError = nil
                    onClearAuthStatus()
                    isCreatingAccount.toggle()
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(colors.primary)

                Spacer(minLength: 16)

                Button("Change server") {
                    localError = nil
                    onClearAuthStatus()
                    isCreatingAccount = false
                    step = .server
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(colors.primary)
            }
            .padding(.top, 6)
        }
    }

    private var isLoginStep: Bool {
        step == .login
    }

    private var currentMessage: String? {
        localError ?? serverErrorMessage ?? authViewModel.errorMessage ?? authViewModel.infoMessage ?? pendingApprovalMessage
    }

    private var currentMessageColor: Color {
        currentMessageIsError ? colors.error : colors.tertiary
    }

    private var currentMessageIsError: Bool {
        (localError ?? serverErrorMessage ?? authViewModel.errorMessage) != nil
    }

    private var primaryAuthActionEnabled: Bool {
        if isCreatingAccount {
            return !firstName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
                !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
                !registerPassword.isEmpty &&
                !confirmPassword.isEmpty &&
                !authViewModel.isLoading
        }

        return !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !password.isEmpty &&
            !authViewModel.isLoading
    }

    private func connectServer() async {
        localError = nil
        onClearAuthStatus()
        isConnecting = true
        let result = await onConnectServer(serverURL)
        isConnecting = false
        switch result {
        case .success:
            step = .login
        case let .failure(error):
            localError = error.message
        }
    }

    private func resetServerTrust() async {
        localError = nil
        onClearAuthStatus()
        isConnecting = true
        let result = await onResetServerTrust(serverURL)
        isConnecting = false
        switch result {
        case .success:
            step = .login
        case let .failure(error):
            localError = error.message
        }
    }

    private func submitAuth() async {
        localError = nil
        onClearAuthStatus()
        if isCreatingAccount {
            guard validateRegistration() else {
                return
            }
            _ = await onRegister(
                firstName.trimmingCharacters(in: .whitespacesAndNewlines),
                email.trimmingCharacters(in: .whitespacesAndNewlines),
                registerPassword
            )
        } else {
            guard !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !password.isEmpty else {
                localError = "Email and password are required"
                return
            }
            _ = await onLogin(email.trimmingCharacters(in: .whitespacesAndNewlines), password)
        }
    }

    private func validateRegistration() -> Bool {
        let normalizedFirstName = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalizedFirstName.count >= 2 else {
            localError = "First name must be at least 2 characters"
            return false
        }
        guard normalizedEmail.contains("@") else {
            localError = "Please enter a valid email address"
            return false
        }
        guard registerPassword.count >= 8 else {
            localError = "Password must be at least 8 characters"
            return false
        }
        guard registerPassword.contains(where: \.isUppercase) else {
            localError = "Password must include at least one uppercase letter"
            return false
        }
        guard registerPassword.contains(where: { !$0.isLetter && !$0.isNumber }) else {
            localError = "Password must include at least one special character"
            return false
        }
        guard registerPassword == confirmPassword else {
            localError = "Passwords do not match"
            return false
        }
        return true
    }
}

private struct WizardStepChip: View {
    let title: String
    let systemImage: String
    let tint: Color
    let active: Bool

    var body: some View {
        let ringColor = Color(
            red: min(1, tint.components.red * 0.75),
            green: min(1, tint.components.green * 0.75),
            blue: min(1, tint.components.blue * 0.75)
        )

        HStack(spacing: 8) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .bold))

            Text(title)
                .font(.system(size: 14, weight: .bold))
                .lineLimit(1)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.chipCornerRadius, style: .continuous)
                .fill(tint)
        )
        .overlay(
            RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.chipCornerRadius, style: .continuous)
                .stroke(active ? ringColor.opacity(0.85) : .clear, lineWidth: 2)
        )
        .scaleEffect(active ? 1.04 : 1)
        .shadow(color: tint.opacity(active ? 0.22 : 0.14), radius: active ? 10 : 8, x: 0, y: 6)
    }
}

private struct WizardInputField: View {
    let title: String
    @Binding var text: String
    var isSecure = false
    var keyboardType: UIKeyboardType = .default
    var autocapitalization: TextInputAutocapitalization? = .sentences
    var disableAutocorrection = false
    var submitLabel: SubmitLabel = .done
    var onSubmit: (() -> Void)? = nil

    @Environment(\.tdayColors) private var colors
    @FocusState private var isFocused: Bool

    var body: some View {
        inputControl
            .focused($isFocused)
            .submitLabel(submitLabel)
            .onSubmit {
                onSubmit?()
            }
            .font(.system(size: 15, weight: .regular))
            .foregroundStyle(colors.onSurface)
            .tint(colors.primary)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: OnboardingWizardOverlay.Metrics.inputHeight)
            .background {
                RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.inputCornerRadius, style: .continuous)
                    .fill(colors.surface.opacity(0.9))
                    .overlay(
                        RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.inputCornerRadius, style: .continuous)
                            .stroke(
                                colors.onSurface.opacity(isFocused ? 0.92 : 0.3),
                                lineWidth: isFocused ? 1.1 : 1
                            )
                    )
            }
            .accessibilityLabel(title)
            .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var inputControl: some View {
        if isSecure {
            SecureField(
                "",
                text: $text,
                prompt: Text(title).foregroundStyle(colors.onSurface.opacity(0.42))
            )
        } else {
            TextField(
                "",
                text: $text,
                prompt: Text(title).foregroundStyle(colors.onSurface.opacity(0.42))
            )
                .textInputAutocapitalization(autocapitalization)
                .keyboardType(keyboardType)
                .autocorrectionDisabled(disableAutocorrection)
        }
    }
}

private struct WizardPrimaryButton: View {
    let title: String
    let enabled: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(enabled ? colors.onPrimary : colors.onSurfaceVariant.opacity(0.65))
                .frame(maxWidth: .infinity)
                .frame(height: OnboardingWizardOverlay.Metrics.buttonHeight)
                .background(
                    Capsule(style: .continuous)
                        .fill(enabled ? colors.primary : colors.surfaceVariant.opacity(0.95))
                )
        }
        .buttonStyle(WizardPressButtonStyle())
        .shadow(color: enabled ? colors.primary.opacity(0.16) : .clear, radius: 12, x: 0, y: 8)
        .opacity(enabled ? 1 : 0.72)
        .disabled(!enabled)
    }
}

private struct WizardPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayRippleEffect(isPressed: configuration.isPressed)
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .brightness(configuration.isPressed ? -0.02 : 0)
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
}

private struct WizardTextButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .tdayRippleEffect(isPressed: configuration.isPressed)
            .opacity(configuration.isPressed ? 0.62 : 1)
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

private struct WizardLoadingPanel: View {
    let systemImage: String
    let title: String
    let subtitle: String

    @Environment(\.tdayColors) private var colors

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(colors.primary)

            ProgressView()
                .tint(colors.primary)

            Text(title)
                .font(.system(size: 22, weight: .semibold, design: .rounded))
                .foregroundStyle(colors.onSurface)

            Text(subtitle)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(colors.onSurface.opacity(0.62))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 22)
    }
}

private extension Color {
    var components: (red: Double, green: Double, blue: Double) {
        let uiColor = UIColor(self)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return (Double(red), Double(green), Double(blue))
    }
}
