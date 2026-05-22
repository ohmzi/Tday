import AuthenticationServices
import Foundation
import Security
import UIKit

struct SystemCredential: Equatable {
    let email: String
    let password: String
}

enum SystemCredentialSaveResult: Equatable {
    case saved
    case skipped
    case cancelled
    case failed(String)

    var failureMessage: String? {
        switch self {
        case .failed(let message):
            return message
        case .saved, .skipped, .cancelled:
            return nil
        }
    }
}

enum LoginCredentialSource {
    case manual
    case systemPasswordAutoFill
}

@MainActor
protocol SystemCredentialServicing: AnyObject {
    func requestSavedCredential() async -> SystemCredential?
    func offerSaveOrUpdateCredential(_ credential: SystemCredential) async -> SystemCredentialSaveResult
}

enum SystemCredentialScope {
    static let appCredentialHost = "tday.ohmz.cloud"
}

private enum LegacySystemCredentialRecord {
    static let serverURLUser = "T'Day Server URL"
}

private func makeLoginCredential(user: String, password: String) -> SystemCredential? {
    let normalizedUser = user.trimmingCharacters(in: .whitespacesAndNewlines)
    guard normalizedUser != LegacySystemCredentialRecord.serverURLUser,
          !normalizedUser.isEmpty,
          !password.isEmpty else {
        return nil
    }

    return SystemCredential(email: normalizedUser, password: password)
}

@MainActor
final class SystemCredentialService: SystemCredentialServicing {
    private var activeAuthorizationSession: PasswordAuthorizationSession?
    private var hasRequestedLegacyServerURLCredentialRemoval = false

    func requestSavedCredential() async -> SystemCredential? {
        await removeLegacyServerURLCredentialIfNeeded()
        let session = PasswordAuthorizationSession()
        activeAuthorizationSession = session
        let credential = await session.requestSavedCredential()
        activeAuthorizationSession = nil
        return credential
    }

    func offerSaveOrUpdateCredential(_ credential: SystemCredential) async -> SystemCredentialSaveResult {
        guard !credential.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !credential.password.isEmpty else {
            return .skipped
        }

        if #available(iOS 26.2, *) {
            return await saveWithCredentialDataManager(credential)
        } else {
            return await saveWithSharedWebCredential(credential)
        }
    }

    @available(iOS 26.2, *)
    private func saveWithCredentialDataManager(_ credential: SystemCredential) async -> SystemCredentialSaveResult {
        let host = SystemCredentialScope.appCredentialHost
        let scope = ASAutoFillURLScope(
            scheme: .https,
            host: host,
            port: nil,
            path: ""
        )
        let passwordCredential = ASPasswordCredential(user: credential.email, password: credential.password)

        do {
            try await ASCredentialDataManager().save(
                password: passwordCredential,
                for: scope,
                title: "Tday",
                anchor: PasswordAuthorizationSession.presentationAnchor()
            )
            return .saved
        } catch {
            let nsError = error as NSError
            if nsError.domain == ASAuthorizationError.errorDomain,
               nsError.code == ASAuthorizationError.canceled.rawValue {
                return .cancelled
            }
            return .failed("Apple Passwords could not save this Tday login. Check that \(host) is associated with the Tday iOS app.")
        }
    }

    private func saveWithSharedWebCredential(_ credential: SystemCredential) async -> SystemCredentialSaveResult {
        let host = SystemCredentialScope.appCredentialHost
        return await withCheckedContinuation { (continuation: CheckedContinuation<SystemCredentialSaveResult, Never>) in
            SecAddSharedWebCredential(
                host as CFString,
                credential.email as CFString,
                credential.password as CFString
            ) { error in
                guard let error else {
                    continuation.resume(returning: .saved)
                    return
                }

                let nsError = error as Error as NSError
                if nsError.code == Int(errSecUserCanceled) {
                    continuation.resume(returning: .cancelled)
                    return
                }

                continuation.resume(returning: .failed("Apple Passwords could not save this Tday login. Check that \(host) is associated with the Tday iOS app."))
            }
        }
    }

    private func removeLegacyServerURLCredentialIfNeeded() async {
        guard !hasRequestedLegacyServerURLCredentialRemoval else {
            return
        }

        hasRequestedLegacyServerURLCredentialRemoval = true
        let host = SystemCredentialScope.appCredentialHost
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            SecAddSharedWebCredential(
                host as CFString,
                LegacySystemCredentialRecord.serverURLUser as CFString,
                nil
            ) { _ in
                continuation.resume()
            }
        }
    }
}

@MainActor
private final class PasswordAuthorizationSession: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<SystemCredential?, Never>?
    private var controller: ASAuthorizationController?

    func requestSavedCredential() async -> SystemCredential? {
        await withCheckedContinuation { continuation in
            self.continuation = continuation

            let provider = ASAuthorizationPasswordProvider()
            let request = provider.createRequest()
            let controller = ASAuthorizationController(authorizationRequests: [request])
            self.controller = controller
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests(options: [.preferImmediatelyAvailableCredentials])
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let credential = authorization.credential as? ASPasswordCredential
        finish(
            credential.flatMap {
                makeLoginCredential(user: $0.user, password: $0.password)
            }
        )
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish(nil)
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        Self.presentationAnchor()
    }

    static func presentationAnchor() -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        if let keyWindow = scenes.flatMap(\.windows).first(where: \.isKeyWindow) {
            return keyWindow
        }
        if let firstWindow = scenes.flatMap(\.windows).first {
            return firstWindow
        }
        return UIWindow(frame: UIScreen.main.bounds)
    }

    private func finish(_ credential: SystemCredential?) {
        controller = nil
        continuation?.resume(returning: credential)
        continuation = nil
    }
}
