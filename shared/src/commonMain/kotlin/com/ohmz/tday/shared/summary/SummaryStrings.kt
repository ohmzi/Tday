package com.ohmz.tday.shared.summary

/**
 * Localized phrase bundle for the deterministic summary engine. The per-locale
 * bundles live in [SummaryStringBundles] (`SummaryStringBundlesGenerated.kt`),
 * generated from the web `summary` namespace by `./gradlew :shared:exportGuideContent`
 * and kept fresh in CI by `./gradlew :shared:verifyGuideContent`.
 */
internal data class SummaryStrings(
    val values: Map<String, String>,
    val monthsShort: List<String>,
) {
    fun t(key: String, params: Map<String, String> = emptyMap()): String {
        var out = values[key] ?: SummaryStringBundles.en.values[key] ?: key
        for ((k, v) in params) out = out.replace("{{" + k + "}}", v)
        return out
    }
}
