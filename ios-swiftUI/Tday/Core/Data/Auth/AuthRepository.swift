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
            let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let key = try await api.getCredentialKey()
            let envelope = try CredentialEnvelopeBuilder.build(email: normalizedEmail, password: password, credentialKey: key)
            let csrf = try await api.getCsrfToken().csrfToken
            let callbackURL = serverConfigRepository.buildAbsoluteAppURL("app/tday")?.absoluteString
                ?? "/app/tday"
            let callback = try await api.signInWithCredentials(
                payload: CredentialsCallbackRequest(
                    email: nil,
                    password: nil,
                    encryptedPayload: envelope.encryptedPayload,
                    encryptedKey: envelope.encryptedKey,
                    encryptedIv: envelope.encryptedIv,
                    credentialKeyId: envelope.keyId,
                    credentialEnvelopeVersion: envelope.version,
                    passwordProof: nil,
                    passwordProofChallengeId: nil,
                    passwordProofVersion: nil,
                    captchaToken: nil,
                    csrfToken: csrf,
                    redirect: "false",
                    callbackUrl: callbackURL
                )
            )

            let queryItems = URLComponents(string: callback.response.url ?? "")?.queryItems ?? []
            let code = callback.response.code ?? queryItems.first(where: { $0.name == "code" })?.value
            let errorCode = queryItems.first(where: { $0.name == "error" })?.value

            if code == "pending_approval" {
                return .pendingApproval
            }
            if let errorCode, !errorCode.isEmpty {
                return .error(mapAuthError(errorCode))
            }
            if !(200 ..< 300).contains(callback.statusCode) && !(300 ..< 400).contains(callback.statusCode) {
                let responseMessage = callback.response.message?.trimmingCharacters(in: .whitespacesAndNewlines)
                if let responseMessage, !responseMessage.isEmpty {
                    return .error(mapAuthError(responseMessage))
                }
                return .error("Unable to sign in")
            }

            if let user = await restoreSession(), user.id != nil {
                secureStore.saveLastEmail(normalizedEmail)
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
            _ = try? await api.signOut()
        } catch {
            // Best effort sign-out before local cleanup.
        }
        await clearAllLocalUserDataForUnauthenticatedState()
    }

    func syncTimezone() async {
        _ = try? await api.syncTimezone(TimeZone.current.identifier)
    }

    @MainActor
    func clearSessionOnly() {
        cacheManager.clearSessionOnly()
        cookieStore.clearAll()
    }

    @MainActor
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
        case "Invalid credentials":
            return "Incorrect email or password"
        case "AccessDenied":
            return "Your account does not have access"
        case "SessionRequired":
            return "Please sign in again"
        case "Account approval required":
            return "Account pending admin approval."
        default:
            return value
        }
    }
}
