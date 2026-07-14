package com.ohmz.tday.services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One VEVENT's worth of data, already decrypted and timezone-tagged. */
data class IcsEvent(
    val uid: String,
    val timeZone: String,
    val start: LocalDateTime,
    val rrule: String?,
    val exdates: List<LocalDateTime>,
    val summary: String,
    val description: String?,
    /** Non-null marks this event as a single-occurrence override (RECURRENCE-ID). */
    val recurrenceId: LocalDateTime? = null,
)

/**
 * Pure RFC 5545 serialization — no DB, no I/O — so the wire format can be tested against
 * fixtures. All content lines use CRLF and are folded to <=75 chars.
 */
object CalendarIcs {
    private val LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun document(events: List<IcsEvent>, stampUtc: LocalDateTime): String {
        val dtStamp = "${stampUtc.format(LOCAL_FORMAT)}Z"
        return buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("PRODID:-//T'Day//Calendar Feed//EN\r\n")
            append("CALSCALE:GREGORIAN\r\n")
            append("METHOD:PUBLISH\r\n")
            append("X-WR-CALNAME:T'Day\r\n")
            events.forEach { append(vevent(it, dtStamp)) }
            append("END:VCALENDAR\r\n")
        }
    }

    private fun vevent(event: IcsEvent, dtStamp: String): String = buildString {
        append("BEGIN:VEVENT\r\n")
        append("UID:").append(event.uid).append("\r\n")
        append("DTSTAMP:").append(dtStamp).append("\r\n")
        // Point-in-time DTSTART only — durations were dropped in V5, so no DTEND/DURATION.
        append(foldLine("DTSTART;TZID=${event.timeZone}:${formatLocal(event.start)}"))
        if (event.recurrenceId != null) {
            append(foldLine("RECURRENCE-ID;TZID=${event.timeZone}:${formatLocal(event.recurrenceId)}"))
        }
        if (event.rrule != null) {
            append(foldLine("RRULE:${event.rrule}"))
        }
        if (event.exdates.isNotEmpty()) {
            val values = event.exdates.joinToString(",") { formatLocal(it) }
            append(foldLine("EXDATE;TZID=${event.timeZone}:$values"))
        }
        append(foldLine("SUMMARY:${escapeText(event.summary)}"))
        if (!event.description.isNullOrBlank()) {
            append(foldLine("DESCRIPTION:${escapeText(event.description)}"))
        }
        append("END:VEVENT\r\n")
    }

    /** Floating local time (paired with TZID): `yyyyMMddTHHmmss`. */
    fun formatLocal(value: LocalDateTime): String = value.format(LOCAL_FORMAT)

    /** RFC 5545 TEXT escaping: backslash, semicolon, comma, and newlines. */
    fun escapeText(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    /** Fold a content line to <=75 chars per RFC 5545, terminating with CRLF. */
    fun foldLine(line: String): String {
        if (line.length <= 75) return "$line\r\n"
        val sb = StringBuilder()
        var index = 0
        var lineLength = 75
        while (index < line.length) {
            val end = minOf(index + lineLength, line.length)
            sb.append(line, index, end)
            sb.append("\r\n")
            index = end
            if (index < line.length) sb.append(' ')
            // Continuation lines allow 74 chars after the leading space.
            lineLength = 74
        }
        return sb.toString()
    }
}
