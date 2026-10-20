package com.ohmz.tday.observability

private val semver = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}""")

/**
 * Tags that tie a backend error to the app build that triggered it.
 *
 * Both headers are caller-controlled text, so only a closed set of platform names and a plain
 * `major.minor.patch` are accepted; anything else is left untagged rather than echoed into Sentry.
 */
internal fun clientTags(clientHeader: String?, versionHeader: String?): Map<String, String> = buildMap {
    when (clientHeader?.trim()?.lowercase()) {
        "web" -> put("client.platform", "web")
        "ios" -> put("client.platform", "ios")
        "android-compose" -> put("client.platform", "android")
    }
    versionHeader?.trim()?.takeIf(semver::matches)?.let { put("client.version", it) }
}
