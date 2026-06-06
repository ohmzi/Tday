import SwiftUI
import UIKit

private func isOnboardingDaytime(_ date: Date) -> Bool {
    let hour = Calendar.current.component(.hour, from: date)
    return (6..<18).contains(hour)
}

enum OnboardingStep: Equatable {
    case mode
    case server
    case login
}

struct OnboardingWizardOverlay: View {
    fileprivate enum Metrics {
        static let overlayPadding: CGFloat = 18
        static let cardMaxWidth: CGFloat = 430
        static let cardCornerRadius: CGFloat = 34
        static let cardPadding: CGFloat = 18
        static let sectionSpacing: CGFloat = 14
        static let chipSpacing: CGFloat = 8
        static let chipCornerRadius: CGFloat = 18
        static let inputHeight: CGFloat = 54
        static let inputCornerRadius: CGFloat = 22
        static let buttonHeight: CGFloat = 48
        static let tileCornerRadius: CGFloat = 26
        static let tileHeight: CGFloat = 116
        static let heroHeight: CGFloat = 78
        static let watermarkSize: CGFloat = 130
    }

    let initialServerURL: String?
    let serverErrorMessage: String?
    let serverCanResetTrust: Bool
    let pendingApprovalMessage: String?
    let authViewModel: AuthViewModel
    let systemCredentialService: SystemCredentialServicing
    let onConnectServer: (String) async -> Result<Void, MessageError>
    let onResetServerTrust: (String) async -> Result<Void, MessageError>
    let onLogin: (String, String, LoginCredentialSource) async -> Bool
    let onRegister: (String, String, String, [SecurityAnswerInput]) async -> Bool
    let onLoadSecurityQuestions: () async -> [SecurityQuestion]
    let onUseLocalMode: () async -> Void
    let onClearAuthStatus: () -> Void

    @Environment(\.tdayColors) private var colors
    @State private var step: OnboardingStep = .server
    @State private var serverURL = ""
    @State private var username = ""
    @State private var password = ""
    @State private var firstName = ""
    @State private var registerPassword = ""
    @State private var confirmPassword = ""
    @State private var isCreatingAccount = false
    @State private var isChoosingSecurityQuestions = false
    @State private var securityQuestions: [SecurityQuestion] = []
    @State private var isLoadingSecurityQuestions = false
    @State private var securityQuestionId1: Int?
    @State private var securityQuestionId2: Int?
    @State private var securityAnswer1 = ""
    @State private var securityAnswer2 = ""
    @State private var localError: String?
    @State private var isConnecting = false
    @State private var isCompletingAuthentication = false
    @State private var serverURLCameFromSystemCredential = false
    @State private var hasRequestedSavedServerURL = false
    @State private var pendingServerURLUsePrompt: String?
    @State private var pendingServerURLSavePrompt: String?
    @State private var isShowingForgotPassword = false
    @State private var credentialCoordinator = LoginCredentialCoordinator()

    private static let usernamePattern = "^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$"

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

            stableCardLayout
        }
        .onAppear {
            serverURL = initialServerURL ?? ""
            step = (initialServerURL?.isEmpty == false) ? .login : .mode
            if step == .login {
                requestSavedCredentialIfAvailable()
            } else if step == .server {
                requestSavedServerURLIfAvailable()
            }
        }
        .onChange(of: step) { _, newStep in
            if newStep == .login {
                requestSavedCredentialIfAvailable()
            } else if newStep == .server {
                requestSavedServerURLIfAvailable()
            }
        }
        .onChange(of: isCreatingAccount) { _, creatingAccount in
            if !creatingAccount {
                requestSavedCredentialIfAvailable()
            }
        }
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: step)
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: isCreatingAccount)
        .animation(.easeInOut(duration: 0.2), value: isConnecting)
        .animation(.easeInOut(duration: 0.2), value: authViewModel.isLoading)
        .animation(.easeInOut(duration: 0.2), value: isCompletingAuthentication)
        .alert("Save server URL?", isPresented: serverURLSavePromptBinding) {
            Button("Not Now", role: .cancel) {
                pendingServerURLSavePrompt = nil
                step = .login
            }
            Button("Save") {
                guard let serverURL = pendingServerURLSavePrompt else {
                    step = .login
                    return
                }
                pendingServerURLSavePrompt = nil
                Task {
                    _ = await systemCredentialService.offerSaveOrUpdateServerURL(serverURL)
                    step = .login
                }
            }
        } message: {
            Text("T'Day can save this server URL securely on this device so you can reuse it during setup.")
        }
        .alert("Use saved server URL?", isPresented: serverURLUsePromptBinding) {
            Button("Not Now", role: .cancel) {
                pendingServerURLUsePrompt = nil
            }
            Button("Use") {
                guard let savedServerURL = pendingServerURLUsePrompt else {
                    return
                }
                pendingServerURLUsePrompt = nil
                useSavedServerURL(savedServerURL)
            }
        } message: {
            Text("T'Day found a server URL saved on this device.")
        }
        .sheet(isPresented: $isShowingForgotPassword) {
            ForgotPasswordView(
                authViewModel: authViewModel,
                initialUsername: username.trimmingCharacters(in: .whitespacesAndNewlines),
                onDismiss: {
                    isShowingForgotPassword = false
                },
                onResetComplete: { resetUsername in
                    isShowingForgotPassword = false
                    username = resetUsername
                    password = ""
                    localError = nil
                    onClearAuthStatus()
                }
            )
        }
    }

    private var stableCardLayout: some View {
        GeometryReader { proxy in
            ScrollView(showsIndicators: false) {
                VStack {
                    Spacer(minLength: Metrics.overlayPadding)
                    wizardCard
                    Spacer(minLength: Metrics.overlayPadding)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: proxy.size.height)
                .padding(.horizontal, Metrics.overlayPadding)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.never)
        }
    }

    private var wizardCard: some View {
        VStack(alignment: .leading, spacing: Metrics.sectionSpacing) {
            HStack(spacing: 8) {
                Image(systemName: isOnboardingDaytime(Date()) ? "sun.max.fill" : "moon.stars.fill")
                    .font(.system(size: 25, weight: .regular))
                    .foregroundStyle(Color(red: 0.96, green: 0.77, blue: 0.26))

                Text("T'Day")
                    .font(.tdayRounded(size: 31, weight: .heavy))
                    .foregroundStyle(colors.onSurface)

                Spacer(minLength: 0)
            }

            HStack(spacing: Metrics.chipSpacing) {
                WizardStepChip(
                    title: "Mode",
                    systemImage: "iphone",
                    tint: Color(red: 0.5, green: 0.72, blue: 0.54),
                    active: step == .mode,
                    completed: step == .server || step == .login
                )

                WizardStepChip(
                    title: "Server",
                    systemImage: "globe",
                    tint: Color(red: 0.43, green: 0.66, blue: 0.88),
                    active: step == .server,
                    completed: step == .login
                )

                WizardStepChip(
                    title: "Login",
                    systemImage: "person.fill",
                    tint: Color(red: 0.83, green: 0.54, blue: 0.55),
                    active: isLoginStep
                )
            }

            Text("Set up your workspace")
                .font(.tdayRounded(size: 13, weight: .bold))
                .foregroundStyle(colors.onSurface.opacity(0.62))
                .padding(.top, -4)

            Group {
                if isConnecting {
                    WizardLoadingPanel(
                        systemImage: "globe.americas.fill",
                        title: "Connecting to server",
                        subtitle: "Validating your secure T'Day endpoint."
                    )
                } else if isAuthInFlight {
                    WizardLoadingPanel(
                        systemImage: isCreatingAccount ? "person.badge.plus.fill" : "lock.fill",
                        title: authLoadingTitle,
                        subtitle: authLoadingSubtitle
                    )
                } else if step == .mode {
                    modeStepContent
                } else if step == .server {
                    serverStepContent
                } else if isChoosingSecurityQuestions {
                    securityQuestionsStepContent
                } else {
                    loginStepContent
                }
            }
        }
        .frame(maxWidth: Metrics.cardMaxWidth, alignment: .leading)
        .padding(Metrics.cardPadding)
        .background {
            ZStack {
                RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                    .fill(colors.background)
                    .overlay(
                        RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                            .fill(Color.white.opacity(colors.isDark ? 0.035 : 0.34))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: Metrics.cardCornerRadius, style: .continuous)
                            .stroke(colors.onSurface.opacity(colors.isDark ? 0.12 : 0.08), lineWidth: 1)
                    )
            }
        }
        .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 14, x: 0, y: 10)
    }

    private var modeStepContent: some View {
        VStack(alignment: .leading, spacing: 11) {
            WizardHeroTile(
                title: "Choose your setup",
                subtitle: "Pick where T'Day keeps your tasks.",
                systemImage: "sparkles",
                tint: Color(red: 0.43, green: 0.66, blue: 0.88)
            )

            HStack(spacing: 10) {
                WizardModeChoiceButton(
                    title: "Self-hosted",
                    subtitle: "Accounts and sync",
                    systemImage: "globe",
                    tint: Color(red: 0.43, green: 0.66, blue: 0.88)
                ) {
                    localError = nil
                    onClearAuthStatus()
                    step = .server
                }

                WizardModeChoiceButton(
                    title: "This device",
                    subtitle: "No login",
                    systemImage: "iphone",
                    tint: Color(red: 0.45, green: 0.62, blue: 0.52)
                ) {
                    localError = nil
                    onClearAuthStatus()
                    Task {
                        await onUseLocalMode()
                    }
                }
            }
        }
    }

    private var serverStepContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            WizardHeroTile(
                title: "Self-hosted server",
                subtitle: "Connect your T'Day endpoint.",
                systemImage: "globe",
                tint: Color(red: 0.43, green: 0.66, blue: 0.88)
            )

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
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(currentMessageColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if serverCanResetTrust, currentMessageIsError {
                Button(isConnecting ? L("Resetting...") : L("Reset saved server trust")) {
                    Task {
                        await resetServerTrust()
                    }
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.primary)
            }

            if serverURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Button("Use saved server URL") {
                    requestSavedServerURL()
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.tdayRounded(size: 15, weight: .bold))
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

            Button("Change setup") {
                localError = nil
                onClearAuthStatus()
                step = .mode
            }
            .buttonStyle(WizardTextButtonStyle())
            .font(.tdayRounded(size: 15, weight: .bold))
            .foregroundStyle(colors.primary)
            .frame(maxWidth: .infinity, alignment: .center)
            .disabled(isConnecting)
        }
    }

    private var loginStepContent: some View {
        VStack(alignment: .leading, spacing: 11) {
            WizardHeroTile(
                title: isCreatingAccount ? "Create account" : "Sign in",
                subtitle: isCreatingAccount ? "Create your server account." : "Open your synced workspace.",
                systemImage: isCreatingAccount ? "person.badge.plus.fill" : "person.fill",
                tint: Color(red: 0.79, green: 0.47, blue: 0.50)
            )

            if isCreatingAccount {
                WizardInputField(title: "First name", text: $firstName, autocapitalization: .words, submitLabel: .next)
            }

            WizardInputField(
                title: "Username",
                text: $username,
                keyboardType: .default,
                textContentType: .username,
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
                        textContentType: .newPassword,
                        passwordRulesDescriptor: TdayPasswordRules.descriptor,
                        submitLabel: .next
                    )
                } else {
                    WizardInputField(
                        title: "Password",
                        text: $password,
                        isSecure: true,
                        textContentType: .password,
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
                    textContentType: .newPassword,
                    passwordRulesDescriptor: TdayPasswordRules.descriptor,
                    submitLabel: .done,
                    onSubmit: {
                        Task { await submitAuth() }
                    }
                )
            }

            if let message = currentMessage {
                Text(message)
                    .font(.tdayRounded(size: 14, weight: .bold))
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

            if !isCreatingAccount {
                Button("Forgot password?") {
                    localError = nil
                    onClearAuthStatus()
                    isShowingForgotPassword = true
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.primary)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.top, 2)
            }

            HStack(alignment: .center) {
                Button(isCreatingAccount ? L("I already have an account") : L("Create account")) {
                    localError = nil
                    onClearAuthStatus()
                    isCompletingAuthentication = false
                    isChoosingSecurityQuestions = false
                    isCreatingAccount.toggle()
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.primary)

                Spacer(minLength: 16)

                Button("Change setup") {
                    localError = nil
                    onClearAuthStatus()
                    isCompletingAuthentication = false
                    isCreatingAccount = false
                    isChoosingSecurityQuestions = false
                    step = .mode
                }
                .buttonStyle(WizardTextButtonStyle())
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(colors.primary)
            }
            .padding(.top, 6)
        }
    }

    private var securityQuestionsStepContent: some View {
        VStack(alignment: .leading, spacing: 11) {
            WizardHeroTile(
                title: "Security questions",
                subtitle: "Used to verify it's you if you reset your password.",
                systemImage: "lock.shield.fill",
                tint: Color(red: 0.5, green: 0.62, blue: 0.86)
            )

            if isLoadingSecurityQuestions {
                WizardLoadingPanel(
                    systemImage: "lock.shield.fill",
                    title: "Loading questions",
                    subtitle: "Fetching available security questions."
                )
            } else {
                WizardSecurityQuestionPicker(
                    title: "Question 1",
                    selection: $securityQuestionId1,
                    options: securityQuestions.filter { $0.id != securityQuestionId2 }
                )
                WizardInputField(
                    title: "Answer",
                    text: $securityAnswer1,
                    autocapitalization: .never,
                    disableAutocorrection: true,
                    submitLabel: .next
                )

                WizardSecurityQuestionPicker(
                    title: "Question 2",
                    selection: $securityQuestionId2,
                    options: securityQuestions.filter { $0.id != securityQuestionId1 }
                )
                WizardInputField(
                    title: "Answer",
                    text: $securityAnswer2,
                    autocapitalization: .never,
                    disableAutocorrection: true,
                    submitLabel: .done,
                    onSubmit: {
                        Task { await submitRegistration() }
                    }
                )
            }

            if let message = currentMessage {
                Text(message)
                    .font(.tdayRounded(size: 14, weight: .bold))
                    .foregroundStyle(currentMessageColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            WizardPrimaryButton(
                title: "Create account",
                enabled: securityQuestionsValid && !isAuthInFlight && !isLoadingSecurityQuestions
            ) {
                Task {
                    await submitRegistration()
                }
            }

            Button("Back") {
                localError = nil
                onClearAuthStatus()
                isChoosingSecurityQuestions = false
            }
            .buttonStyle(WizardTextButtonStyle())
            .font(.tdayRounded(size: 15, weight: .bold))
            .foregroundStyle(colors.primary)
            .frame(maxWidth: .infinity, alignment: .center)
            .disabled(isAuthInFlight)
            .padding(.top, 6)
        }
    }

    private var isLoginStep: Bool {
        step == .login
    }

    private var serverURLSavePromptBinding: Binding<Bool> {
        Binding(
            get: { pendingServerURLSavePrompt != nil },
            set: { isPresented in
                if !isPresented {
                    pendingServerURLSavePrompt = nil
                }
            }
        )
    }

    private var serverURLUsePromptBinding: Binding<Bool> {
        Binding(
            get: { pendingServerURLUsePrompt != nil },
            set: { isPresented in
                if !isPresented {
                    pendingServerURLUsePrompt = nil
                }
            }
        )
    }

    private var isAuthInFlight: Bool {
        authViewModel.isLoading || isCompletingAuthentication
    }

    private var authLoadingTitle: String {
        if isCompletingAuthentication && !authViewModel.isLoading {
            return "Opening T'Day"
        }
        return isCreatingAccount ? "Creating account" : "Authenticating"
    }

    private var authLoadingSubtitle: String {
        if isCompletingAuthentication && !authViewModel.isLoading {
            return "Loading your workspace."
        }
        return isCreatingAccount
            ? "Preparing your new T'Day account."
            : "Completing encrypted sign in."
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
                !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
                !registerPassword.isEmpty &&
                !confirmPassword.isEmpty &&
                !isAuthInFlight
        }

        return !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !password.isEmpty &&
            !isAuthInFlight
    }

    private func connectServer() async {
        localError = nil
        onClearAuthStatus()
        isConnecting = true
        let result = await onConnectServer(serverURL)
        isConnecting = false
        switch result {
        case .success:
            if !serverURLCameFromSystemCredential {
                pendingServerURLSavePrompt = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                step = .login
            }
            serverURLCameFromSystemCredential = false
        case let .failure(error):
            serverURLCameFromSystemCredential = false
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
            pendingServerURLSavePrompt = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
        case let .failure(error):
            localError = error.message
        }
    }

    private func requestSavedServerURL() {
        guard step == .server,
              serverURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !isConnecting else {
            return
        }

        Task { @MainActor in
            guard let savedServerURL = await systemCredentialService.requestSavedServerURL() else {
                return
            }

            pendingServerURLUsePrompt = savedServerURL
        }
    }

    private func useSavedServerURL(_ savedServerURL: String) {
        serverURL = savedServerURL
        serverURLCameFromSystemCredential = true
        Task { await connectServer() }
    }

    private func requestSavedServerURLIfAvailable() {
        guard !hasRequestedSavedServerURL else {
            return
        }

        hasRequestedSavedServerURL = true
        requestSavedServerURL()
    }

    private func requestSavedCredentialIfAvailable() {
        guard isLoginStep, !isCompletingAuthentication else {
            return
        }

        Task { @MainActor in
            _ = await credentialCoordinator.requestSavedCredentialIfAvailable(
                currentPassword: password,
                isCreatingAccount: isCreatingAccount,
                isAuthLoading: isAuthInFlight,
                credentialService: systemCredentialService
            ) { credential in
                isCompletingAuthentication = true
                let didLogin = await onLogin(credential.username, credential.password, .systemPasswordAutoFill)
                if !didLogin || authViewModel.pendingApproval {
                    isCompletingAuthentication = false
                }
                return didLogin
            }
        }
    }

    private func submitAuth() async {
        localError = nil
        onClearAuthStatus()
        if isCreatingAccount {
            guard validateRegistration() else {
                return
            }
            await beginSecurityQuestionsStep()
        } else {
            guard !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !password.isEmpty else {
                localError = "Username and password are required"
                return
            }
            isCompletingAuthentication = true
            let didLogin = await onLogin(username.trimmingCharacters(in: .whitespacesAndNewlines), password, .manual)
            if !didLogin || authViewModel.pendingApproval {
                isCompletingAuthentication = false
            }
        }
    }

    private func validateRegistration() -> Bool {
        let normalizedFirstName = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard normalizedFirstName.count >= 2 else {
            localError = "First name must be at least 2 characters"
            return false
        }
        guard normalizedUsername.range(of: Self.usernamePattern, options: .regularExpression) != nil else {
            localError = "Please enter a valid username"
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

    private func beginSecurityQuestionsStep() async {
        localError = nil
        isChoosingSecurityQuestions = true
        if securityQuestions.isEmpty {
            isLoadingSecurityQuestions = true
            let loaded = await onLoadSecurityQuestions()
            isLoadingSecurityQuestions = false
            securityQuestions = loaded
            if securityQuestionId1 == nil, loaded.indices.contains(0) {
                securityQuestionId1 = loaded[0].id
            }
            if securityQuestionId2 == nil, loaded.indices.contains(1) {
                securityQuestionId2 = loaded[1].id
            }
            if loaded.isEmpty {
                localError = "Could not load security questions. Please try again."
            }
        }
    }

    private var securityQuestionsValid: Bool {
        guard let id1 = securityQuestionId1, let id2 = securityQuestionId2, id1 != id2 else {
            return false
        }
        return !securityAnswer1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !securityAnswer2.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func submitRegistration() async {
        localError = nil
        onClearAuthStatus()
        guard let id1 = securityQuestionId1, let id2 = securityQuestionId2 else {
            localError = "Please choose two security questions"
            return
        }
        guard id1 != id2 else {
            localError = "Choose two different questions"
            return
        }
        let trimmedAnswer1 = securityAnswer1.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedAnswer2 = securityAnswer2.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedAnswer1.isEmpty, !trimmedAnswer2.isEmpty else {
            localError = "Please answer both questions"
            return
        }

        isCompletingAuthentication = true
        let didRegister = await onRegister(
            firstName.trimmingCharacters(in: .whitespacesAndNewlines),
            username.trimmingCharacters(in: .whitespacesAndNewlines),
            registerPassword,
            [
                SecurityAnswerInput(questionId: id1, answer: trimmedAnswer1),
                SecurityAnswerInput(questionId: id2, answer: trimmedAnswer2),
            ]
        )
        if !didRegister || authViewModel.pendingApproval {
            isCompletingAuthentication = false
        }
        if !didRegister {
            // Stay on the questions step so the user can correct and retry.
            isChoosingSecurityQuestions = true
        }
    }
}

private struct WizardStepChip: View {
    let title: String
    let systemImage: String
    let tint: Color
    let active: Bool
    var completed: Bool = false

    @Environment(\.tdayColors) private var colors

    var body: some View {
        // A step keeps its color once it is the active step or has been
        // completed; completed steps swap their glyph for a checkmark.
        let highlighted = active || completed
        let ringColor = Color(
            red: min(1, tint.components.red * 0.75),
            green: min(1, tint.components.green * 0.75),
            blue: min(1, tint.components.blue * 0.75)
        )

        HStack(spacing: 8) {
            Image(systemName: completed ? "checkmark" : systemImage)
                .font(.system(size: 13, weight: .bold))

            Text(L(title))
                .font(.tdayRounded(size: 13, weight: .bold))
                .lineLimit(1)
        }
        .foregroundStyle(highlighted ? .white : colors.onSurface.opacity(0.68))
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.chipCornerRadius, style: .continuous)
                .fill(highlighted ? tint : colors.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.chipCornerRadius, style: .continuous)
                .stroke(highlighted ? ringColor.opacity(0.62) : colors.onSurface.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: tint.opacity(highlighted ? 0.18 : 0), radius: highlighted ? 8 : 0, x: 0, y: 5)
    }
}

private struct WizardInputField: View {
    let title: String
    @Binding var text: String
    var isSecure = false
    var keyboardType: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var passwordRulesDescriptor: String?
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
            .font(.tdayRounded(size: 15, weight: .bold))
            .foregroundStyle(colors.onSurface)
            .tint(colors.primary)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: OnboardingWizardOverlay.Metrics.inputHeight)
            .background {
                RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.inputCornerRadius, style: .continuous)
                    .fill(colors.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.inputCornerRadius, style: .continuous)
                            .stroke(
                                isFocused ? colors.primary.opacity(0.82) : colors.onSurface.opacity(0.14),
                                lineWidth: isFocused ? 1.1 : 1
                            )
                    )
            }
            .shadow(color: Color.black.opacity(colors.isDark ? 0.08 : 0.04), radius: 7, x: 0, y: 4)
            .background(PasswordRulesConfigurator(rulesDescriptor: passwordRulesDescriptor))
            .accessibilityLabel(L(title))
            .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var inputControl: some View {
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
                .keyboardType(keyboardType)
                .autocorrectionDisabled(disableAutocorrection)
        }
    }
}

private enum TdayPasswordRules {
    static let descriptor = "allowed: ascii-printable; minlength: 8; required: upper; required: special;"
}

private struct WizardSecurityQuestionPicker: View {
    let title: String
    @Binding var selection: Int?
    let options: [SecurityQuestion]

    @Environment(\.tdayColors) private var colors

    private var selectedText: String {
        guard let selection, let match = options.first(where: { $0.id == selection }) else {
            return L("Choose a question")
        }
        return match.text
    }

    var body: some View {
        Menu {
            ForEach(options) { question in
                Button {
                    selection = question.id
                } label: {
                    if selection == question.id {
                        Label(question.text, systemImage: "checkmark")
                    } else {
                        Text(question.text)
                    }
                }
            }
        } label: {
            HStack(spacing: 10) {
                Text(selectedText)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(selection == nil ? colors.onSurface.opacity(0.42) : colors.onSurface)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Spacer(minLength: 0)

                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: OnboardingWizardOverlay.Metrics.inputHeight)
            .background {
                RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.inputCornerRadius, style: .continuous)
                    .fill(colors.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.inputCornerRadius, style: .continuous)
                            .stroke(colors.onSurface.opacity(0.14), lineWidth: 1)
                    )
            }
            .shadow(color: Color.black.opacity(colors.isDark ? 0.08 : 0.04), radius: 7, x: 0, y: 4)
        }
        .accessibilityLabel(L(title))
        .frame(maxWidth: .infinity)
    }
}

private struct WizardHeroTile: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let tint: Color

    var body: some View {
        ZStack(alignment: .trailing) {
            RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.tileCornerRadius, style: .continuous)
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

                VStack(alignment: .leading, spacing: 3) {
                    Text(L(title))
                        .font(.tdayRounded(size: 21, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)

                    Text(L(subtitle))
                        .font(.tdayRounded(size: 13, weight: .bold))
                        .foregroundStyle(.white.opacity(0.82))
                        .lineLimit(2)
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
        }
        .frame(maxWidth: .infinity)
        .frame(height: OnboardingWizardOverlay.Metrics.heroHeight)
        .clipShape(RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.tileCornerRadius, style: .continuous))
        .shadow(color: tint.opacity(0.16), radius: 9, x: 0, y: 7)
    }
}

private struct WizardModeChoiceButton: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let tint: Color
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.tileCornerRadius, style: .continuous)
                    .fill(tint)
                    .overlay(
                        RadialGradient(
                            colors: [Color.white.opacity(0.24), Color.white.opacity(0.08), .clear],
                            center: UnitPoint(x: 0.22, y: 0.18),
                            startRadius: 0,
                            endRadius: 140
                        )
                    )
                    .overlay(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.12),
                                Color(red: 0.91, green: 0.96, blue: 1.0).opacity(0.08),
                                .clear,
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Image(systemName: systemImage)
                    .font(.system(size: 70, weight: .regular))
                    .foregroundStyle(Color.white.opacity(0.22))
                    .offset(x: 14, y: 18)

                VStack(alignment: .leading, spacing: 8) {
                    Image(systemName: systemImage)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)

                    Spacer(minLength: 0)

                    Text(L(title))
                        .font(.tdayRounded(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .lineLimit(2)

                    Text(L(subtitle))
                        .font(.tdayRounded(size: 12, weight: .bold))
                        .foregroundStyle(.white.opacity(0.82))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(13)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: OnboardingWizardOverlay.Metrics.tileHeight)
            .clipShape(RoundedRectangle(cornerRadius: OnboardingWizardOverlay.Metrics.tileCornerRadius, style: .continuous))
        }
        .buttonStyle(WizardPressButtonStyle())
        .shadow(color: tint.opacity(colors.isDark ? 0.18 : 0.16), radius: 9, x: 0, y: 7)
    }
}

private struct PasswordRulesConfigurator: UIViewRepresentable {
    let rulesDescriptor: String?

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isHidden = true
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let rulesDescriptor, !rulesDescriptor.isEmpty else {
            return
        }

        DispatchQueue.main.async {
            let rules = UITextInputPasswordRules(descriptor: rulesDescriptor)
            uiView.nearestTextFields()
                .filter { $0.textContentType == .newPassword }
                .forEach { $0.passwordRules = rules }
        }
    }
}

private extension UIView {
    func nearestTextFields() -> [UITextField] {
        var ancestor = superview
        while let current = ancestor {
            let fields = current.recursiveTextFields()
            if !fields.isEmpty {
                return fields
            }
            ancestor = current.superview
        }
        return []
    }

    func recursiveTextFields() -> [UITextField] {
        subviews.flatMap { subview -> [UITextField] in
            if let textField = subview as? UITextField {
                return [textField]
            }
            return subview.recursiveTextFields()
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
            Text(L(title))
                .font(.tdayRounded(size: 15, weight: .bold))
                .foregroundStyle(enabled ? colors.onPrimary : colors.onSurfaceVariant.opacity(0.65))
                .frame(maxWidth: .infinity)
                .frame(height: OnboardingWizardOverlay.Metrics.buttonHeight)
                .background {
                    Capsule(style: .continuous)
                        .fill(enabled ? colors.primary : colors.surfaceVariant.opacity(0.95))
                        .overlay(
                            Capsule(style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: [Color.white.opacity(enabled ? 0.16 : 0), .clear],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                        )
                }
        }
        .buttonStyle(WizardPressButtonStyle())
        .shadow(color: enabled ? colors.primary.opacity(0.18) : .clear, radius: 11, x: 0, y: 8)
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

            Text(L(title))
                .font(.tdayRounded(size: 22, weight: .bold))
                .foregroundStyle(colors.onSurface)

            Text(L(subtitle))
                .font(.tdayRounded(size: 14, weight: .bold))
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
