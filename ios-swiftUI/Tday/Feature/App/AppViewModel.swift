import Foundation
import Network
import Observation

struct GitHubRelease: Codable, Equatable, Identifiable {
    let tagName: String
    let name: String?
    let body: String?
    let publishedAt: String?
    let htmlUrl: String

    var id: String { tagName }

    var version: String {
        tagName.hasPrefix("v") ? String(tagName.dropFirst()) : tagName
    }

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case name
        case body
        case publishedAt = "published_at"
        case htmlUrl = "html_url"
    }
}

/// Outcome of an inline account edit (display name / password change) so the
/// Settings editors can show a localized error without owning the API call.
enum ProfileEditResult: Equatable {
    case success
    case failure(String)
}

@MainActor
@Observable
final class AppViewModel {
    let container: AppContainer

    var loading = true
    var hasCompletedInitialBootstrap = false
    var authenticated = false
    var requiresServerSetup = false
    var requiresLogin = false
    /// Drives the persistent "waiting for admin approval" holding screen. True when a
    /// registered/attempted account is still PENDING; survives relaunch via the secure
    /// pending marker and clears once the account is approved (or the user signs out).
    var pendingApproval = false
    var pendingApprovalUsername: String?
    var isCheckingApproval = false
    var serverURL: String?
    var dataMode: AppDataMode = .unset
    var themeMode: AppThemeMode
    /// Stored language choice: a supported code or LanguageStore.systemValue.
    var appLanguage: String
    /// Bumped on every language change to force SwiftUI to re-resolve strings.
    var localizationGeneration: Int = 0
    var user: SessionUser?
    var error: String?
    var canResetServerTrust = false
    var pendingApprovalMessage: String?
    var isManualSyncing = false
    /// The user's own AI-summary on/off preference (default ON). Per-user now — the
    /// old global admin toggle is gone.
    var aiSummaryEnabled = true
    var isAiSummarySaving = false
    var selectedReminder: ReminderOption
    var isOffline = false
    var pendingMutationCount = 0
    var lastSuccessfulSyncEpochMs: Int64 = 0
    var lastSyncAttemptEpochMs: Int64 = 0
    var offlineNoticeID = 0
    var navigationPath: [AppRoute] = []
    var latestVersionName: String?
    var latestRelease: GitHubRelease?
    var currentRelease: GitHubRelease?
    var isReleaseLoading = false
    var releaseError: String?
    var versionCheckResult: VersionCheckResult = .compatible
    var backendVersion: String?

    var currentVersionName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"
    }

    var iosUpdateURL: URL? {
        Self.bundleUpdateURL()
    }

    var isLocalMode: Bool {
        dataMode == .local
    }

    var isWorkspaceAvailable: Bool {
        authenticated || isLocalMode
    }

    var syncStatus: MobileSyncStatus {
        MobileSyncStatus(
            dataMode: dataMode,
            isOffline: isOffline,
            isManualSyncing: isManualSyncing,
            pendingMutationCount: pendingMutationCount,
            lastSuccessfulSyncEpochMs: lastSuccessfulSyncEpochMs,
            lastSyncAttemptEpochMs: lastSyncAttemptEpochMs
        )
    }

    var hasUpdate: Bool {
        guard let remote = latestVersionName else { return false }
        return Self.compareVersions(remote, currentVersionName) > 0
    }

    @ObservationIgnored nonisolated(unsafe) private var cacheObservationTask: Task<Void, Never>?
    @ObservationIgnored nonisolated(unsafe) private var syncLoopTask: Task<Void, Never>?
    @ObservationIgnored nonisolated(unsafe) private var offlineSyncFailureTask: Task<Void, Never>?
    @ObservationIgnored nonisolated(unsafe) private var offlineSyncSuccessTask: Task<Void, Never>?
    @ObservationIgnored nonisolated(unsafe) private var networkMonitor: NWPathMonitor?
    @ObservationIgnored private let networkMonitorQueue = DispatchQueue(label: "tday.network-monitor")
    @ObservationIgnored private var isForegroundReconnectInFlight = false
    @ObservationIgnored private var lastOfflineNoticeShownAt: Date?

    private static let offlineNoticeCooldownSeconds: TimeInterval = 10 * 60

    init(container: AppContainer) {
        self.container = container
        themeMode = container.themeStore.load()
        appLanguage = container.languageStore.load()
        selectedReminder = container.reminderPreferenceStore.getDefaultReminder()
        observeCacheChanges()
        observeOfflineSyncFailures()
        observeOfflineSyncSuccesses()
        startNetworkMonitor()
    }

    deinit {
        cacheObservationTask?.cancel()
        syncLoopTask?.cancel()
        offlineSyncFailureTask?.cancel()
        offlineSyncSuccessTask?.cancel()
        networkMonitor?.cancel()
    }

    func bootstrap() async {
        loading = true
        error = nil
        isManualSyncing = false

        if container.serverConfigRepository.appDataMode() == .local {
            await enterLocalWorkspace()
            return
        }

        if !container.serverConfigRepository.hasServerConfigured() {
            container.authRepository.clearAllLocalUserDataForUnauthenticatedState()
            authenticated = false
            requiresServerSetup = true
            requiresLogin = false
            serverURL = nil
            dataMode = .unset
            user = nil
            error = nil
            canResetServerTrust = false
            pendingApprovalMessage = nil
            isAiSummarySaving = false
            pendingMutationCount = 0
            lastSuccessfulSyncEpochMs = 0
            lastSyncAttemptEpochMs = 0
            isOffline = false
            finishBootstrap()
            stopRealtime()
            stopSyncLoop()
            return
        }

        serverURL = container.serverConfigRepository.getServerURL()?.absoluteString
        dataMode = .server
        let sessionResult = await container.bootstrapSession()
        if let sessionResult, sessionResult.user.id != nil {
            let session = sessionResult.user
            let versionResult = await container.serverConfigRepository.recheckVersion()
            versionCheckResult = versionResult.versionCheck
            backendVersion = versionResult.backendVersion ?? backendVersion
            switch versionResult.versionCheck {
            case .appUpdateRequired(let version):
                backendVersion = version
            case .serverUpdateRequired(let version):
                backendVersion = version
            case .compatible:
                break
            }
            authenticated = true
            requiresServerSetup = false
            requiresLogin = false
            // Approval came through: drop the holding-screen state in the same render
            // pass that flips `authenticated`, so we never flash the login screen.
            pendingApproval = false
            pendingApprovalUsername = nil
            dataMode = .server
            user = session
            error = nil
            pendingApprovalMessage = nil
            canResetServerTrust = true
            isOffline = sessionResult.isOffline
            if sessionResult.isOffline && shouldShowOfflineNotice() {
                offlineNoticeID += 1
            }
            finishBootstrap()
            await refreshAiSummarySetting()
            refreshSyncStatusFromCache()
            startRealtime()
            startSyncLoop()
            await container.reminderScheduler.requestAuthorization()
            await rescheduleReminders()
            await refreshVersionInfo()
            return
        }

        container.authRepository.clearSessionOnly()

        // No active session. If a pending-approval marker exists, silently re-attempt
        // login: if the account was approved we fall through to a fresh authenticated
        // bootstrap; otherwise we show the persistent holding screen.
        if let creds = container.authRepository.loadPendingApproval() {
            let result = await container.authRepository.login(username: creds.username, password: creds.password)
            if case .success = result {
                container.authRepository.clearPendingApproval()
                // Keep the holding screen up through the re-bootstrap; the authenticated
                // branch clears it the instant the session is restored — no login flash.
                pendingApproval = true
                pendingApprovalUsername = creds.username
                await bootstrap()
                return
            }
            authenticated = false
            requiresServerSetup = false
            requiresLogin = false
            pendingApproval = true
            pendingApprovalUsername = creds.username
            dataMode = .server
            user = nil
            error = nil
            pendingApprovalMessage = nil
            canResetServerTrust = true
            isAiSummarySaving = false
            refreshSyncStatusFromCache()
            finishBootstrap()
            stopRealtime()
            stopSyncLoop()
            return
        }

        authenticated = false
        requiresServerSetup = false
        requiresLogin = true
        pendingApproval = false
        dataMode = .server
        user = nil
        error = nil
        pendingApprovalMessage = nil
        canResetServerTrust = true
        isAiSummarySaving = false
        refreshSyncStatusFromCache()
        finishBootstrap()
        stopRealtime()
        stopSyncLoop()
    }

    // MARK: - Pending admin approval (holding screen)

    /// Persist the pending marker and switch to the holding screen after a register/login
    /// that returned "pending approval".
    func enterPendingApproval(username: String, password: String) {
        container.authRepository.savePendingApproval(username: username, password: password)
        pendingApprovalUsername = username
        pendingApproval = true
        authenticated = false
        requiresLogin = false
    }

    /// Re-attempt login with the stored pending credentials. Returns true once the account
    /// is approved (and routes to Home via a fresh bootstrap); false while still pending.
    @discardableResult
    func checkPendingApproval() async -> Bool {
        guard !isCheckingApproval else { return false }
        guard let creds = container.authRepository.loadPendingApproval() else {
            pendingApproval = false
            return false
        }
        isCheckingApproval = true
        let result = await container.authRepository.login(username: creds.username, password: creds.password)
        if case .success = result {
            container.authRepository.clearPendingApproval()
            // Stay on the holding screen through the re-bootstrap; the authenticated
            // branch flips straight to Home without flashing the login screen.
            await bootstrap()
            isCheckingApproval = false
            return true
        }
        isCheckingApproval = false
        return false
    }

    /// Abandon the pending account and return to the sign-in / onboarding flow.
    func cancelPendingApproval() {
        container.authRepository.clearPendingApproval()
        pendingApproval = false
        pendingApprovalUsername = nil
        requiresLogin = true
    }

    func refreshSession() async {
        await bootstrap()
    }

    func useLocalMode() async {
        TdayTelemetry.addBreadcrumb("local_mode.enter")
        container.authRepository.clearAllLocalUserDataForUnauthenticatedState()
        container.serverConfigRepository.enableLocalMode()
        await enterLocalWorkspace()
    }

    private func enterLocalWorkspace() async {
        stopRealtime()
        stopSyncLoop()
        _ = try? await container.cacheManager.updateOfflineState { state in
            var nextState = state
            nextState.lastSuccessfulSyncEpochMs = 0
            nextState.lastSyncAttemptEpochMs = 0
            nextState.pendingMutations = []
            return nextState
        }
        authenticated = false
        requiresServerSetup = false
        requiresLogin = false
        serverURL = nil
        dataMode = .local
        user = nil
        error = nil
        canResetServerTrust = false
        pendingApprovalMessage = nil
        isManualSyncing = false
        isAiSummarySaving = false
        isOffline = false
        pendingMutationCount = 0
        lastSuccessfulSyncEpochMs = 0
        lastSyncAttemptEpochMs = 0
        versionCheckResult = .compatible
        backendVersion = nil
        await container.reminderScheduler.requestAuthorization()
        await rescheduleReminders()
        finishBootstrap()
    }

    func connectServer(rawURL: String) async -> Result<Void, MessageError> {
        do {
            TdayTelemetry.addBreadcrumb("server.probe", data: ["phase": "start"])
            let probeResult = try await container.serverConfigRepository.probeAndSave(rawURL)
            TdayTelemetry.addBreadcrumb(
                "server.probe",
                data: ["phase": "success", "version": String(describing: probeResult.versionCheck)]
            )
            serverURL = probeResult.serverURL
            dataMode = .server
            versionCheckResult = probeResult.versionCheck
            backendVersion = probeResult.backendVersion
            let isBlocking = probeResult.versionCheck != .compatible
            requiresServerSetup = false
            requiresLogin = !isBlocking
            error = nil
            canResetServerTrust = true
            return .success(())
        } catch {
            TdayTelemetry.addBreadcrumb(
                "server.probe",
                level: .warning,
                data: ["phase": "failure", "error": String(describing: type(of: error))]
            )
            let msg = serverConnectionMessage(for: error)
            self.error = msg
            canResetServerTrust = shouldOfferServerTrustReset(for: error)
            return .failure(MessageError(message: msg))
        }
    }

    func recheckVersion() async {
        guard !isLocalMode else {
            return
        }
        let result = await container.serverConfigRepository.recheckVersion()
        versionCheckResult = result.versionCheck
        backendVersion = result.backendVersion ?? backendVersion
        switch result.versionCheck {
        case .appUpdateRequired(let version):
            backendVersion = version
        case .serverUpdateRequired(let version):
            backendVersion = version
        case .compatible:
            break
        }
    }

    func resetTrustedServer(rawURL: String) async -> Result<Void, MessageError> {
        do {
            _ = try await container.serverConfigRepository.resetTrustedServer(rawURL: rawURL)
            let savedServerURL = container.serverConfigRepository.getServerURL()?.absoluteString ?? rawURL
            serverURL = savedServerURL
            dataMode = .server
            requiresServerSetup = false
            requiresLogin = true
            error = nil
            return .success(())
        } catch {
            let msg = serverConnectionMessage(for: error)
            self.error = msg
            canResetServerTrust = shouldOfferServerTrustReset(for: error)
            return .failure(MessageError(message: msg))
        }
    }

    func clearPendingApprovalNotice() {
        pendingApprovalMessage = nil
    }

    /// Loads the user's own AI-summary preference (cache first, then server).
    func refreshAiSummarySetting() async {
        guard !isLocalMode else { return }
        aiSummaryEnabled = container.settingsRepository.isAiSummaryEnabledSnapshot()
        aiSummaryEnabled = await container.settingsRepository.refreshAiSummaryEnabled()
    }

    /// Toggles the user's own AI-summary preference (optimistic, reverts on failure).
    func setAiSummaryEnabled(_ enabled: Bool) async {
        guard !isLocalMode, !isAiSummarySaving else { return }
        let previous = aiSummaryEnabled
        aiSummaryEnabled = enabled
        isAiSummarySaving = true
        do {
            aiSummaryEnabled = try await container.settingsRepository.setAiSummaryEnabled(enabled)
        } catch {
            aiSummaryEnabled = previous
            container.snackbarManager.show(
                userFacingMessage(for: error, fallback: "Could not update your settings."),
                kind: .error
            )
        }
        isAiSummarySaving = false
    }

    // MARK: - Account (profile name + password)

    /// Lightweight session re-fetch: re-pulls the user from the server and
    /// re-caches it, without the heavy full `bootstrap()` resync. Updating
    /// `user` propagates the new display name everywhere it's shown.
    func refreshSessionUser() async {
        if let refreshed = await container.authRepository.restoreSession() {
            user = refreshed
        }
    }

    /// Saves a new display name, then re-fetches the session so the change is
    /// confirmed by the server and reflected app-wide.
    func updateDisplayName(_ newName: String) async -> ProfileEditResult {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !isLocalMode, authenticated else {
            return .failure(L("You're not signed in."))
        }
        guard !trimmed.isEmpty else {
            return .failure(L("Name cannot be empty."))
        }
        do {
            _ = try await container.apiService.patchUserProfile(payload: .init(name: trimmed))
            await refreshSessionUser()
            return .success
        } catch {
            return .failure(userFacingMessage(for: error, fallback: "Could not update your name."))
        }
    }

    /// Changes the password (server enforces the strength rules), then re-fetches
    /// the session — the backend rotates the cookie on the same response.
    func changePassword(currentPassword: String, newPassword: String) async -> ProfileEditResult {
        guard !isLocalMode, authenticated else {
            return .failure(L("You're not signed in."))
        }
        do {
            _ = try await container.apiService.changePassword(
                payload: .init(currentPassword: currentPassword, newPassword: newPassword)
            )
            await refreshSessionUser()
            return .success
        } catch {
            return .failure(userFacingMessage(for: error, fallback: "Could not change your password."))
        }
    }

    func setThemeMode(_ mode: AppThemeMode) {
        themeMode = mode
        container.themeStore.save(mode)
    }

    /// Concrete locale identifier to apply (resolves "system" to the device language).
    var resolvedLocaleIdentifier: String {
        container.languageStore.resolvedCode()
    }

    func setAppLanguage(_ value: String) {
        appLanguage = value
        container.languageStore.save(value)
        // nil = follow system (clears the bundle override).
        LanguageBundle.setLanguage(value == LanguageStore.systemValue ? nil : value)
        localizationGeneration += 1
    }

    func setDefaultReminder(_ option: ReminderOption) {
        selectedReminder = option
        container.reminderPreferenceStore.setDefaultReminder(option)
        Task {
            await self.rescheduleReminders()
        }
    }

    func manualSync() async {
        guard !isLocalMode else {
            return
        }
        TdayTelemetry.addBreadcrumb("sync.manual", data: ["phase": "start"])
        isManualSyncing = true
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: true,
            notifyOfflineFailure: false,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        isManualSyncing = false
        applySyncResult(result, showOfflineNotice: true)
        await rescheduleReminders()
    }

    func reconnectAfterForeground() async {
        guard authenticated, !isLocalMode, !isForegroundReconnectInFlight else {
            return
        }

        isForegroundReconnectInFlight = true
        await reconnectWithServer(showOfflineNotice: true)
        isForegroundReconnectInFlight = false
    }

    func logout() async {
        await container.authRepository.logout()
        navigationPath = []
        await bootstrap()
    }

    /// Forcibly ends a session whose authentication has expired and returns the user to
    /// login (the onboarding overlay shows once `authenticated` is false). Reuses the
    /// logout flow, then surfaces a toast explaining why. No-op if a session is no longer
    /// active, which dedupes a burst of 401s into a single expiry.
    func expireSession() async {
        guard authenticated else { return }
        await logout()
        container.snackbarManager.show(
            "Your session expired — please sign in again.",
            kind: .error
        )
    }

    func navigate(to route: AppRoute) {
        switch route {
        case .home, .floaterTodos:
            navigationPath = []
        default:
            navigationPath.append(route)
        }
    }

    func goBack() {
        guard !navigationPath.isEmpty else {
            return
        }
        navigationPath.removeLast()
    }

    func handleDeepLink(_ url: URL) {
        guard let route = AppRoute.from(url: url) else {
            return
        }
        navigate(to: route)
    }

    private func observeCacheChanges() {
        cacheObservationTask = Task {
            for await _ in NotificationCenter.default.notifications(named: .offlineCacheDidChange) {
                await MainActor.run {
                    self.refreshSyncStatusFromCache()
                }
            }
        }
    }

    func refreshSyncStatusFromCache() {
        guard !isLocalMode else {
            pendingMutationCount = 0
            lastSuccessfulSyncEpochMs = 0
            lastSyncAttemptEpochMs = 0
            return
        }
        let state = container.cacheManager.loadOfflineState()
        pendingMutationCount = state.pendingMutations.count
        lastSuccessfulSyncEpochMs = state.lastSuccessfulSyncEpochMs
        lastSyncAttemptEpochMs = state.lastSyncAttemptEpochMs
    }

    private func startSyncLoop() {
        guard syncLoopTask == nil else {
            return
        }
        syncLoopTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(300))
                guard await MainActor.run(body: { self.authenticated }) else {
                    continue
                }
                let result = await self.container.syncAndRefresh(
                    force: false,
                    replayPendingMutations: true,
                    notifyOfflineFailure: false
                )
                let recoveredResult = await self.recoverSessionAndRetrySyncIfNeeded(
                    after: result,
                    connectionProbeTimeoutSeconds: nil
                )
                await MainActor.run {
                    self.applySyncResult(recoveredResult, suppressAuthenticationExpired: true)
                }
                await self.rescheduleReminders()
            }
        }
    }

    private func stopSyncLoop() {
        syncLoopTask?.cancel()
        syncLoopTask = nil
    }

    private func startRealtime() {
        Task { [weak self] in
            guard let self else {
                return
            }
            await self.container.realtimeClient.start { [weak self] event in
                guard event.requiresRefresh, let self else {
                    return
                }
                let result = await self.container.syncAndRefresh(
                    force: true,
                    replayPendingMutations: true,
                    notifyOfflineFailure: false
                )
                let recoveredResult = await self.recoverSessionAndRetrySyncIfNeeded(
                    after: result,
                    connectionProbeTimeoutSeconds: nil
                )
                await MainActor.run {
                    self.applySyncResult(recoveredResult, suppressAuthenticationExpired: true)
                }
                await self.rescheduleReminders()
            }
        }
    }

    private func stopRealtime() {
        let realtimeClient = container.realtimeClient
        Task {
            await realtimeClient.stop()
        }
    }

    private func applySyncResult(
        _ result: Result<Void, Error>,
        showOfflineNotice: Bool = false,
        suppressAuthenticationExpired: Bool = false
    ) {
        switch result {
        case .success:
            isOffline = false
            refreshSyncStatusFromCache()
        case let .failure(error):
            if isVersionGateError(error) {
                isOffline = false
                refreshSyncStatusFromCache()
                Task { await self.recheckVersion() }
                return
            }
            isOffline = isLikelyConnectivityIssue(error) ||
                (suppressAuthenticationExpired && isSessionAuthenticationIssue(error))
            if isOffline && showOfflineNotice && shouldShowOfflineNotice() {
                offlineNoticeID += 1
            }
            if !isOffline {
                container.snackbarManager.show(message: userFacingMessage(for: error))
            }
            refreshSyncStatusFromCache()
        }
    }

    private func isVersionGateError(_ error: Error) -> Bool {
        guard let apiError = error as? APIError else {
            return false
        }
        return apiError.statusCode == 426 ||
            apiError.reason == "app_update_required" ||
            apiError.reason == "server_update_required"
    }

    private func shouldShowOfflineNotice(now: Date = Date()) -> Bool {
        if let lastOfflineNoticeShownAt,
           now.timeIntervalSince(lastOfflineNoticeShownAt) < Self.offlineNoticeCooldownSeconds {
            return false
        }

        lastOfflineNoticeShownAt = now
        return true
    }

    private func observeOfflineSyncFailures() {
        offlineSyncFailureTask = Task {
            for await _ in NotificationCenter.default.notifications(named: .offlineSyncAttemptFailed) {
                await self.confirmOfflineSyncFailure()
            }
        }
    }

    private func confirmOfflineSyncFailure() async {
        guard authenticated else {
            return
        }

        await reconnectWithServer(showOfflineNotice: true)
    }

    private func observeOfflineSyncSuccesses() {
        offlineSyncSuccessTask = Task {
            for await _ in NotificationCenter.default.notifications(named: .offlineSyncAttemptSucceeded) {
                await MainActor.run {
                    guard self.authenticated else {
                        return
                    }
                    self.isOffline = false
                    self.refreshSyncStatusFromCache()
                }
            }
        }
    }

    private func startNetworkMonitor() {
        guard networkMonitor == nil else {
            return
        }

        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self, self.authenticated else {
                    return
                }

                if path.status == .satisfied {
                    guard self.isOffline else {
                        return
                    }
                    await self.reconnectWithServer(showOfflineNotice: false)
                }
            }
        }
        monitor.start(queue: networkMonitorQueue)
        networkMonitor = monitor
    }

    private func reconnectWithServer(showOfflineNotice: Bool) async {
        let result = await container.syncAndRefresh(
            force: true,
            replayPendingMutations: true,
            notifyOfflineFailure: false,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        let recoveredResult = await recoverSessionAndRetrySyncIfNeeded(
            after: result,
            connectionProbeTimeoutSeconds: SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        )
        applySyncResult(
            recoveredResult,
            showOfflineNotice: showOfflineNotice,
            suppressAuthenticationExpired: true
        )
        if case .success = recoveredResult {
            startRealtime()
            await rescheduleReminders()
        }
    }

    private func recoverSessionAndRetrySyncIfNeeded(
        after result: Result<Void, Error>,
        connectionProbeTimeoutSeconds: TimeInterval?
    ) async -> Result<Void, Error> {
        guard case let .failure(error) = result, isSessionAuthenticationIssue(error) else {
            return result
        }

        guard let restoredSession = await container.authRepository.restoreSessionForBootstrap() else {
            // A confirmed 401 that silent recovery could not heal, and not a mere
            // connectivity blip, means the session is truly gone — expire it and send
            // the user back to login. Connectivity issues stay in offline mode.
            if !isLikelyConnectivityIssue(error) && !isLocalMode {
                await expireSession()
            }
            return result
        }

        user = restoredSession.user
        authenticated = true
        isOffline = restoredSession.usedCachedSession

        guard !restoredSession.usedCachedSession else {
            return .failure(APIError(message: "Unable to refresh session while offline", statusCode: nil))
        }

        return await container.syncAndRefresh(
            force: true,
            replayPendingMutations: true,
            notifyOfflineFailure: false,
            connectionProbeTimeoutSeconds: connectionProbeTimeoutSeconds
        )
    }


    private func serverConnectionMessage(for error: Error) -> String {
        if let probeError = error as? ServerProbeError {
            switch probeError {
            case .invalidURL:
                return "Enter a valid server URL, like https://tday.example.com."
            case .insecureTransport:
                return "Use an HTTPS server URL. HTTP is only allowed for local servers."
            case .notTdayServer:
                return "That server responded, but it does not look like a T'Day server."
            case .certificateChanged:
                return "This server's certificate changed. Reset saved server trust only if you recognize this server."
            }
        }

        if let apiError = error as? APIError {
            if apiError.statusCode == nil {
                return serverConnectionMessage(from: apiError.message)
            }
            return userFacingMessage(for: apiError, fallback: "Could not connect to that T'Day server.")
        }

        return serverConnectionMessage(from: error.localizedDescription)
    }

    private func serverConnectionMessage(from rawMessage: String) -> String {
        let message = rawMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = message.lowercased()

        if lower.contains("unsupported url") || lower.contains("bad url") || lower.contains("invalid url") {
            return "Enter a valid server URL, like https://tday.example.com."
        }
        if lower.contains("timed out") || lower.contains("cannot connect") ||
           lower.contains("could not connect") || lower.contains("network connection was lost") {
            return "Could not reach that server. Check the URL and make sure the T'Day server is online."
        }
        if lower.contains("a server with the specified hostname could not be found") ||
           lower.contains("cannot find host") || lower.contains("dns") {
            return "That server name could not be found. Check the URL for typos."
        }
        if lower.contains("not connected to the internet") {
            return "No internet connection. Connect to the network and try again."
        }
        if lower.contains("ssl") || lower.contains("certificate") || lower.contains("secure connection") {
            return "Could not verify this server's secure connection. Reset saved server trust only if you recognize this server."
        }
        if lower == "cancelled" || lower == "canceled" || lower.contains("cancelled") || lower.contains("canceled") {
            return "The connection was interrupted before it finished. Check the URL and try again."
        }

        return message.isEmpty ? "Could not reach that server. Check the URL and try again." : message
    }

    private func shouldOfferServerTrustReset(for error: Error) -> Bool {
        if case .certificateChanged? = error as? ServerProbeError {
            return true
        }

        let message = (error as? APIError)?.message ?? error.localizedDescription
        let lower = message.lowercased()
        return lower.contains("ssl") || lower.contains("certificate") || lower.contains("secure connection")
    }

    private func finishBootstrap() {
        loading = false
        hasCompletedInitialBootstrap = true
    }

    private func rescheduleReminders() async {
        let tasks = container.todoRepository.fetchTodosSnapshot(mode: .all)
        await container.reminderScheduler.reschedule(tasks: tasks, defaultReminder: selectedReminder)
    }

    func refreshVersionInfo() async {
        TdayTelemetry.addBreadcrumb("update.check", data: ["scope": "all"])
        if !isLocalMode {
            await recheckVersion()
        }
        await refreshGitHubReleases()
    }

    func checkForUpdate() async {
        TdayTelemetry.addBreadcrumb("update.check", data: ["scope": "release"])
        await refreshGitHubReleases()
    }

    private func refreshGitHubReleases() async {
        isReleaseLoading = latestRelease == nil && currentRelease == nil
        releaseError = nil
        async let latestResult = fetchGitHubRelease(urlString: "https://api.github.com/repos/ohmzi/Tday/releases/latest")
        async let currentResult = fetchGitHubRelease(urlString: "https://api.github.com/repos/ohmzi/Tday/releases/tags/v\(currentVersionName)")
        let latest = await latestResult
        let current = await currentResult

        latestRelease = latest.release
        currentRelease = current.release
        latestVersionName = latest.release?.version
        releaseError = latest.error?.localizedDescription
        isReleaseLoading = false
    }

    private func fetchGitHubRelease(urlString: String) async -> (release: GitHubRelease?, error: Error?) {
        guard let url = URL(string: urlString) else {
            return (nil, nil)
        }
        var request = URLRequest(url: url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse,
               !(200..<300).contains(httpResponse.statusCode) {
                return (nil, nil)
            }
            return (try JSONDecoder().decode(GitHubRelease.self, from: data), nil)
        } catch {
            return (nil, error)
        }
    }

    nonisolated static func compareVersions(_ a: String, _ b: String) -> Int {
        let aParts = a.split(separator: ".").map { Int($0) ?? 0 }
        let bParts = b.split(separator: ".").map { Int($0) ?? 0 }
        let maxLen = max(aParts.count, bParts.count)
        for i in 0..<maxLen {
            let av = i < aParts.count ? aParts[i] : 0
            let bv = i < bParts.count ? bParts[i] : 0
            if av != bv { return av < bv ? -1 : 1 }
        }
        return 0
    }

    nonisolated static func bundleUpdateURL() -> URL? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "TdayUpdateURL") as? String else {
            return nil
        }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.hasPrefix("$(") else {
            return nil
        }
        return URL(string: trimmed)
    }
}
