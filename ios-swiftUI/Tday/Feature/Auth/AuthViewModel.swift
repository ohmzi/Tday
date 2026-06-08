import Foundation
import Observation

@MainActor
@Observable
final class AuthViewModel {
    private let authRepository: AuthRepositoryServicing
    private let systemCredentialService: SystemCredentialServicing

    var isLoading = false
    var errorMessage: String?
    var infoMessage: String?
    var pendingApproval = false
    var savedUsername = ""

    init(
        authRepository: AuthRepositoryServicing,
        systemCredentialService: SystemCredentialServicing
    ) {
        self.authRepository = authRepository
        self.systemCredentialService = systemCredentialService
        savedUsername = authRepository.getLastUsername() ?? ""
    }

    func clearStatus() {
        errorMessage = nil
        infoMessage = nil
        pendingApproval = false
    }

    func login(username: String, password: String, source: LoginCredentialSource = .manual) async -> Bool {
        isLoading = true
        clearStatus()
        defer { isLoading = false }

        let result = await authRepository.login(username: username, password: password)
        switch result {
        case .success:
            let normalizedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            savedUsername = normalizedUsername
            if source == .manual {
                _ = await systemCredentialService.offerSaveOrUpdateCredential(
                    SystemCredential(username: normalizedUsername, password: password)
                )
            }
            return true
        case .pendingApproval:
            pendingApproval = true
            infoMessage = "Account pending admin approval."
            return false
        case let .error(message):
            errorMessage = friendlyMessage(message)
            return false
        }
    }

    func register(firstName: String, lastName: String, username: String, password: String, securityAnswers: [SecurityAnswerInput]) async -> Bool {
        isLoading = true
        clearStatus()
        defer { isLoading = false }

        let outcome = await authRepository.register(firstName: firstName, lastName: lastName, username: username, password: password, securityAnswers: securityAnswers)
        if outcome.success {
            infoMessage = outcome.message
            pendingApproval = outcome.requiresApproval
            let normalizedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            savedUsername = normalizedUsername
            _ = await systemCredentialService.offerSaveOrUpdateCredential(
                SystemCredential(username: normalizedUsername, password: password)
            )
            return true
        }

        errorMessage = friendlyMessage(outcome.message)
        return false
    }

    // MARK: - Security questions catalogue

    func loadAllSecurityQuestions() async -> [SecurityQuestion] {
        (try? await authRepository.fetchAllSecurityQuestions()) ?? []
    }

    // MARK: - Forgot password

    func fetchQuestionsForUsername(_ username: String) async -> [SecurityQuestion]? {
        try? await authRepository.fetchQuestionsForUsername(username)
    }

    func lookupQuestions(_ username: String) async -> LookupQuestionsOutcome {
        await authRepository.lookupQuestions(username)
    }

    func verifyAnswers(username: String, answers: [SecurityAnswerInput]) async -> VerifyAnswersOutcome {
        await authRepository.verifyAnswers(username: username, answers: answers)
    }

    func resetPassword(username: String, answers: [SecurityAnswerInput], newPassword: String) async -> PasswordResetResult {
        await authRepository.resetPassword(username: username, answers: answers, newPassword: newPassword)
    }

    func requestAdminReset(_ username: String) async -> Bool {
        await authRepository.requestAdminReset(username)
    }

    // MARK: - Set security questions gate

    func setSecurityQuestions(_ answers: [SecurityAnswerInput]) async -> Bool {
        await authRepository.setSecurityQuestions(answers)
    }

    private func friendlyMessage(_ rawValue: String) -> String {
        let lower = rawValue.lowercased()
        if lower.contains("invalid credentials") || lower.contains("incorrect username or password") {
            return "Incorrect username or password"
        }
        if lower.contains("pending admin approval") || lower.contains("approval required") || lower.contains("pending_approval") {
            return "Account pending admin approval."
        }
        if lower.contains("127.0.0.1") || lower.contains("localhost") || lower.contains("econnrefused") ||
           lower.contains("timed out") || lower.contains("network") || lower.contains("cannot connect") ||
           lower.contains("could not connect") || lower.contains("not connected") ||
           lower.contains("connection refused") || lower.contains("bad gateway") ||
           lower.contains("service unavailable") || lower.contains("gateway timeout") ||
           lower.contains("origin unreachable") || lower.contains("web server is down") {
            return Self.serverUnreachableMessage
        }
        if lower.contains("serial name") || lower.contains("codingkeys") ||
           lower.contains("decodingerror") || lower.contains("no value associated with key") ||
           lower.contains("unsupported secure sign-in version") {
            return "This version of the app is out of date. Please update to continue."
        }
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Something went wrong. Please try again." : trimmed
    }

    private static let serverUnreachableMessage = "Cannot reach server. Check your server URL and try again."
}
