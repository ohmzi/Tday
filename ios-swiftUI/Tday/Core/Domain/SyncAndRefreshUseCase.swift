import Foundation

struct SyncAndRefreshUseCase {
    private let syncManager: SyncManager
    // Reachability probe budget for user-initiated refresh, foreground reconnect, and
    // cached-session restore. 2s was too aggressive — a cold TLS handshake over cellular
    // or dual-stack IPv6 routinely exceeds it and falsely flips the app to "offline".
    // 8s leaves ample headroom while still failing fast when there is genuinely no route
    // (the OS surfaces "no network" errors immediately, independent of this timeout).
    static let userRefreshConnectionTimeoutSeconds: TimeInterval = 8

    init(syncManager: SyncManager) {
        self.syncManager = syncManager
    }

    func callAsFunction(
        force: Bool,
        replayPendingMutations: Bool,
        notifyOfflineFailure: Bool = true,
        userInitiated: Bool = false,
        connectionProbeTimeoutSeconds: TimeInterval? = nil
    ) async -> Result<Void, Error> {
        await syncManager.syncCachedData(
            force: force,
            replayPendingMutations: replayPendingMutations,
            notifyOfflineFailure: notifyOfflineFailure,
            userInitiated: userInitiated,
            connectionProbeTimeoutSeconds: connectionProbeTimeoutSeconds
        )
    }
}
