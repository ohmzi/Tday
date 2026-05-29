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

@MainActor
@Observable
final class AppViewModel {
    let container: AppContainer

    var loading = true
    var hasCompletedInitialBootstrap = false
    var authenticated = false
    var requiresServerSetup = false
    var requiresLogin = false
    var serverURL: String?
    var themeMode: AppThemeMode
    var user: SessionUser?
    var error: String?
    var canResetServerTrust = false
    var pendingApprovalMessage: String?
    var isManualSyncing = false
    var adminAiSummaryEnabled: Bool?
    var isAdminAiSummaryLoading = false
    var isAdminAiSummarySaving = false
    var adminAiSummaryError: String?
    var aiSummaryValidationError: String?
    var selectedReminder: ReminderOption
    var isOffline = false
    var pendingMutationCount = 0
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

        if !container.serverConfigRepository.hasServerConfigured() {
            container.authRepository.clearAllLocalUserDataForUnauthenticatedState()
            authenticated = false
            requiresServerSetup = true
            requiresLogin = false
            serverURL = nil
            user = nil
            error = nil
            canResetServerTrust = false
            pendingApprovalMessage = nil
            adminAiSummaryEnabled = nil
            isAdminAiSummaryLoading = false
            isAdminAiSummarySaving = false
            adminAiSummaryError = nil
            pendingMutationCount = 0
            isOffline = false
            finishBootstrap()
            stopRealtime()
            stopSyncLoop()
            return
        }

        serverURL = container.serverConfigRepository.getServerURL()?.absoluteString
        let sessionResult = await container.bootstrapSession()
        if let sessionResult, sessionResult.user.id != nil {
            let session = sessionResult.user
            let versionResult = await container.serverConfigRepository.recheckVersion()
            versionCheckResult = versionResult
            switch versionResult {
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
            user = session
            error = nil
            pendingApprovalMessage = nil
            canResetServerTrust = true
            isOffline = sessionResult.isOffline
            if sessionResult.isOffline && shouldShowOfflineNotice() {
                offlineNoticeID += 1
            }
            finishBootstrap()
            await refreshAdminAiSummarySetting()
            refreshPendingMutationCount()
            startRealtime()
            startSyncLoop()
            await container.reminderScheduler.requestAuthorization()
            await rescheduleReminders()
            await refreshVersionInfo()
            return
        }

        container.authRepository.clearSessionOnly()
        authenticated = false
        requiresServerSetup = false
        requiresLogin = true
        user = nil
        error = nil
        pendingApprovalMessage = nil
        canResetServerTrust = true
        adminAiSummaryEnabled = nil
        isAdminAiSummaryLoading = false
        isAdminAiSummarySaving = false
        adminAiSummaryError = nil
        refreshPendingMutationCount()
        finishBootstrap()
        stopRealtime()
        stopSyncLoop()
    }

    func refreshSession() async {
        await bootstrap()
    }

    func connectServer(rawURL: String) async -> Result<Void, MessageError> {
        do {
            let probeResult = try await container.serverConfigRepository.probeAndSave(rawURL)
            serverURL = probeResult.serverURL
            versionCheckResult = probeResult.versionCheck
            backendVersion = probeResult.backendVersion
            let isBlocking = probeResult.versionCheck != .compatible
            requiresServerSetup = false
            requiresLogin = !isBlocking
            error = nil
            canResetServerTrust = true
            return .success(())
        } catch {
            let msg = serverConnectionMessage(for: error)
            self.error = msg
            canResetServerTrust = shouldOfferServerTrustReset(for: error)
            return .failure(MessageError(message: msg))
        }
    }

    func recheckVersion() async {
        let result = await container.serverConfigRepository.recheckVersion()
        versionCheckResult = result
        switch result {
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

    func refreshAdminAiSummarySetting() async {
        guard isAdmin(user) else {
            adminAiSummaryEnabled = nil
            isAdminAiSummaryLoading = false
            isAdminAiSummarySaving = false
            adminAiSummaryError = nil
            return
        }

        isAdminAiSummaryLoading = true
        adminAiSummaryError = nil
        adminAiSummaryEnabled = adminAiSummaryEnabled ?? container.settingsRepository.isAiSummaryEnabledSnapshot()
        do {
            adminAiSummaryEnabled = try await container.settingsRepository.fetchAdminAiSummaryEnabled()
            adminAiSummaryError = nil
        } catch {
            adminAiSummaryError = userFacingMessage(for: error, fallback: "Could not load admin settings.")
        }
        isAdminAiSummaryLoading = false
    }

    func setAdminAiSummaryEnabled(_ enabled: Bool) async {
        guard isAdmin(user), !isAdminAiSummarySaving else {
            return
        }
        isAdminAiSummarySaving = true
        adminAiSummaryError = nil
        adminAiSummaryEnabled = enabled
        do {
            let response = try await container.settingsRepository.updateAdminAiSummaryEnabled(enabled)
            adminAiSummaryEnabled = response.aiSummaryEnabled
            aiSummaryValidationError = response.validationError
        } catch {
            adminAiSummaryError = userFacingMessage(for: error, fallback: "Could not update admin settings.")
            await refreshAdminAiSummarySetting()
        }
        isAdminAiSummarySaving = false
    }

    func dismissAiSummaryValidationError() {
        aiSummaryValidationError = nil
    }

    func setThemeMode(_ mode: AppThemeMode) {
        themeMode = mode
        container.themeStore.save(mode)
    }

    func setDefaultReminder(_ option: ReminderOption) {
        selectedReminder = option
        container.reminderPreferenceStore.setDefaultReminder(option)
        Task {
            await self.rescheduleReminders()
        }
    }

    func manualSync() async {
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
        guard authenticated, !isForegroundReconnectInFlight else {
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
                    self.refreshPendingMutationCount()
                }
            }
        }
    }

    private func refreshPendingMutationCount() {
        pendingMutationCount = container.cacheManager.loadOfflineState().pendingMutations.count
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
            refreshPendingMutationCount()
        case let .failure(error):
            isOffline = isLikelyConnectivityIssue(error) ||
                (suppressAuthenticationExpired && isSessionAuthenticationIssue(error))
            if isOffline && showOfflineNotice && shouldShowOfflineNotice() {
                offlineNoticeID += 1
            }
            if !isOffline {
                container.snackbarManager.show(message: userFacingMessage(for: error))
            }
            refreshPendingMutationCount()
        }
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
                    self.refreshPendingMutationCount()
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

    private func isAdmin(_ user: SessionUser?) -> Bool {
        user?.role?.uppercased() == "ADMIN"
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
        await recheckVersion()
        await refreshGitHubReleases()
    }

    func checkForUpdate() async {
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
}
