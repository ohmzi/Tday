import Foundation

struct SyncAndRefreshUseCase {
    private let syncManager: SyncManager
    static let userRefreshConnectionTimeoutSeconds: TimeInterval = 2

    init(syncManager: SyncManager) {
        self.syncManager = syncManager
    }

    func callAsFunction(
        force: Bool,
        replayPendingMutations: Bool,
        notifyOfflineFailure: Bool = true,
        connectionProbeTimeoutSeconds: TimeInterval? = nil
    ) async -> Result<Void, Error> {
        await syncManager.syncCachedData(
            force: force,
            replayPendingMutations: replayPendingMutations,
            notifyOfflineFailure: notifyOfflineFailure,
            connectionProbeTimeoutSeconds: connectionProbeTimeoutSeconds
        )
    }
}
