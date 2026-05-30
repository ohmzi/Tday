package com.ohmz.tday.compose.core.observability

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object TdayTelemetry {
    private val staticSegments = setOf(
        "api",
        "app",
        "auth",
        "callback",
        "credentials",
        "credentials-key",
        "csrf",
        "logout",
        "register",
        "session",
        "todo",
        "todos",
        "today",
        "overdue",
        "scheduled",
        "all",
        "priority",
        "instance",
        "complete",
        "uncomplete",
        "prioritize",
        "reorder",
        "summary",
        "nlp",
        "list",
        "floater",
        "floaterList",
        "completedTodo",
        "completedFloater",
        "completed",
        "calendar",
        "settings",
        "latest-release",
        "app-settings",
        "preferences",
        "user",
        "profile",
        "change-password",
        "timezone",
        "mobile",
        "probe",
        "admin",
        "ws",
        "health",
    )

    private val routeLikeDataKeys = setOf("route", "path", "url", "href", "from", "to", "endpoint")
    private val sensitiveDataKeyPattern = Regex(
        "(authorization|cookie|csrf|token|password|session|secret|email|body|payload|header)",
        RegexOption.IGNORE_CASE,
    )
    private val sensitiveLabelPattern = Regex(
        "(https?://|wss?://|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|bearer\\s+|token=|password=|session=|cookie=|csrf)",
        RegexOption.IGNORE_CASE,
    )
    private val tokenLikeLabelPattern = Regex("^[A-Za-z0-9_.:-]+$")

    fun traceSampleRate(rawValue: String?, fallback: Double): Double {
        val parsed = rawValue
            ?.takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()
            ?: fallback
        return parsed.coerceIn(0.0, 1.0)
    }

    fun sanitizePath(raw: String): String {
        val noQuery = raw.substringBefore('?').substringBefore('#')
        val path = if ("://" in noQuery) {
            val withoutScheme = noQuery.substringAfter("://")
            val slashIndex = withoutScheme.indexOf('/')
            if (slashIndex >= 0) withoutScheme.substring(slashIndex) else "/"
        } else {
            noQuery
        }
        val segments = path.split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return "/"
        return segments.joinToString(prefix = "/", separator = "/") { sanitizeSegment(it) }
    }

    fun safeLabel(value: Any?): String {
        val raw = value?.toString()?.trim().orEmpty()
        if (raw.isBlank()) return "unknown"
        if (sensitiveLabelPattern.containsMatchIn(raw)) return "redacted"
        if (raw.length > 24 && raw.any(Char::isDigit) && tokenLikeLabelPattern.matches(raw)) return "id"
        return raw.replace(Regex("[^A-Za-z0-9_.:-]"), "_")
            .take(64)
            .ifBlank { "unknown" }
    }

    fun addBreadcrumb(
        operation: String,
        category: String = "tday",
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any?> = emptyMap(),
    ) {
        val breadcrumb = Breadcrumb().apply {
            this.category = category
            this.message = safeLabel(operation)
            this.level = level
        }
        data.forEach { (key, value) ->
            breadcrumb.setData(key, safeDataValue(key, value))
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    fun capture(error: Throwable, operation: String, data: Map<String, Any?> = emptyMap()) {
        Sentry.withScope { scope ->
            scope.setTag("tday.operation", safeLabel(operation))
            data.forEach { (key, value) -> scope.setExtra(key, safeDataValue(key, value).toString()) }
            Sentry.captureException(error)
        }
    }

    fun safeDataValue(key: String, value: Any?): Any {
        if (sensitiveDataKeyPattern.containsMatchIn(key)) return "redacted"
        return when (value) {
            null -> "null"
            is Number, is Boolean -> value
            is String -> if (key.lowercase() in routeLikeDataKeys) sanitizePath(value) else safeLabel(value)
            else -> safeLabel(value)
        }
    }

    private fun sanitizeSegment(segment: String): String {
        val decoded = runCatching {
            java.net.URLDecoder.decode(segment, Charsets.UTF_8.name())
        }.getOrDefault(segment).trim()
        return when {
            decoded.isBlank() -> ":value"
            decoded.matches(Regex("^:[A-Za-z][A-Za-z0-9_]*$")) -> decoded
            decoded in staticSegments -> decoded
            decoded.matches(Regex("[a-z]{2}(-[A-Z]{2})?")) -> ":locale"
            decoded.contains('@') || decoded.contains('=') -> ":redacted"
            decoded.length > 24 -> ":id"
            decoded.any(Char::isDigit) -> ":id"
            decoded.any { it == '-' || it == '_' || it == ':' } -> ":id"
            else -> ":value"
        }
    }

}
