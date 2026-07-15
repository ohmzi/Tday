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
        // Show the "can't reach server" notice whenever we're running degraded/offline.
        // If we had to fall back to a cached session, the server already failed to hand us
        // a fresh one this launch, so ANY subsequent sync failure means we're offline —
        // including a 5xx or a reverse-proxy error/maintenance page that isn't a strict
        // connectivity code. With a freshly fetched session, only a true connectivity error
        // counts as offline (a healthy server that merely hit a transient sync error is not).
        let isOffline: Bool
        if let syncError {
            isOffline = restored.usedCachedSession || isLikelyConnectivityIssue(syncError)
        } else {
            isOffline = false
        }
        return BootstrapSessionResult(user: restored.user, isOffline: isOffline)
    }
}
