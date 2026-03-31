package com.ohmz.tday.compose.feature.release

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubReleaseRepository @Inject constructor(
    private val json: Json,
) {
    private val client = OkHttpClient.Builder().build()

    suspend fun fetchLatestRelease(): GitHubRelease = fetchRelease(LATEST_RELEASE_URL)

    suspend fun fetchReleaseByTag(tag: String): GitHubRelease? =
        try {
            fetchRelease("$RELEASES_URL/tags/$tag")
        } catch (_: Exception) {
            null
        }

    private suspend fun fetchRelease(url: String): GitHubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("GitHub API returned ${response.code}")
        }
        val body = response.body?.string()
            ?: throw RuntimeException("Empty response body")
        json.decodeFromString<GitHubRelease>(body)
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/ohmzi/Tday/releases"
        private const val LATEST_RELEASE_URL = "$RELEASES_URL/latest"
    }
}
