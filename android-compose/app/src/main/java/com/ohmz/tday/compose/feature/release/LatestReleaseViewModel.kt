package com.ohmz.tday.compose.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.data.server.AppVersionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LatestReleaseUiState(
    val isLoading: Boolean = true,
    val currentRelease: GitHubRelease? = null,
    val latestRelease: GitHubRelease? = null,
    val error: String? = null,
    val currentVersion: String = BuildConfig.VERSION_NAME,
) {
    val hasUpdate: Boolean
        get() {
            val remote = latestRelease?.version ?: return false
            return compareVersions(remote, currentVersion) > 0
        }
}

fun parseChangelog(body: String?): List<String> =
    body?.lineSequence()
        ?.filter { it.startsWith("* ") || it.startsWith("- ") }
        ?.map { it.removePrefix("* ").removePrefix("- ").trim() }
        ?.filter { it.isNotBlank() }
        ?.toList()
        ?: emptyList()

internal fun compareVersions(a: String, b: String): Int {
    val aParts = a.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val bParts = b.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(aParts.size, bParts.size)
    for (i in 0 until maxLen) {
        val av = aParts.getOrElse(i) { 0 }
        val bv = bParts.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

@HiltViewModel
class LatestReleaseViewModel @Inject constructor(
    private val appVersionManager: AppVersionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LatestReleaseUiState())
    val uiState: StateFlow<LatestReleaseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appVersionManager.state.collect { vs ->
                _uiState.update {
                    it.copy(
                        isLoading = vs.isLoadingReleases,
                        currentRelease = vs.currentRelease,
                        latestRelease = vs.latestRelease,
                        error = vs.releaseError,
                    )
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            appVersionManager.refreshGitHubReleases()
        }
    }
}
