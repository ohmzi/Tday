package com.ohmz.tday.compose.core.data.cache

import com.ohmz.tday.compose.core.data.SecureConfigStore
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Has this install ever had an answer from its workspace at all" -- the one
 * term that separates a FIRST load from a REFRESH, read from the two places
 * that already know.
 *
 * Extracted rather than inlined into each `hydrateFromCache` because three
 * ViewModels need the identical sentence (`TodoListViewModel`,
 * `CompletedViewModel`, `CalendarViewModel`) and a rule copied three times is a
 * rule that will be corrected in one place. The correction it already needed is
 * [isLocalMode]: Local Mode has no server to sync with and deliberately keeps
 * `lastSuccessfulSyncEpochMs` at zero forever (see `SyncManager` and
 * `AppViewModel.enterLocalWorkspace`), so a sync stamp used alone would tell an
 * empty local workspace that its answer has never arrived -- a row skeleton that
 * never resolves, and an empty state that is never allowed to be said.
 */
@Singleton
class FirstAnswerSignal @Inject constructor(
    private val secureConfigStore: SecureConfigStore,
    private val offlineCacheManager: OfflineCacheManager,
) {

    /**
     * Bumps whenever the sync stamp below could have moved.
     *
     * Deliberately NOT [OfflineCacheManager.cacheDataVersion], and the
     * difference is the whole reason this flow is exposed here. That counter
     * only advances on `hasUiDataChanges` -- tasks, lists, completed items --
     * so the case this signal exists for, a first sync against an EMPTY
     * account, moves nothing it watches. The sync stamp is metadata, and
     * [OfflineCacheManager.syncMetadataVersion] is the counter that carries it.
     * Collect this or the very first answer on a fresh install with nothing in
     * it never arrives in UI state at all.
     */
    val version: StateFlow<Long> = offlineCacheManager.syncMetadataVersion

    /**
     * Synchronous, because it is read from inside the synchronous cache reads
     * that already establish `hasHydratedSnapshot` -- the flag has to be true on
     * the same frame the store read lands, or the gate that reads both sees a
     * hydrated feed with no answer and draws a skeleton for one frame.
     */
    fun hasLanded(): Boolean {
        if (secureConfigStore.isLocalMode()) return true
        return runCatching {
            offlineCacheManager.loadOfflineStateBlocking().lastSuccessfulSyncEpochMs
        }.getOrDefault(0L) > 0L
    }
}
