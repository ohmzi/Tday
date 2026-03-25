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
        if rawValue.contains("127.0.0.1") || rawValue.contains("localhost") || rawValue.contains("ECONNREFUSED") {
            return "Cannot reach backend. Check the server URL."
        }
        return rawValue
    }
}
