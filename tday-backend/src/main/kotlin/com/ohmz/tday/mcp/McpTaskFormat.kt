package com.ohmz.tday.mcp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Which of T'Day's two task entities a handle addresses. */
enum class TaskKind(val prefix: String, val label: String) {
    /** A scheduled task — has a due date, appears in Today/calendar/reminders. */
    TODO("todo", "scheduled task"),

    /** An undated "Anytime" task. */
    FLOATER("floater", "Anytime task"),
}

/**
 * A task id as MCP callers see it: `todo:<id>` / `floater:<id>`.
 *
 * Scheduled tasks and floaters are separate tables with separate endpoints, and their
 * ids are indistinguishable CUIDs. Prefixing makes every write unambiguous and lets a
 * wrong handle fail with an explanation rather than a 404 from the wrong table.
 */
data class TaskHandle(val kind: TaskKind, val id: String) {
    override fun toString(): String = "${kind.prefix}:$id"

    companion object {
        fun of(kind: TaskKind, id: String) = TaskHandle(kind, id)

        fun parse(raw: String?): TaskHandle? {
            val value = raw?.trim().orEmpty()
            val separator = value.indexOf(':')
            if (separator <= 0 || separator == value.length - 1) return null
            val prefix = value.substring(0, separator).lowercase()
            val id = value.substring(separator + 1).trim()
            if (id.isEmpty()) return null
            val kind = TaskKind.entries.firstOrNull { it.prefix == prefix } ?: return null
            return TaskHandle(kind, id)
        }
    }
}

/**
 * Date handling for the MCP surface.
 *
 * The API's wire format is a UTC wall clock with no offset (`2026-08-21T09:00`), so
 * every timestamp that crosses this boundary has to be converted deliberately in both
 * directions — a value that looks like a local time but is actually UTC is the easiest
 * way to report a time that is silently hours off.
 */
object McpDates {

    /**
     * Time of day given to a `due` with no time. End of day rather than start, so a
     * task the user dated but didn't time doesn't read as overdue for most of that day.
     */
    const val DATE_ONLY_HOUR = 23
    const val DATE_ONLY_MINUTE = 59

    /**
     * Lower bound for an open-ended window. `LocalDateTime.MIN` would overflow the
     * moment anything converted it to epoch millis; no T'Day task predates this.
     */
    val FLOOR: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    private val DISPLAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val DISPLAY_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

    fun zoneOf(timeZone: String?): ZoneId =
        runCatching { ZoneId.of(timeZone?.trim().orEmpty().ifEmpty { "UTC" }) }.getOrDefault(ZoneOffset.UTC)

    /**
     * Parse a user/model-supplied due into the UTC wall clock the API stores.
     *
     * Accepts a full instant (`…Z` / `+01:00`), a local date-time (`2026-08-21T09:00`),
     * or a bare date (`2026-08-21`, which becomes end of that day). Local forms are read
     * in [zone]. Returns null when nothing parses.
     */
    fun parseDue(raw: String, zone: ZoneId): LocalDateTime? {
        val value = raw.trim().replace(' ', 'T')
        if (value.isEmpty()) return null

        runCatching { OffsetDateTime.parse(value) }.getOrNull()?.let {
            return it.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().flooredToMinute()
        }
        runCatching { LocalDateTime.parse(value) }.getOrNull()?.let {
            return it.toUtcWallClock(zone)
        }
        runCatching { LocalDate.parse(value) }.getOrNull()?.let {
            return it.atTime(DATE_ONLY_HOUR, DATE_ONLY_MINUTE).toUtcWallClock(zone)
        }
        return null
    }

    /** True when [raw] carried no time of day, so callers can say which default was applied. */
    fun isDateOnly(raw: String): Boolean =
        runCatching { LocalDate.parse(raw.trim()) }.isSuccess

    /** A UTC wall clock rendered in the user's timezone, for text the model reads back. */
    fun display(utcWallClock: String?, zone: ZoneId): String {
        val parsed = parseWireFormat(utcWallClock) ?: return "no date"
        return parsed.toZone(zone).format(DISPLAY)
    }

    fun displayDate(local: LocalDateTime): String = local.format(DISPLAY_DATE)

    /** Read the API's own wire format (with or without seconds, with or without an offset). */
    fun parseWireFormat(value: String?): LocalDateTime? {
        val normalized = value?.trim()?.ifEmpty { null } ?: return null
        runCatching { LocalDateTime.parse(normalized) }.getOrNull()?.let { return it }
        return runCatching {
            OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
        }.getOrNull()
    }

    fun nowUtc(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).flooredToMinute()

    /** Start of today in [zone], as a UTC wall clock. */
    fun startOfToday(zone: ZoneId): LocalDateTime =
        LocalDate.now(zone).atStartOfDay().toUtcWallClock(zone)

    /** Last minute of today in [zone], as a UTC wall clock. */
    fun endOfToday(zone: ZoneId): LocalDateTime = endOfDay(LocalDate.now(zone), zone)

    fun endOfDay(date: LocalDate, zone: ZoneId): LocalDateTime =
        date.atTime(23, 59, 59).toUtcWallClock(zone)

    fun startOfDay(date: LocalDate, zone: ZoneId): LocalDateTime =
        date.atStartOfDay().toUtcWallClock(zone)

    /** Parse a window bound: a bare date or a local date-time, read in [zone]. */
    fun parseWindowStart(raw: String, zone: ZoneId): LocalDateTime? {
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()?.let { return startOfDay(it, zone) }
        return parseDue(raw, zone)
    }

    fun parseWindowEnd(raw: String, zone: ZoneId): LocalDateTime? {
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()?.let { return endOfDay(it, zone) }
        return parseDue(raw, zone)
    }

    fun LocalDateTime.flooredToMinute(): LocalDateTime = withSecond(0).withNano(0)

    /** Read this as a wall clock in [zone] and re-express it as a UTC wall clock. */
    fun LocalDateTime.toUtcWallClock(zone: ZoneId): LocalDateTime =
        atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().flooredToMinute()

    /** Read this as a UTC wall clock and re-express it in [zone]. */
    fun LocalDateTime.toZone(zone: ZoneId): LocalDateTime =
        atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
}
