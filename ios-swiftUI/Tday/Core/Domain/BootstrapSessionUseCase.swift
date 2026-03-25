import Foundation

struct BootstrapSessionUseCase {
    private let authRepository: AuthRepository
    private let syncManager: SyncManager

    init(authRepository: AuthRepository, syncManager: SyncManager) {
        self.authRepository = authRepository
        self.syncManager = syncManager
    }

    func callAsFunction() async -> SessionUser? {
        let session = await authRepository.restoreSession()
        guard session?.id != nil else {
            return nil
        }
        _ = await syncManager.syncCachedData(force: true, replayPendingMutations: true)
        return session
    }
}
