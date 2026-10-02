package com.ohmz.tday.compose.core.observability

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.SentryStackTrace

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

    /**
     * How much of a message the rules read. Several of them are quadratic on a long unbroken run
     * (the email one most of all), and they run on the thread that is crashing: a base64 blob in an
     * exception message must not stall it. Far above [MAX_TEXT_LENGTH], so everything that can
     * survive the cut has still been through every rule.
     */
    private const val MAX_SCANNED_LENGTH = 2000

    /** A thread name a person could not have put an address in: short, plain words and separators. */
    private val SAFE_THREAD_NAME = Regex("^[A-Za-z0-9 _.:-]{1,48}$")

    private const val ROUTE_KEY = "route"

    /** A rule that keeps the text before the match (group 1) and hides what follows it. */
    private const val KEEP_PREFIX_REDACTED = "$1[redacted]"
    private const val KEEP_PREFIX_HOST = "$1[host]"

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

    private val HTTP_BREADCRUMB_KEYS = setOf("method", "status_code", ROUTE_KEY)
    private val NAVIGATION_BREADCRUMB_KEYS = setOf("from", "to")

    // Domains a self-hoster is likely to serve T'Day from. A host with no dot ("nas") is caught by
    // the contextual rules below instead, and the list stops short of the suffixes that are also
    // file extensions or Kotlin members (.so, .md, .sh, .py, .rs, .kt, .ts, .cc, .id, .is, .to) and
    // would otherwise read stack frames and file names as hosts. "ts.net" needs no entry: "net" has it.
    private const val HOST_SUFFIXES =
        "com|net|org|info|biz|io|dev|app|page|me|ai|xyz|cloud|tech|online|site|link|lan|local|" +
            "localdomain|home|internal|intranet|corp|example|test|localhost|arpa|co|de|uk|fr|nl|eu|us|" +
            "ca|au|jp|cn|br|ru|es|ch|se|fi|dk|pl|cz|nz|za|kr|tw|hk|sg|ie|mx|tr|ua|il|it|at|be|pt|gr|" +
            "hu|ro|bg|sk|si|hr|lt|lv|ee"

    private const val HEX = "[0-9A-Fa-f]"

    /**
     * Applied in this order: the structured shapes (urls, quoted values, certificate text) go first
     * so the generic ones do not take half of them and leave the rest looking harmless.
     */
    private val TEXT_RULES: List<Pair<Regex, String>> = listOf(
        // Text a library quotes back from what it was parsing: kotlinx.serialization's "JSON input:"
        // (the whole payload, or a window around the failure), java.time's "Text '...' could not be
        // parsed" and the number parsers' "For input string:". That is the user's own words.
        Regex("(JSON input:\\s*)[\\s\\S]*") to KEEP_PREFIX_REDACTED,
        Regex("(Text\\s+)'[\\s\\S]*?'(?=\\s+could not be parsed)") to "$1'[redacted]'",
        Regex("(For input string:\\s*)[^\\n]*") to KEEP_PREFIX_REDACTED,
        Regex("jdbc:\\S+", RegexOption.IGNORE_CASE) to "[url]",
        Regex("\\b[a-z][a-z0-9+.-]*://[^\\s\"'<>)\\]]+", RegexOption.IGNORE_CASE) to "[url]",
        Regex("Key \\([^)]*\\)=\\([^)]*\\)") to "Key ([redacted])=([redacted])",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+") to "[email]",
        Regex("sha(?:1|256)/[A-Za-z0-9+/=]{6,}", RegexOption.IGNORE_CASE) to "[pin]",
        Regex("(DN:\\s*)[^\\n]*", RegexOption.IGNORE_CASE) to KEEP_PREFIX_REDACTED,
        Regex("(subjectAltNames:\\s*)\\[[^\\]]*]", RegexOption.IGNORE_CASE) to KEEP_PREFIX_HOST,
        Regex("(unable to resolve host\\s+)\"[^\"]*\"", RegexOption.IGNORE_CASE) to "$1\"[host]\"",
        Regex("(UnknownHostException:\\s*)\\S+") to KEEP_PREFIX_HOST,
        Regex("(failed to connect to\\s+)\\S+", RegexOption.IGNORE_CASE) to KEEP_PREFIX_HOST,
        Regex("(hostname\\s+)\\S+(?=\\s+not verified)", RegexOption.IGNORE_CASE) to KEEP_PREFIX_HOST,
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
        val redacted = TEXT_RULES.fold(text.take(MAX_SCANNED_LENGTH)) { current, (pattern, replacement) ->
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

        event.exceptions?.forEach { exception ->
            exception.value = exception.value?.let(::scrubText)
            exception.stacktrace?.let(::scrubStackTrace)
        }
        // An ANR is built from the system's thread dump, so it brings every thread and every loaded
        // library along. OkHttp names its threads after the server it is talking to, and the
        // images' paths run through the install's random directory names.
        event.threads?.forEach { thread ->
            thread.name = thread.name?.let(::safeThreadName)
            thread.stacktrace?.let(::scrubStackTrace)
        }
        event.debugMeta?.images?.forEach { image ->
            image.codeFile = image.codeFile?.let(::fileName)
            image.debugFile = image.debugFile?.let(::fileName)
        }
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

    /**
     * A thread's name if it is plain words, else its first word if that is, else nothing. A name
     * that a rule would change is not plain: "OkHttp tday.example.com" becomes "OkHttp".
     */
    private fun safeThreadName(name: String): String? =
        sequenceOf(name, name.substringBefore(' '))
            .firstOrNull { SAFE_THREAD_NAME.matches(it) && "://" !in it && scrubText(it) == it }

    /** `/data/app/~~random==/com.ohmz.tday-random==/lib/arm64/libx.so` is `libx.so`. */
    private fun fileName(path: String): String = path.substringAfterLast('/')

    /** Native frames name the library by its install path; Java frames carry class and file names only. */
    private fun scrubStackTrace(stackTrace: SentryStackTrace) {
        stackTrace.frames?.forEach { frame ->
            frame.filename = frame.filename?.let(::fileName)
            frame.absPath = frame.absPath?.let(::fileName)
            frame.`package` = frame.`package`?.let(::fileName)
        }
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
        val route = (breadcrumb.data[ROUTE_KEY] ?: breadcrumb.data["url"])
            ?.toString()
            ?.let(TdayTelemetry::sanitizePath)
        keepOnly(breadcrumb, HTTP_BREADCRUMB_KEYS)
        route?.let { breadcrumb.setData(ROUTE_KEY, it) }
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
