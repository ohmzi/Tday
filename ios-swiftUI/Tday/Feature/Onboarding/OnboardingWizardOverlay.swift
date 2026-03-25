import SwiftUI

enum OnboardingStep {
    case server
    case login
}

struct OnboardingWizardOverlay: View {
    let initialServerURL: String?
    let serverErrorMessage: String?
    let serverCanResetTrust: Bool
    let pendingApprovalMessage: String?
    let authViewModel: AuthViewModel
    let onConnectServer: (String) async -> Result<Void, String>
    let onResetServerTrust: (String) async -> Result<Void, String>
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
            Color.black.opacity(0.3).ignoresSafeArea()

            VStack(spacing: 20) {
                VStack(spacing: 8) {
                    Image(systemName: "checklist")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(colors.primary)
                    Text("Tday for iPhone")
                        .font(.title.bold())
                        .foregroundStyle(colors.onSurface)
                    Text(step == .server ? "Connect to your Tday server" : (isCreatingAccount ? "Create your account" : "Sign in"))
                        .font(.subheadline)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                VStack(spacing: 14) {
                    if step == .server {
                        TextField("https://app.example.com", text: $serverURL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(14)
                            .background(colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                        Button(isConnecting ? "Connecting..." : "Connect") {
                            Task {
                                await connectServer()
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(serverURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isConnecting)

                        if serverCanResetTrust {
                            Button("Reset saved server trust") {
                                Task {
                                    await resetServerTrust()
                                }
                            }
                            .buttonStyle(.bordered)
                        }
                    } else {
                        if isCreatingAccount {
                            TextField("First name", text: $firstName)
                                .padding(14)
                                .background(colors.surfaceVariant)
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }

                        TextField("Email", text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                            .autocorrectionDisabled()
                            .padding(14)
                            .background(colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                        SecureField(isCreatingAccount ? "Password" : "Password", text: isCreatingAccount ? $registerPassword : $password)
                            .padding(14)
                            .background(colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                        if isCreatingAccount {
                            SecureField("Confirm password", text: $confirmPassword)
                                .padding(14)
                                .background(colors.surfaceVariant)
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }

                        Button(authViewModel.isLoading ? (isCreatingAccount ? "Creating..." : "Signing in...") : (isCreatingAccount ? "Create account" : "Sign in")) {
                            Task {
                                await submitAuth()
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(authViewModel.isLoading)

                        Button(isCreatingAccount ? "I already have an account" : "Create account") {
                            localError = nil
                            onClearAuthStatus()
                            isCreatingAccount.toggle()
                        }
                        .buttonStyle(.borderless)

                        Button("Change server") {
                            localError = nil
                            onClearAuthStatus()
                            step = .server
                        }
                        .font(.footnote.weight(.semibold))
                    }
                }

                if let message = localError ?? serverErrorMessage ?? authViewModel.errorMessage ?? authViewModel.infoMessage ?? pendingApprovalMessage {
                    Text(message)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle((localError ?? serverErrorMessage ?? authViewModel.errorMessage) == nil ? colors.primary : colors.error)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(24)
            .frame(maxWidth: 420)
            .background(colors.surface.opacity(0.98))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .shadow(color: .black.opacity(0.2), radius: 30, x: 0, y: 24)
            .padding(24)
        }
        .onAppear {
            serverURL = initialServerURL ?? ""
            email = authViewModel.savedEmail
            step = (initialServerURL?.isEmpty == false) ? .login : .server
        }
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
        case let .failure(message):
            localError = message
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
        case let .failure(message):
            localError = message
        }
    }

    private func submitAuth() async {
        localError = nil
        onClearAuthStatus()
        if isCreatingAccount {
            guard validateRegistration() else {
                return
            }
            _ = await onRegister(firstName.trimmingCharacters(in: .whitespacesAndNewlines), email.trimmingCharacters(in: .whitespacesAndNewlines), registerPassword)
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
