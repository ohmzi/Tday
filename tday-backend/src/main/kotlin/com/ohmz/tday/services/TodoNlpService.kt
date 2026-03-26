package com.ohmz.tday.services

import com.joestelmach.natty.Parser
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

data class NlpParseResult(
    val cleanTitle: String,
    val matchedText: String?,
    val matchStart: Int?,
    val startEpochMs: Long?,
    val dueEpochMs: Long?,
)

interface TodoNlpService {
    fun parse(text: String, locale: String? = null, referenceEpochMs: Long? = null, timezoneOffsetMinutes: Int? = null, defaultDurationMinutes: Int? = null): NlpParseResult
}

class TodoNlpServiceImpl : TodoNlpService {
    private val defaultDuration = 180

    @Suppress("UNUSED_PARAMETER")
    override fun parse(
        text: String,
        locale: String?,
        referenceEpochMs: Long?,
        timezoneOffsetMinutes: Int?,
        defaultDurationMinutes: Int?,
    ): NlpParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return NlpParseResult("", null, null, null, null)

        val refDate = if (referenceEpochMs != null) Date(referenceEpochMs) else Date()
        val tz = if (timezoneOffsetMinutes != null) {
            TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(-timezoneOffsetMinutes * 60))
        } else {
            TimeZone.getDefault()
        }

        val parser = Parser(tz)
        val groups = parser.parse(text, refDate)
        if (groups.isEmpty()) return NlpParseResult(trimmed, null, null, null, null)

        val group = groups[0]
        val dates = group.dates
        if (dates.isEmpty()) return NlpParseResult(trimmed, null, null, null, null)

        val matchedText = group.text ?: ""
        val matchStart = text.indexOf(matchedText).coerceAtLeast(0)
        val startDate = dates[0]
        val duration = sanitizeDuration(defaultDurationMinutes)
        val dueDate = if (dates.size > 1) dates[1] else Date(startDate.time + duration * 60_000L)

        val before = text.substring(0, matchStart)
        val after = text.substring((matchStart + matchedText.length).coerceAtMost(text.length))
        val cleanTitle = "$before$after".replace(Regex("\\s{2,}"), " ").trim()

        return NlpParseResult(
            cleanTitle = cleanTitle,
            matchedText = matchedText.ifEmpty { null },
            matchStart = matchStart,
            startEpochMs = startDate.time,
            dueEpochMs = dueDate.time,
        )
    }

    private fun sanitizeDuration(raw: Int?): Int {
        if (raw == null || raw <= 0) return defaultDuration
        return raw.coerceIn(1, 24 * 60)
    }
}
