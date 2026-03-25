import Foundation

final class AuthRepository {
    private let api: TdayAPIService
    private let secureStore: SecureStore
    private let serverConfigRepository: ServerConfigRepository
    private let cacheManager: OfflineCacheManager
    private let cookieStore: CookieStore
    private let themeStore: ThemeStore
    private let reminderPreferenceStore: ReminderPreferenceStore

    init(
        api: TdayAPIService,
        secureStore: SecureStore,
        serverConfigRepository: ServerConfigRepository,
        cacheManager: OfflineCacheManager,
        cookieStore: CookieStore,
        themeStore: ThemeStore,
        reminderPreferenceStore: ReminderPreferenceStore
    ) {
        self.api = api
        self.secureStore = secureStore
        self.serverConfigRepository = serverConfigRepository
        self.cacheManager = cacheManager
        self.cookieStore = cookieStore
        self.themeStore = themeStore
        self.reminderPreferenceStore = reminderPreferenceStore
    }

    func restoreSession() async -> SessionUser? {
        do {
            return try await api.getSession()?.user
        } catch {
            return nil
        }
    }

    func login(email: String, password: String) async -> AuthResult {
        guard serverConfigRepository.hasServerConfigured() else {
            return .error("Server URL is not configured")
        }

        do {
            let key = try await api.getCredentialKey()
            let envelope = try CredentialEnvelopeBuilder.build(email: email, password: password, credentialKey: key)
            let csrf = try await api.getCsrfToken().csrfToken
            let callbackURL = serverConfigRepository.buildAbsoluteAppURL("app/tday")?.absoluteString
                ?? "/app/tday"
            let callbackResponse = try await api.signInWithCredentials(
                payload: [
                    "csrfToken": csrf,
                    "encryptedPayload": envelope.encryptedPayload,
                    "encryptedKey": envelope.encryptedKey,
                    "encryptedIv": envelope.encryptedIv,
                    "credentialKeyId": envelope.keyId,
                    "credentialEnvelopeVersion": envelope.version,
                    "redirect": "false",
                    "callbackUrl": callbackURL,
                ]
            )

            let components = URLComponents(string: callbackResponse.url ?? "")
            let code = components?.queryItems?.first(where: { $0.name == "code" })?.value
            let errorCode = components?.queryItems?.first(where: { $0.name == "error" })?.value

            if code == "pending_approval" {
                return .pendingApproval
            }
            if let errorCode, !errorCode.isEmpty {
                return .error(mapAuthError(errorCode))
            }

            if let user = await restoreSession(), user.id != nil {
                secureStore.saveLastEmail(email.trimmingCharacters(in: .whitespacesAndNewlines))
                serverConfigRepository.persistRuntimeServerURL()
                await syncTimezone()
                return .success
            }
            return .error("Sign in failed. Please check backend URL and credentials.")
        } catch {
            return .error(error.localizedDescription)
        }
    }

    func register(firstName: String, lastName: String, email: String, password: String) async -> RegisterOutcome {
        do {
            let response = try await api.register(
                payload: RegisterRequest(
                    fname: firstName.trimmingCharacters(in: .whitespacesAndNewlines),
                    lname: lastName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : lastName.trimmingCharacters(in: .whitespacesAndNewlines),
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password
                )
            )
            return RegisterOutcome(
                success: true,
                requiresApproval: response.requiresApproval,
                message: response.message ?? "Account created"
            )
        } catch {
            return RegisterOutcome(success: false, requiresApproval: false, message: error.localizedDescription)
        }
    }

    func logout() async {
        do {
            let csrf = try await api.getCsrfToken().csrfToken
            let callbackURL = serverConfigRepository.buildAbsoluteAppURL("login")?.absoluteString ?? "/login"
            _ = try? await api.signOut(payload: ["csrfToken": csrf, "callbackUrl": callbackURL])
        } catch {
            // Best effort sign-out before local cleanup.
        }
        clearAllLocalUserDataForUnauthenticatedState()
    }

    func syncTimezone() async {
        _ = try? await api.syncTimezone(TimeZone.current.identifier)
    }

    func clearSessionOnly() {
        cacheManager.clearSessionOnly()
        cookieStore.clearAll()
    }

    func clearAllLocalUserDataForUnauthenticatedState() {
        cacheManager.clearAllLocalData()
        cookieStore.clearAll()
        secureStore.clearAllUserValues()
        themeStore.clear()
        reminderPreferenceStore.clear()
        serverConfigRepository.clearServerConfiguration()
    }

    func getLastEmail() -> String? {
        secureStore.loadLastEmail()
    }

    func lastEmail() -> String? {
        getLastEmail()
    }

    private func mapAuthError(_ value: String) -> String {
        switch value {
        case "CredentialsSignin":
            return "Incorrect email or password"
        case "AccessDenied":
            return "Your account does not have access"
        case "SessionRequired":
            return "Please sign in again"
        default:
            return value
        }
    }
}
