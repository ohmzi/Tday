package com.ohmz.tday.compose.feature.release

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList(),
) {
    val version: String get() = tagName.removePrefix("v")

    val apkAsset: GitHubAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk") }
}

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
    @SerialName("download_count") val downloadCount: Int = 0,
)
