package com.ohmz.tday.compose.core.observability

import io.sentry.Breadcrumb
import io.sentry.SentryEvent

/**
 * The one place that decides what a report may say. Pure functions over the SDK's own event types,
 * so the privacy promise in the FAQ is a unit test (see `TelemetryPrivacyGoldenTest`) rather than
 * a reading of `SentryAndroid.init`.
 *
 * Two rules shape everything below. Allow, don't deny: a breadcrumb category or an event context
 * this file has not heard of is dropped, so a future SDK release that starts recording something
 * new records nothing until someone decides it is safe. And redact by shape rather than by the
 * values the app happens to know: an exception message is free text written by a library, and the
 * user's server address, a list id or an email reaches it in ways no list of fields can enumerate.
 */
object TelemetryScrubber {

    /** Past this a message is a payload, not a diagnosis. */
    private const val MAX_TEXT_LENGTH = 300

    private val ALLOWED_CONTEXTS = setOf("app", "device", "os", "runtime", "trace", "art")

    /** `http` and `navigation` are reduced to structure below; the rest are structural already. */
    private val ALLOWED_BREADCRUMB_CATEGORIES = setOf(
        "http",
        "navigation",
        "tday",
        "error",
        "app.lifecycle",
        "network.event",
    )

    private val HTTP_BREADCRUMB_KEYS = setOf("method", "status_code", "route")
    private val NAVIGATION_BREADCRUMB_KEYS = setOf("from", "to")

    // Domains a self-hoster is likely to serve T'Day from. A host with no dot ("nas") is caught by
    // the contextual rules below instead, and the list stops short of file extensions (.so, .md)
    // and Kotlin members (.id, .it, .is) that would otherwise read as hosts.
    private const val HOST_SUFFIXES =
        "com|net|org|io|dev|app|me|xyz|info|biz|cloud|tech|online|site|link|lan|local|localdomain|home|" +
            "internal|example|test|localhost|arpa|de|uk|fr|nl|eu|us|ca|au|jp|cn|br|ru|es|ch|se|fi|dk|pl|" +
            "cz|nz|za|kr|tw|hk|sg|ie|mx|tr|ua|il|co"

    private const val HEX = "[0-9A-Fa-f]"

    /**
     * Applied in this order: the structured shapes (urls, quoted values, certificate text) go first
     * so the generic ones do not take half of them and leave the rest looking harmless.
     */
    private val TEXT_RULES: List<Pair<Regex, String>> = listOf(
        Regex("jdbc:\\S+", RegexOption.IGNORE_CASE) to "[url]",
        Regex("\\b[a-z][a-z0-9+.-]*://[^\\s\"'<>)\\]]+", RegexOption.IGNORE_CASE) to "[url]",
        Regex("Key \\([^)]*\\)=\\([^)]*\\)") to "Key ([redacted])=([redacted])",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+") to "[email]",
        Regex("sha(?:1|256)/[A-Za-z0-9+/=]{6,}", RegexOption.IGNORE_CASE) to "[pin]",
        Regex("(DN:\\s*)[^\\n]*", RegexOption.IGNORE_CASE) to "$1[redacted]",
        Regex("(subjectAltNames:\\s*)\\[[^\\]]*]", RegexOption.IGNORE_CASE) to "$1[host]",
        Regex("(unable to resolve host\\s+)\"[^\"]*\"", RegexOption.IGNORE_CASE) to "$1\"[host]\"",
        Regex("(UnknownHostException:\\s*)\\S+") to "$1[host]",
        Regex("(failed to connect to\\s+)\\S+", RegexOption.IGNORE_CASE) to "$1[host]",
        Regex("(hostname\\s+)\\S+(?=\\s+not verified)", RegexOption.IGNORE_CASE) to "$1[host]",
        Regex(
            "(?<![\\w:.])(?:(?:$HEX{1,4}:){7}$HEX{1,4}|" +
                "(?:$HEX{1,4}:){0,6}$HEX{0,4}::(?:$HEX{1,4}:){0,6}$HEX{0,4})(?![\\w:])",
        ) to "[ip]",
        Regex("(?<![\\w.])\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d{1,5})?(?!\\w|\\.\\d)") to "[ip]",
        Regex(
            "(?<![\\w.@-])(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+(?:$HOST_SUFFIXES)(?![\\w-]|\\.[a-z0-9])",
            RegexOption.IGNORE_CASE,
        ) to "[host]",
        Regex("$HEX{8}-$HEX{4}-$HEX{4}-$HEX{4}-$HEX{12}") to "[id]",
        // cuids, 32 character install ids and anything else that is long, id-shaped and numeric
        // enough not to be a word. Three digits keeps "libsentry-android-ndk-8" a library name.
        Regex("(?<![A-Za-z0-9_-])(?=[A-Za-z0-9_-]{20,}(?![A-Za-z0-9_-]))(?=(?:[A-Za-z_-]*\\d){3})[A-Za-z0-9_-]{20,}") to "[id]",
        Regex("(?<!\\d)\\d{7,}(?!\\d)") to "[number]",
    )

    /** Replaces everything in [text] that identifies a person, a server or a record. */
    fun scrubText(text: String): String {
        val redacted = TEXT_RULES.fold(text) { current, (pattern, replacement) ->
            pattern.replace(current, replacement)
        }
        return if (redacted.length > MAX_TEXT_LENGTH) redacted.take(MAX_TEXT_LENGTH) + "…" else redacted
    }

    /**
     * Strips an event down to what the FAQ lists, then stamps [tags] on it.
     *
     * Tags are only added where the event does not already carry one: a replayed ANR comes from an
     * earlier process, and what it recorded then is closer to the truth than what is true now.
     */
    fun scrub(event: SentryEvent, tags: Map<String, String>): SentryEvent {
        // The SDK fills the user with a random per-install id and, with `{{auto}}`, the sender's
        // IP. Nulling the whole user takes both, and anything else a later SDK adds to it.
        event.user = null
        event.serverName = null
        event.request = null

        val contexts = event.contexts
        contexts.keys().toList().filterNot { it in ALLOWED_CONTEXTS }.forEach { contexts.remove(it) }
        contexts.device?.let { device ->
            device.id = null
            device.name = null
            device.locale = null
            device.timezone = null
            device.bootTime = null
        }
        contexts.app?.permissions = null

        event.exceptions?.forEach { exception -> exception.value = exception.value?.let(::scrubText) }
        event.message?.let { message ->
            message.message = message.message?.let(::scrubText)
            message.formatted = message.formatted?.let(::scrubText)
            message.params = message.params?.map(::scrubText)
        }
        event.extras?.entries?.toList()?.forEach { (key, value) ->
            if (value is String) event.setExtra(key, scrubText(value))
        }

        tags.forEach { (key, value) -> if (event.getTag(key) == null) event.setTag(key, value) }
        return event
    }

    /** The breadcrumb reduced to structure, or `null` if its category is not on the allow list. */
    fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb? {
        val category = breadcrumb.category ?: return null
        if (category !in ALLOWED_BREADCRUMB_CATEGORIES) return null

        breadcrumb.message = breadcrumb.message?.let(::scrubText)
        when (category) {
            "http" -> reduceHttp(breadcrumb)
            "navigation" -> reduceNavigation(breadcrumb)
        }
        return breadcrumb
    }

    /** Method, status and the path with ids and the host taken out; never the host, query or sizes. */
    private fun reduceHttp(breadcrumb: Breadcrumb) {
        val route = (breadcrumb.data["route"] ?: breadcrumb.data["url"])
            ?.toString()
            ?.let(TdayTelemetry::sanitizePath)
        keepOnly(breadcrumb, HTTP_BREADCRUMB_KEYS)
        route?.let { breadcrumb.setData("route", it) }
    }

    /** The two route templates; the SDK's `*_arguments` carry the real list ids and names. */
    private fun reduceNavigation(breadcrumb: Breadcrumb) {
        keepOnly(breadcrumb, NAVIGATION_BREADCRUMB_KEYS)
        NAVIGATION_BREADCRUMB_KEYS.forEach { key ->
            (breadcrumb.data[key] as? String)?.let { breadcrumb.setData(key, scrubText(it)) }
        }
    }

    private fun keepOnly(breadcrumb: Breadcrumb, keys: Set<String>) {
        breadcrumb.data.keys.toList().filterNot { it in keys }.forEach { breadcrumb.removeData(it) }
    }
}
