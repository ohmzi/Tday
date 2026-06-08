import Foundation

protocol AuthRepositoryServicing: AnyObject {
    func restoreSession() async -> SessionUser?
    func login(username: String, password: String) async -> AuthResult
    func register(firstName: String, lastName: String, username: String, password: String, securityAnswers: [SecurityAnswerInput]) async -> RegisterOutcome
    func fetchAllSecurityQuestions() async throws -> [SecurityQuestion]
    func fetchQuestionsForUsername(_ username: String) async throws -> [SecurityQuestion]
    func lookupQuestions(_ username: String) async -> LookupQuestionsOutcome
    func verifyAnswers(username: String, answers: [SecurityAnswerInput]) async -> VerifyAnswersOutcome
    func resetPassword(username: String, answers: [SecurityAnswerInput], newPassword: String) async -> PasswordResetResult
    func requestAdminReset(_ username: String) async -> Bool
    func setSecurityQuestions(_ answers: [SecurityAnswerInput]) async -> Bool
    func logout() async
    func syncTimezone() async
    func savePendingApproval(username: String, password: String)
    func loadPendingApproval() -> (username: String, password: String)?
    func clearPendingApproval()
    @MainActor func clearSessionOnly()
    @MainActor func clearAllLocalUserDataForUnauthenticatedState()
    func getLastUsername() -> String?
    func lastUsername() -> String?
}

struct RestoredSession {
    let user: SessionUser
    let usedCachedSession: Bool
}

final class AuthRepository: AuthRepositoryServicing {
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
            let user = try await restoreSessionFromServer()
            cacheSessionUser(user)
            return user
        } catch {
            return nil
        }
    }

    func restoreSessionForBootstrap() async -> RestoredSession? {
        do {
            guard let user = try await restoreSessionFromServer(), user.id != nil else {
                return nil
            }
            cacheSessionUser(user)
            return RestoredSession(user: user, usedCachedSession: false)
        } catch {
            guard isLikelyConnectivityIssue(error) else {
                return nil
            }
            if let cached = loadCachedSessionUser(), cached.id != nil {
                return RestoredSession(user: cached, usedCachedSession: true)
            }
            guard let fallback = await loadLastKnownOfflineSessionUser(), fallback.id != nil else {
                return nil
            }
            return RestoredSession(user: fallback, usedCachedSession: true)
        }
    }

    private func restoreSessionFromServer() async throws -> SessionUser? {
        let user = try await api.getSession()?.user
        if user?.id == nil {
            secureStore.clearCachedSessionUser()
        }
        return user
    }

    func login(username: String, password: String) async -> AuthResult {
        guard serverConfigRepository.hasServerConfigured() else {
            return .error("Server URL is not configured")
        }

        do {
            let normalizedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let key = try await api.getCredentialKey()
            let envelope = try CredentialEnvelopeBuilder.build(username: normalizedUsername, password: password, credentialKey: key)
            let csrf = try await api.getCsrfToken().csrfToken
            let callbackURL = serverConfigRepository.buildAbsoluteAppURL("app/tday")?.absoluteString
                ?? "/app/tday"
            let callback = try await api.signInWithCredentials(
                payload: CredentialsCallbackRequest(
                    username: nil,
                    password: nil,
                    encryptedPayload: envelope.encryptedPayload,
                    encryptedKey: envelope.encryptedKey,
                    encryptedIv: envelope.encryptedIv,
                    credentialKeyId: envelope.keyId,
                    credentialEnvelopeVersion: envelope.version,
                    passwordProof: nil,
                    passwordProofChallengeId: nil,
                    passwordProofVersion: nil,
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
                if isLikelyServerUnavailableStatusCode(callback.statusCode) {
                    return .error(ConnectionFailureMessage.serverUnavailable)
                }
                let responseMessage = callback.response.message?.trimmingCharacters(in: .whitespacesAndNewlines)
                if let responseMessage, !responseMessage.isEmpty {
                    return .error(mapAuthError(responseMessage))
                }
                return .error("Unable to sign in")
            }

            if let user = await restoreSession(), user.id != nil {
                secureStore.saveLastUsername(normalizedUsername)
                serverConfigRepository.persistRuntimeServerURL()
                await syncTimezone()
                return .success
            }
            return .error("Sign in failed. Please check backend URL and credentials.")
        } catch {
            return .error(connectionFailureMessage(for: error) ?? mapAuthError(error.localizedDescription))
        }
    }

    func register(firstName: String, lastName: String, username: String, password: String, securityAnswers: [SecurityAnswerInput]) async -> RegisterOutcome {
        do {
            let response = try await api.register(
                payload: RegisterRequest(
                    fname: firstName.trimmingCharacters(in: .whitespacesAndNewlines),
                    lname: lastName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : lastName.trimmingCharacters(in: .whitespacesAndNewlines),
                    username: username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
                    password: password,
                    securityAnswers: securityAnswers
                )
            )
            return RegisterOutcome(
                success: true,
                requiresApproval: response.requiresApproval,
                message: response.message ?? "Account created"
            )
        } catch {
            return RegisterOutcome(success: false, requiresApproval: false, message: connectionFailureMessage(for: error) ?? mapAuthError(error.localizedDescription))
        }
    }

    func fetchAllSecurityQuestions() async throws -> [SecurityQuestion] {
        try await api.getAllSecurityQuestions().questions
    }

    func fetchQuestionsForUsername(_ username: String) async throws -> [SecurityQuestion] {
        let normalized = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return try await api.getSecurityQuestions(username: normalized).questions
    }

    func resetPassword(username: String, answers: [SecurityAnswerInput], newPassword: String) async -> PasswordResetResult {
        do {
            _ = try await api.resetPassword(
                payload: SelfServiceResetRequest(
                    username: username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
                    answers: answers,
                    newPassword: newPassword
                )
            )
            return .success
        } catch let apiError as APIError {
            switch apiError.reason {
            case "reset_locked":
                return .locked(apiError.message)
            case "reset_failed":
                return .failed(apiError.message)
            default:
                return .error(connectionFailureMessage(for: apiError) ?? apiError.message)
            }
        } catch {
            return .error(connectionFailureMessage(for: error) ?? error.localizedDescription)
        }
    }

    func lookupQuestions(_ username: String) async -> LookupQuestionsOutcome {
        do {
            let questions = try await api.getSecurityQuestions(
                username: username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            ).questions
            return .found(questions)
        } catch let apiError as APIError {
            if apiError.reason == "user_not_found" { return .notFound }
            return .error(connectionFailureMessage(for: apiError) ?? apiError.message)
        } catch {
            return .error(connectionFailureMessage(for: error) ?? error.localizedDescription)
        }
    }

    func verifyAnswers(username: String, answers: [SecurityAnswerInput]) async -> VerifyAnswersOutcome {
        do {
            let response = try await api.verifySecurityAnswers(
                payload: VerifySecurityAnswersRequest(
                    username: username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
                    answers: answers
                )
            )
            return response.valid ? .valid : .invalid(response.results)
        } catch let apiError as APIError {
            if apiError.reason == "reset_locked" { return .locked }
            return .error(connectionFailureMessage(for: apiError) ?? apiError.message)
        } catch {
            return .error(connectionFailureMessage(for: error) ?? error.localizedDescription)
        }
    }

    func requestAdminReset(_ username: String) async -> Bool {
        do {
            _ = try await api.requestAdminReset(
                payload: RequestAdminResetRequest(
                    username: username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                )
            )
            return true
        } catch {
            return false
        }
    }

    func setSecurityQuestions(_ answers: [SecurityAnswerInput]) async -> Bool {
        do {
            _ = try await api.setUserSecurityQuestions(payload: SetSecurityQuestionsRequest(answers: answers))
            return true
        } catch {
            return false
        }
    }

    func logout() async {
        _ = try? await api.signOut()
        await clearAllLocalUserDataForUnauthenticatedState(preservingServerConfiguration: true)
    }

    func syncTimezone() async {
        _ = try? await api.syncTimezone(TimeZone.current.identifier)
    }

    @MainActor
    func savePendingApproval(username: String, password: String) {
        secureStore.savePendingApproval(username: username, password: password)
    }

    func loadPendingApproval() -> (username: String, password: String)? {
        secureStore.loadPendingApproval()
    }

    func clearPendingApproval() {
        secureStore.clearPendingApproval()
    }

    func clearSessionOnly() {
        secureStore.clearCachedSessionUser()
        cacheManager.clearSessionOnly()
        cookieStore.clearAll()
    }

    @MainActor
    func clearAllLocalUserDataForUnauthenticatedState() {
        clearAllLocalUserDataForUnauthenticatedState(preservingServerConfiguration: false)
    }

    @MainActor
    private func clearAllLocalUserDataForUnauthenticatedState(preservingServerConfiguration: Bool) {
        cacheManager.clearAllLocalData()
        cookieStore.clearAll()
        secureStore.clearAllUserValues(preservingServerURL: preservingServerConfiguration)
        themeStore.clear()
        reminderPreferenceStore.clear()
        if !preservingServerConfiguration {
            serverConfigRepository.clearServerConfiguration()
        }
    }

    func getLastUsername() -> String? {
        secureStore.loadLastUsername()
    }

    func lastUsername() -> String? {
        getLastUsername()
    }

    private func cacheSessionUser(_ user: SessionUser?) {
        guard let user, user.id != nil, let data = try? JSONEncoder().encode(user) else {
            return
        }
        secureStore.saveCachedSessionUserData(data)
    }

    private func loadCachedSessionUser() -> SessionUser? {
        guard let data = secureStore.loadCachedSessionUserData() else {
            return nil
        }
        return try? JSONDecoder().decode(SessionUser.self, from: data)
    }

    private func loadLastKnownOfflineSessionUser() async -> SessionUser? {
        guard let username = secureStore.loadLastUsername() else {
            return nil
        }
        let hasCachedData = await MainActor.run {
            cacheManager.hasCachedData()
        }
        guard hasCachedData else {
            return nil
        }
        return SessionUser(
            id: username,
            name: nil,
            username: username,
            image: nil,
            timeZone: nil,
            role: nil,
            approvalStatus: nil
        )
    }

    private func mapAuthError(_ value: String) -> String {
        switch value {
        case "CredentialsSignin":
            return "Incorrect username or password"
        case "Invalid credentials":
            return "Incorrect username or password"
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
