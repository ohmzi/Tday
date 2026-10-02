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
        "(authorization|cookie|csrf|token|password|session|secret|email|username|body|payload|header)",
        RegexOption.IGNORE_CASE,
    )
    private val sensitiveLabelPattern = Regex(
        "(https?://|wss?://|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|bearer\\s+|token=|password=|session=|cookie=|csrf)",
        RegexOption.IGNORE_CASE,
    )
    private val tokenLikeLabelPattern = Regex("^[A-Za-z0-9_.:-]+$")
    private val routeTemplateSegment = Regex("^\\{[A-Za-z][A-Za-z0-9_]*}$")
    private val routeLiteralSegment = Regex("^[A-Za-z][A-Za-z0-9_-]*$")

    /** What a path or route segment becomes when it is a value and not one of the app's own names. */
    private const val VALUE_SEGMENT = ":value"
    private const val ID_SEGMENT = ":id"

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

    /**
     * A navigation destination's declared pattern as a path: `todos/list/{listId}/{listName}`
     * becomes `/todos/list/:listId/:listName`.
     *
     * Not [sanitizePath], which has to guess whether a segment is a value. A pattern is the
     * opposite: the segments are the app's own route names and the braces mark where a value goes,
     * so keeping them says where the user was without saying what they were looking at. Query
     * templates (`?topic={topic}`) are dropped.
     */
    fun navigationTemplate(routePattern: String): String {
        val segments = routePattern.substringBefore('?').split('/').filter(String::isNotBlank)
        if (segments.isEmpty()) return "/"
        return segments.joinToString(prefix = "/", separator = "/") { segment ->
            when {
                routeTemplateSegment.matches(segment) -> ":" + segment.removeSurrounding("{", "}")
                routeLiteralSegment.matches(segment) -> segment
                else -> VALUE_SEGMENT
            }
        }
    }

    /**
     * Records that the user moved to the destination declared as [routePattern]: the pattern and
     * nothing else. Sentry's own navigation listener also attaches the arguments, which for a list
     * screen are the list's id and its name.
     */
    fun recordNavigation(routePattern: String) {
        val breadcrumb = Breadcrumb().apply {
            category = "navigation"
            message = "navigate"
            level = SentryLevel.INFO
            setData("to", navigationTemplate(routePattern))
        }
        Sentry.addBreadcrumb(breadcrumb)
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
            decoded.isBlank() -> VALUE_SEGMENT
            decoded.matches(Regex("^:[A-Za-z][A-Za-z0-9_]*$")) -> decoded
            decoded in staticSegments -> decoded
            decoded.matches(Regex("[a-z]{2}(-[A-Z]{2})?")) -> ":locale"
            decoded.contains('@') || decoded.contains('=') -> ":redacted"
            decoded.length > 24 -> ID_SEGMENT
            decoded.any(Char::isDigit) -> ID_SEGMENT
            decoded.any { it == '-' || it == '_' || it == ':' } -> ID_SEGMENT
            else -> VALUE_SEGMENT
        }
    }

}
