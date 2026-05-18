import Foundation

@MainActor
final class LoginCredentialCoordinator {
    private var hasRequestedSystemCredential = false
    private var isRequestingSystemCredential = false

    func requestSavedCredentialIfAvailable(
        currentPassword: String,
        isCreatingAccount: Bool,
        isAuthLoading: Bool,
        credentialService: SystemCredentialServicing,
        login: (SystemCredential) async -> Bool
    ) async -> Bool {
        guard !isCreatingAccount,
              currentPassword.isEmpty,
              !isAuthLoading,
              !isRequestingSystemCredential,
              !hasRequestedSystemCredential else {
            return false
        }

        hasRequestedSystemCredential = true
        isRequestingSystemCredential = true
        defer { isRequestingSystemCredential = false }

        guard let credential = await credentialService.requestSavedCredential() else {
            return false
        }

        return await login(credential)
    }
}
