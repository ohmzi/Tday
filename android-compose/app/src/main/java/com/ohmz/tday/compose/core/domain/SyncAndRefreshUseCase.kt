package com.ohmz.tday.compose.core.domain

import com.ohmz.tday.compose.core.data.sync.SyncManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncAndRefreshUseCase @Inject constructor(
    private val syncManager: SyncManager,
) {
    suspend operator fun invoke(
        force: Boolean = true,
        replayPendingMutations: Boolean = false,
    ): Result<Unit> {
        return syncManager.syncCachedData(
            force = force,
            replayPendingMutations = replayPendingMutations,
        )
    }
}
