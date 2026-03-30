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

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
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
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/ohmzi/Tday/releases/latest"
    }
}
