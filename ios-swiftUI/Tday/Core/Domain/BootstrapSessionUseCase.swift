import Foundation

struct BootstrapSessionResult {
    let user: SessionUser
    let isOffline: Bool
}

struct BootstrapSessionUseCase {
    private let authRepository: AuthRepository
    private let syncManager: SyncManager

    init(authRepository: AuthRepository, syncManager: SyncManager) {
        self.authRepository = authRepository
        self.syncManager = syncManager
    }

    func callAsFunction() async -> BootstrapSessionResult? {
        guard let restored = await authRepository.restoreSessionForBootstrap(),
              restored.user.id != nil else {
            return nil
        }
        let connectionProbeTimeout: TimeInterval?
        if restored.usedCachedSession {
            connectionProbeTimeout = SyncAndRefreshUseCase.userRefreshConnectionTimeoutSeconds
        } else {
            connectionProbeTimeout = nil
        }
        let result = await syncManager.syncCachedData(
            force: true,
            replayPendingMutations: true,
            notifyOfflineFailure: false,
            connectionProbeTimeoutSeconds: connectionProbeTimeout
        )
        let syncError: Error?
        switch result {
        case .success:
            syncError = nil
        case .failure(let error):
            syncError = error
        }
        return BootstrapSessionResult(
            user: restored.user,
            isOffline: syncError.map(isLikelyConnectivityIssue) == true
        )
    }
}
