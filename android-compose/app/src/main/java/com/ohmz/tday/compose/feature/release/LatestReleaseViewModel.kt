package com.ohmz.tday.compose.feature.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ohmz.tday.compose.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
    private val repository: GitHubReleaseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LatestReleaseUiState())
    val uiState: StateFlow<LatestReleaseUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val currentTag = "v${BuildConfig.VERSION_NAME}"
                val latestDeferred = async { repository.fetchLatestRelease() }
                val currentDeferred = async { repository.fetchReleaseByTag(currentTag) }

                val latest = latestDeferred.await()
                val current = currentDeferred.await()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        latestRelease = latest,
                        currentRelease = current,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to fetch release")
                }
            }
        }
    }
}
