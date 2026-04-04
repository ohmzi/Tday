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
        val recheckResult = runCatching { serverConfigRepository.recheckVersion() }.getOrNull()
        val versionResult = recheckResult?.versionCheck ?: VersionCheckResult.Compatible
        applyServerCompatibility(versionResult, recheckResult?.serverAppVersion)
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
            _state.update { it.copy(isCheckingUpdateRelease = true, requiredUpdateRelease = null) }
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
        try {
            val currentTag = "v${BuildConfig.VERSION_NAME}"
            coroutineScope {
                val latestDeferred = async { gitHubReleaseRepository.fetchLatestRelease() }
                val currentDeferred = async { gitHubReleaseRepository.fetchReleaseByTag(currentTag) }
                _state.update {
                    it.copy(
                        isLoadingReleases = false,
                        latestRelease = latestDeferred.await(),
                        currentRelease = currentDeferred.await(),
                    )
                }
            }
        } catch (e: java.io.IOException) {
            _state.update {
                it.copy(
                    isLoadingReleases = false,
                    releaseError = e.message ?: "Network error fetching release",
                )
            }
        } catch (e: RuntimeException) {
            _state.update {
                it.copy(
                    isLoadingReleases = false,
                    releaseError = e.message ?: "Failed to fetch release",
                )
            }
        }
    }

    suspend fun refreshAll() = coroutineScope {
        launch { refreshServerCompatibility() }
        launch { refreshGitHubReleases() }
    }
}
