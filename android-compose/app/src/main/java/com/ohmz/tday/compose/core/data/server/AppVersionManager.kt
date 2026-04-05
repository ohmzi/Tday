package com.ohmz.tday.compose.core.data.server

import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.feature.release.GitHubRelease
import com.ohmz.tday.compose.feature.release.GitHubReleaseRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVersionManager @Inject constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val gitHubReleaseRepository: GitHubReleaseRepository,
) {
    data class VersionState(
        val backendVersion: String? = null,
        val versionCheckResult: VersionCheckResult = VersionCheckResult.Compatible,
        val requiredUpdateRelease: GitHubRelease? = null,
        val isCheckingUpdateRelease: Boolean = false,
        val latestRelease: GitHubRelease? = null,
        val currentRelease: GitHubRelease? = null,
        val isLoadingReleases: Boolean = true,
        val releaseError: String? = null,
    )

    private val _state = MutableStateFlow(VersionState())
    val state: StateFlow<VersionState> = _state.asStateFlow()

    /**
     * Probes the server for version compatibility, then checks GitHub
     * for the required release APK when an app update is needed.
     */
    suspend fun refreshServerCompatibility() {
        val recheckResult = runCatching { serverConfigRepository.recheckVersion() }
            .getOrNull() ?: return
        applyServerCompatibility(recheckResult.versionCheck, recheckResult.serverAppVersion)
    }

    /**
     * Applies an externally-obtained version check result (e.g. from a
     * [ServerConfigRepository.probeAndSave] call) and performs the
     * required-release lookup when an app update is needed.
     */
    suspend fun applyServerCompatibility(
        versionCheck: VersionCheckResult,
        backendVersion: String?,
    ) {
        _state.update {
            it.copy(
                versionCheckResult = versionCheck,
                backendVersion = backendVersion ?: it.backendVersion,
            )
        }
        if (versionCheck is VersionCheckResult.AppUpdateRequired) {
            val previousRequired = (_state.value.versionCheckResult
                as? VersionCheckResult.AppUpdateRequired)?.requiredVersion
            val versionChanged = previousRequired != versionCheck.requiredVersion
            _state.update {
                it.copy(
                    isCheckingUpdateRelease = true,
                    requiredUpdateRelease = if (versionChanged) null else it.requiredUpdateRelease,
                )
            }
            val release = runCatching {
                gitHubReleaseRepository.fetchReleaseByTag("v${versionCheck.requiredVersion}")
            }.getOrNull()
            _state.update {
                it.copy(isCheckingUpdateRelease = false, requiredUpdateRelease = release)
            }
        } else {
            _state.update {
                it.copy(isCheckingUpdateRelease = false, requiredUpdateRelease = null)
            }
        }
    }

    suspend fun refreshGitHubReleases() {
        _state.update { it.copy(isLoadingReleases = true, releaseError = null) }
        val currentTag = "v${BuildConfig.VERSION_NAME}"
        coroutineScope {
            val latestResult = async { runCatching { gitHubReleaseRepository.fetchLatestRelease() } }
            val currentResult = async { runCatching { gitHubReleaseRepository.fetchReleaseByTag(currentTag) } }
            val latest = latestResult.await()
            val current = currentResult.await()
            _state.update {
                it.copy(
                    isLoadingReleases = false,
                    latestRelease = latest.getOrNull(),
                    currentRelease = current.getOrNull(),
                    releaseError = latest.exceptionOrNull()?.message,
                )
            }
        }
    }

    suspend fun refreshAll() = coroutineScope {
        launch { refreshServerCompatibility() }
        launch { refreshGitHubReleases() }
    }
}
