import Foundation
import Observation

@MainActor
@Observable
final class AppViewModel {
    let container: AppContainer

    var loading = true
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
    var navigationPath: [AppRoute] = []

    private var cacheObservationTask: Task<Void, Never>?
    private var syncLoopTask: Task<Void, Never>?

    init(container: AppContainer) {
        self.container = container
        themeMode = container.themeStore.load()
        selectedReminder = container.reminderPreferenceStore.getDefaultReminder()
        observeCacheChanges()
    }

    deinit {
        cacheObservationTask?.cancel()
        syncLoopTask?.cancel()
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
            loading = false
            stopRealtime()
            stopSyncLoop()
            return
        }

        serverURL = container.serverConfigRepository.getServerURL()?.absoluteString
        let session = await container.bootstrapSession()
        if let session, session.id != nil {
            authenticated = true
            requiresServerSetup = false
            requiresLogin = false
            user = session
            error = nil
            pendingApprovalMessage = nil
            canResetServerTrust = true
            isOffline = false
            loading = false
            await refreshAdminAiSummarySetting()
            refreshPendingMutationCount()
            startRealtime()
            startSyncLoop()
            await container.reminderScheduler.requestAuthorization()
            await rescheduleReminders()
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
        loading = false
        stopRealtime()
        stopSyncLoop()
    }

    func refreshSession() async {
        await bootstrap()
    }

    func connectServer(rawURL: String) async -> Result<Void, String> {
        do {
            serverURL = try await container.serverConfigRepository.saveServerURL(rawURL)
            requiresServerSetup = false
            requiresLogin = true
            error = nil
            canResetServerTrust = true
            return .success(())
        } catch {
            error = error.localizedDescription
            canResetServerTrust = true
            return .failure(error.localizedDescription)
        }
    }

    func resetTrustedServer(rawURL: String) async -> Result<Void, String> {
        do {
            _ = try await container.serverConfigRepository.resetTrustedServer(rawURL: rawURL)
            serverURL = container.serverConfigRepository.getServerURL()?.absoluteString
            requiresServerSetup = false
            requiresLogin = true
            error = nil
            return .success(())
        } catch {
            error = error.localizedDescription
            return .failure(error.localizedDescription)
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
            adminAiSummaryError = error.localizedDescription
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
            adminAiSummaryError = error.localizedDescription
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
        let result = await container.syncAndRefresh(force: true, replayPendingMutations: true)
        isManualSyncing = false
        applySyncResult(result)
        await rescheduleReminders()
    }

    func logout() async {
        await container.authRepository.logout()
        navigationPath = []
        await bootstrap()
    }

    func navigate(to route: AppRoute) {
        switch route {
        case .home:
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
                let result = await self.container.syncAndRefresh(force: false, replayPendingMutations: true)
                await MainActor.run {
                    self.applySyncResult(result)
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
                let result = await self.container.syncAndRefresh(force: true, replayPendingMutations: true)
                await MainActor.run {
                    self.applySyncResult(result)
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

    private func applySyncResult(_ result: Result<Void, Error>) {
        switch result {
        case .success:
            isOffline = false
            refreshPendingMutationCount()
        case let .failure(error):
            isOffline = isLikelyConnectivityIssue(error)
            if !isOffline {
                container.snackbarManager.show(message: error.localizedDescription)
            }
            refreshPendingMutationCount()
        }
    }

    private func isAdmin(_ user: SessionUser?) -> Bool {
        user?.role?.uppercased() == "ADMIN"
    }

    private func rescheduleReminders() async {
        let tasks = container.todoRepository.fetchTodosSnapshot(mode: .all)
        await container.reminderScheduler.reschedule(tasks: tasks, defaultReminder: selectedReminder)
    }
}
