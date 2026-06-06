package com.ohmz.tday.compose.core.data.settings

import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.cache.OfflineCacheManager
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.model.PreferencesDto
import com.ohmz.tday.compose.core.network.TdayApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val api: TdayApiService,
    private val cacheManager: OfflineCacheManager,
    private val secureConfigStore: SecureConfigStore,
) {
    /**
     * Per-user preference (default ON) that hides the AI-summary feature entirely.
     * In local mode the deterministic engine still runs on-device, so it stays on.
     */
    fun isAiSummaryEnabledSnapshot(): Boolean {
        if (secureConfigStore.isLocalMode()) return true
        return cacheManager.loadOfflineStateBlocking().aiSummaryEnabled
    }

    /** Whether the backend has an AI model configured (drives the source label). */
    fun aiSummaryConfiguredSnapshot(): Boolean = secureConfigStore.getAiSummaryConfigured()

    /** Whether the configured AI model is currently healthy. */
    fun aiSummaryHealthySnapshot(): Boolean = secureConfigStore.getAiSummaryHealthy()

    /**
     * Fetches `/app-settings` and persists the AI capability (configured/healthy) into
     * [SecureConfigStore]. On failure the previously-stored values are left untouched.
     */
    suspend fun refreshAiCapability() {
        if (secureConfigStore.isLocalMode()) return

        runCatching {
            requireApiBody(
                api.getAppSettings(),
                "Could not load app settings",
            )
        }.onSuccess { settings ->
            secureConfigStore.setAiSummaryConfigured(settings.aiSummaryConfigured)
            secureConfigStore.setAiSummaryHealthy(settings.aiSummaryHealthy)
        }
    }

    /**
     * Updates the per-user preference via `/api/preferences` and mirrors it into the
     * offline cache so the gating snapshot reflects the change immediately.
     */
    suspend fun setAiSummaryEnabled(enabled: Boolean) {
        if (secureConfigStore.isLocalMode()) {
            cacheManager.updateOfflineState { state ->
                if (state.aiSummaryEnabled == enabled) state else state.copy(aiSummaryEnabled = enabled)
            }
            return
        }

        val response = requireApiBody(
            api.patchPreferences(PreferencesDto(aiSummaryEnabled = enabled)),
            "Could not update preferences",
        )
        cacheManager.updateOfflineState { state ->
            if (state.aiSummaryEnabled == response.aiSummaryEnabled) state
            else state.copy(aiSummaryEnabled = response.aiSummaryEnabled)
        }
    }

    /**
     * Fetches `/api/preferences` and mirrors the per-user AI-summary preference into the
     * offline cache. On failure the cached value is left untouched.
     */
    suspend fun refreshAiSummaryPreference(): Boolean {
        if (secureConfigStore.isLocalMode()) return true

        return runCatching {
            val enabled = requireApiBody(
                api.getPreferences(),
                "Could not load preferences",
            ).aiSummaryEnabled
            cacheManager.updateOfflineState { state ->
                if (state.aiSummaryEnabled == enabled) state else state.copy(aiSummaryEnabled = enabled)
            }
            enabled
        }.getOrElse {
            cacheManager.loadOfflineState().aiSummaryEnabled
        }
    }
}
