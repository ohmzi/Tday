package com.ohmz.tday.compose.core.data.todo

import com.joestelmach.natty.Parser
import com.ohmz.tday.compose.core.model.TodoTitleNlpResponse
import java.util.Date
import java.util.TimeZone

/**
 * On-device natural-language date/time parser for the new-task title field.
 *
 * Fully offline — no AI model, no network. Uses Natty (the same engine the
 * backend's [com.ohmz.tday.compose] server path used), so behaviour matches the
 * previous server-side parsing and is close to the web app (chrono-node). It
 * detects a phrase like "July 29 2030 at 8pm", returns the matched span (for the
 * in-field highlight), the cleaned title (phrase stripped), and the due instant.
 *
 * Parsing runs in the device timezone; the resulting instant ([dueEpochMs]) is an
 * absolute moment, which the create form converts to a UTC ISO string on save.
 */
object OnDeviceTitleNlpParser {
    fun parse(text: String, referenceEpochMs: Long): TodoTitleNlpResponse? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val parser = Parser(TimeZone.getDefault())
        val groups = runCatching { parser.parse(text, Date(referenceEpochMs)) }.getOrNull()
            ?: return null
        if (groups.isEmpty()) return null

        val group = groups[0]
        val dates = group.dates
        if (dates.isNullOrEmpty()) return null

        val matchedText = group.text ?: ""
        val matchStart = text.indexOf(matchedText).coerceAtLeast(0)
        // Use the time the user named: the start for a single time, the end only
        // when an explicit range ("8pm to 10pm") was given — matching the backend
        // and web, so a bare "8pm" is never shifted.
        val startDate = dates[0]
        val dueDate = if (dates.size > 1) dates[1] else startDate

        val before = text.substring(0, matchStart)
        val after = text.substring((matchStart + matchedText.length).coerceAtMost(text.length))
        val cleanTitle = "$before$after".replace(Regex("\\s{2,}"), " ").trim()

        return TodoTitleNlpResponse(
            cleanTitle = cleanTitle,
            matchedText = matchedText.ifEmpty { null },
            matchStart = matchStart,
            dueEpochMs = dueDate.time,
        )
    }
}
