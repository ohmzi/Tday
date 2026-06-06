import AuthenticationServices
import Foundation
import Security
import UIKit

struct SystemCredential: Equatable {
    let username: String
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
    func requestSavedServerURL() async -> String?
    func offerSaveOrUpdateCredential(_ credential: SystemCredential) async -> SystemCredentialSaveResult
    func offerSaveOrUpdateServerURL(_ rawURL: String) async -> SystemCredentialSaveResult
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

    return SystemCredential(username: normalizedUser, password: password)
}

@MainActor
final class SystemCredentialService: SystemCredentialServicing {
    private let secureStore: SecureStore
    private var activeAuthorizationSession: PasswordAuthorizationSession?

    init(secureStore: SecureStore = SecureStore()) {
        self.secureStore = secureStore
    }

    func requestSavedCredential() async -> SystemCredential? {
        credentialBreadcrumb(operation: "credential.request", kind: "login", result: "start")
        let session = PasswordAuthorizationSession()
        activeAuthorizationSession = session
        let credential = await session.requestPasswordCredential()
            .flatMap { makeLoginCredential(user: $0.user, password: $0.password) }
        activeAuthorizationSession = nil
        credentialBreadcrumb(
            operation: "credential.result",
            kind: "login",
            result: credential == nil ? "empty" : "filled"
        )
        return credential
    }

    func requestSavedServerURL() async -> String? {
        credentialBreadcrumb(operation: "credential.request", kind: "server_url", result: "start")
        let url = secureStore.loadServerURLSuggestion()?.absoluteString
        credentialBreadcrumb(
            operation: "credential.result",
            kind: "server_url",
            result: url == nil ? "empty" : "filled"
        )
        return url
    }

    func offerSaveOrUpdateCredential(_ credential: SystemCredential) async -> SystemCredentialSaveResult {
        guard !credential.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !credential.password.isEmpty else {
            credentialBreadcrumb(operation: "credential.save", kind: "login", result: "skipped")
            return .skipped
        }

        credentialBreadcrumb(operation: "credential.save", kind: "login", result: "start")
        if #available(iOS 26.2, *) {
            let result = await saveWithCredentialDataManager(credential)
            credentialBreadcrumb(operation: "credential.save", kind: "login", result: telemetryResult(result))
            return result
        } else {
            let result = await saveWithSharedWebCredential(credential)
            credentialBreadcrumb(operation: "credential.save", kind: "login", result: telemetryResult(result))
            return result
        }
    }

    func offerSaveOrUpdateServerURL(_ rawURL: String) async -> SystemCredentialSaveResult {
        guard let normalizedURL = secureStore.normalizeServerURL(rawURL) else {
            credentialBreadcrumb(operation: "credential.save", kind: "server_url", result: "skipped")
            return .skipped
        }

        credentialBreadcrumb(operation: "credential.save", kind: "server_url", result: "start")
        secureStore.saveServerURLSuggestion(normalizedURL)
        credentialBreadcrumb(operation: "credential.save", kind: "server_url", result: "saved")
        return .saved
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
        let passwordCredential = ASPasswordCredential(user: credential.username, password: credential.password)

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
                credential.username as CFString,
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

    private func credentialBreadcrumb(
        operation: String,
        kind: String,
        result: String
    ) {
        TdayTelemetry.addBreadcrumb(
            operation,
            data: [
                "kind": kind,
                "result": result
            ]
        )
    }

    private func telemetryResult(_ result: SystemCredentialSaveResult) -> String {
        switch result {
        case .saved:
            return "saved"
        case .skipped:
            return "skipped"
        case .cancelled:
            return "cancelled"
        case .failed:
            return "failed"
        }
    }
}

@MainActor
private final class PasswordAuthorizationSession: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<ASPasswordCredential?, Never>?
    private var controller: ASAuthorizationController?

    func requestPasswordCredential() async -> ASPasswordCredential? {
        await withCheckedContinuation { continuation in
            self.continuation = continuation

            let provider = ASAuthorizationPasswordProvider()
            let request = provider.createRequest()
            let controller = ASAuthorizationController(authorizationRequests: [request])
            self.controller = controller
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let credential = authorization.credential as? ASPasswordCredential
        finish(credential)
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

    private func finish(_ credential: ASPasswordCredential?) {
        controller = nil
        continuation?.resume(returning: credential)
        continuation = nil
    }
}
