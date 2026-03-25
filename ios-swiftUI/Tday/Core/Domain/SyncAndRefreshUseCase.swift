import Foundation

struct SyncAndRefreshUseCase {
    private let syncManager: SyncManager

    init(syncManager: SyncManager) {
        self.syncManager = syncManager
    }

    func callAsFunction(force: Bool, replayPendingMutations: Bool) async -> Result<Void, Error> {
        await syncManager.syncCachedData(force: force, replayPendingMutations: replayPendingMutations)
    }
}
