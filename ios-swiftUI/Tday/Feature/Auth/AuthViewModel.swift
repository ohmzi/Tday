import Foundation
import Observation

@MainActor
@Observable
final class AuthViewModel {
    private let authRepository: AuthRepository

    var isLoading = false
    var errorMessage: String?
    var infoMessage: String?
    var pendingApproval = false
    var savedEmail = ""

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
        savedEmail = authRepository.getLastEmail() ?? ""
    }

    func clearStatus() {
        errorMessage = nil
        infoMessage = nil
        pendingApproval = false
    }

    func login(email: String, password: String) async -> Bool {
        isLoading = true
        clearStatus()
        defer { isLoading = false }

        let result = await authRepository.login(email: email, password: password)
        switch result {
        case .success:
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

    func register(firstName: String, lastName: String, email: String, password: String) async -> Bool {
        isLoading = true
        clearStatus()
        defer { isLoading = false }

        let outcome = await authRepository.register(firstName: firstName, lastName: lastName, email: email, password: password)
        if outcome.success {
            infoMessage = outcome.message
            pendingApproval = outcome.requiresApproval
            savedEmail = email
            return true
        }

        errorMessage = friendlyMessage(outcome.message)
        return false
    }

    private func friendlyMessage(_ rawValue: String) -> String {
        let lower = rawValue.lowercased()
        if lower.contains("127.0.0.1") || lower.contains("localhost") || lower.contains("econnrefused") {
            return "Cannot reach server. Check your server URL and try again."
        }
        if lower.contains("serial name") || lower.contains("codingkeys") ||
           lower.contains("decodingerror") || lower.contains("no value associated with key") {
            return "This version of the app is out of date. Please update to continue."
        }
        if lower.contains("timed out") || lower.contains("network") || lower.contains("cannot connect") {
            return "Connection error. Check your internet and try again."
        }
        return "Something went wrong. Please try again."
    }
}
