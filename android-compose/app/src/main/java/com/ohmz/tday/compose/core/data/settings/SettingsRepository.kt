package com.ohmz.tday.compose.core.data.settings

import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.AdminSettingsResponse
import com.ohmz.tday.compose.core.model.UpdateAdminSettingsRequest
import com.ohmz.tday.compose.core.network.TdayApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
) {
    fun isAiSummaryEnabledSnapshot(): Boolean {
        return cacheManager.loadOfflineState().aiSummaryEnabled
    }

    suspend fun refreshAiSummaryEnabled(): Boolean {
        return runCatching {
            val enabled = requireApiBody(
                api.getAppSettings(),
                "Could not load app settings",
            ).aiSummaryEnabled
            cacheManager.updateOfflineState { state ->
                if (state.aiSummaryEnabled == enabled) state else state.copy(aiSummaryEnabled = enabled)
            }
            enabled
        }.getOrElse {
            cacheManager.loadOfflineState().aiSummaryEnabled
        }
    }

    suspend fun fetchAdminAiSummaryEnabled(): Boolean {
        val enabled = requireApiBody(
            api.getAdminSettings(),
            "Could not load admin settings",
        ).aiSummaryEnabled
        cacheManager.updateOfflineState { state ->
            if (state.aiSummaryEnabled == enabled) state else state.copy(aiSummaryEnabled = enabled)
        }
        return enabled
    }

    suspend fun updateAdminAiSummaryEnabled(enabled: Boolean): AdminSettingsResponse {
        val response = requireApiBody(
            api.patchAdminSettings(
                UpdateAdminSettingsRequest(aiSummaryEnabled = enabled),
            ),
            "Could not update admin settings",
        )
        cacheManager.updateOfflineState { state ->
            if (state.aiSummaryEnabled == response.aiSummaryEnabled) state
            else state.copy(aiSummaryEnabled = response.aiSummaryEnabled)
        }
        return response
    }
}
