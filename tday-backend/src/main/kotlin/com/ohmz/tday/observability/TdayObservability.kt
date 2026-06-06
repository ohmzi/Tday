package com.ohmz.tday.observability

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object TdayObservability {
    private val staticSegments = setOf(
        "api",
        "app",
        "app-settings",
        "auth",
        "callback",
        "credentials",
        "credentials-key",
        "csrf",
        "logout",
        "register",
        "session",
        "todo",
        "instance",
        "complete",
        "uncomplete",
        "prioritize",
        "reorder",
        "summary",
        "nlp",
        "overdue",
        "floater",
        "floaterList",
        "completedTodo",
        "completedFloater",
        "list",
        "preferences",
        "user",
        "profile",
        "change-password",
        "timezone",
        "mobile",
        "probe",
        "admin",
        "settings",
        "ws",
        "health",
        "tday",
        ".well-known",
        "apple-app-site-association",
    )

    private val sensitiveQueryKeys = setOf(
        "token",
        "session",
        "secret",
        "password",
        "username",
        "code",
        "key",
        "captcha",
        "highlightTodoId",
    )

    private val routeLikeDataKeys = setOf("route", "path", "url", "href", "from", "to", "endpoint")
    private val sensitiveDataKeyPattern =
        Regex("(authorization|cookie|csrf|token|password|session|secret|username|body|payload|header)", RegexOption.IGNORE_CASE)
    private val sensitiveLabelPattern =
        Regex("(https?://|wss?://|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|bearer\\s+|token=|password=|session=|cookie=|csrf)", RegexOption.IGNORE_CASE)
    private val tokenLikeLabelPattern = Regex("^[A-Za-z0-9_.:-]+$")

    fun routeTemplate(method: String, rawPath: String): String =
        "${method.uppercase()} ${sanitizePath(rawPath)}"

    fun sanitizePath(raw: String): String {
        val withoutQuery = raw.substringBefore('?').substringBefore('#')
        val path = if (withoutQuery.contains("://")) {
            val withoutScheme = withoutQuery.substringAfter("://")
            val slash = withoutScheme.indexOf('/')
            if (slash >= 0) withoutScheme.substring(slash) else "/"
        } else {
            withoutQuery
        }
            .ifBlank { "/" }

        val segments = path.split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return "/"

        return segments.joinToString(prefix = "/", separator = "/") { segment ->
            sanitizeSegment(segment)
        }
    }

    fun safeLabel(raw: Any?): String {
        val value = raw?.toString()?.trim().orEmpty()
        if (value.isBlank()) return "unknown"
        if (sensitiveLabelPattern.containsMatchIn(value)) return "redacted"
        if (value.length > 24 && value.any(Char::isDigit) && tokenLikeLabelPattern.matches(value)) return "id"
        val normalized = value
            .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
            .take(64)
        return normalized.ifBlank { "unknown" }
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

    fun captureException(
        throwable: Throwable,
        operation: String,
        data: Map<String, Any?> = emptyMap(),
    ) {
        Sentry.withScope { scope ->
            scope.setTag("tday.operation", safeLabel(operation))
            data.forEach { (key, value) -> scope.setExtra(key, safeDataValue(key, value).toString()) }
            Sentry.captureException(throwable)
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
        val decoded = runCatching { java.net.URLDecoder.decode(segment, Charsets.UTF_8.name()) }
            .getOrDefault(segment)
            .trim()
        return when {
            decoded.isBlank() -> ":value"
            decoded.matches(Regex("^:[A-Za-z][A-Za-z0-9_]*$")) -> decoded
            decoded in staticSegments -> decoded
            decoded.matches(Regex("[a-z]{2}(-[A-Z]{2})?")) -> ":locale"
            decoded.length > 24 -> ":id"
            decoded.any(Char::isDigit) -> ":id"
            decoded.contains('@') -> ":redacted"
            decoded.contains('=') -> ":redacted"
            decoded.contains(':') -> ":id"
            decoded.contains('_') -> ":id"
            decoded.contains('-') -> ":id"
            decoded.lowercase() in sensitiveQueryKeys -> ":redacted"
            else -> ":value"
        }
    }

}
