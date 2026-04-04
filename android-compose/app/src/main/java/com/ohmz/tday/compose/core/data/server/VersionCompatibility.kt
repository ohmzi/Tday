package com.ohmz.tday.compose.core.data.server

import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.security.ProbeCompatibilityPayload
import com.ohmz.tday.compose.feature.release.compareVersions

data class VersionCompatibility(
    val appVersion: String,
    val updateRequired: Boolean,
)

sealed class VersionCheckResult {
    data object Compatible : VersionCheckResult()

    data class AppUpdateRequired(
        val requiredVersion: String,
    ) : VersionCheckResult()

    data class ServerUpdateRequired(
        val serverVersion: String,
    ) : VersionCheckResult()
}

fun checkVersionCompatibility(payload: ProbeCompatibilityPayload?): VersionCheckResult {
    if (payload == null) return VersionCheckResult.Compatible

    val localVersion = BuildConfig.VERSION_NAME
    val serverAppVersion = payload.appVersion
    val cmp = compareVersions(localVersion, serverAppVersion)

    if (!payload.updateRequired) return VersionCheckResult.Compatible

    return when {
        cmp < 0 -> VersionCheckResult.AppUpdateRequired(requiredVersion = serverAppVersion)
        cmp > 0 -> VersionCheckResult.ServerUpdateRequired(serverVersion = serverAppVersion)
        else -> VersionCheckResult.Compatible
    }
}
