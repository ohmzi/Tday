package com.ohmz.tday.compose.core.data.export

import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.data.sync.SyncManager
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.shared.model.ImportRequest
import com.ohmz.tday.shared.model.ImportResponse
import com.ohmz.tday.shared.model.TdayExport
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Your data" export/import. Export works in both modes (Server Mode pulls the
 * backend's complete bundle; Local Mode builds a best-effort one from the
 * cache). Import goes through the server's `/api/import`, so it's a Server-Mode
 * action — which is exactly the Local→Server migration: export locally, sign
 * in to a server, import there.
 */
@Singleton
class DataTransferRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val syncManager: SyncManager,
    private val secureConfigStore: SecureConfigStore,
    private val json: Json,
) {
    fun isLocalMode(): Boolean = secureConfigStore.isLocalMode()

    suspend fun buildExportJson(): String {
        val export = if (secureConfigStore.isLocalMode()) {
            LocalExportMapper.toExport(
                state = cacheManager.loadOfflineState(),
                source = "local-android",
                exportedAtEpochMs = System.currentTimeMillis(),
            )
        } else {
            requireApiBody(api.getExport(), "Could not export your data")
        }
        return json.encodeToString(TdayExport.serializer(), export)
    }

    /**
     * Applies a bundle through the server. [dryRun] returns the preview counts
     * without writing; a real run also pulls the merged server state back into
     * the cache so every screen refreshes.
     */
    suspend fun importJson(rawJson: String, dryRun: Boolean): ImportResponse {
        val bundle = json.decodeFromString(TdayExport.serializer(), rawJson)
        val response = requireApiBody(
            api.postImport(ImportRequest(export = bundle, dryRun = dryRun)),
            "Could not import that file",
        )
        if (!dryRun) {
            syncManager.syncCachedData(force = true, replayPendingMutations = true)
        }
        return response
    }
}
