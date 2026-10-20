package com.ohmz.tday.compose.core.observability

import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Which workspace a report came from. The FAQ discloses it, because Local Mode never touches a server. */
enum class TelemetryWorkspaceMode(val tag: String) { LOCAL("local"), SERVER("server") }

/**
 * The five tags every report carries: which client, which version, which workspace, and the two
 * context facts a planner's bugs depend on, the UTC offset and the language.
 *
 * Coarse on purpose. The offset says "UTC+2", never the zone id ("Europe/Berlin" narrows a person
 * to a city), and the language says "pt", never "pt_BR". Both are disclosed in the FAQ.
 */
object TelemetryEventTags {
    @Volatile
    private var workspace: TelemetryWorkspaceMode? = null

    /** The app calls this as the workspace resolves; `null` while it is unknown or has been left. */
    fun recordWorkspace(mode: TelemetryWorkspaceMode?) {
        workspace = mode
    }

    fun current(appVersion: String): Map<String, String> = build(appVersion, workspace)

    fun build(
        appVersion: String,
        mode: TelemetryWorkspaceMode?,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
        nowMs: Long = System.currentTimeMillis(),
    ): Map<String, String> = buildMap {
        put("client", "android")
        put("app_version", appVersion)
        mode?.let { put("mode", it.tag) }
        put("tz_offset", utcOffsetTag(timeZone.getOffset(nowMs)))
        put("locale_lang", languageTag(locale))
    }

    /** `UTC`, `UTC+2`, `UTC-5`, `UTC+5:30`: minutes appear only when the offset has them. */
    fun utcOffsetTag(offsetMs: Int): String {
        if (offsetMs == 0) return "UTC"
        val totalMinutes = abs(offsetMs) / MS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        val sign = if (offsetMs > 0) "+" else "-"
        return if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
    }

    /** The language alone, lower case; `und` (undetermined) when the locale names none. */
    fun languageTag(locale: Locale): String = locale.language.lowercase(Locale.ROOT).ifBlank { "und" }

    private const val MS_PER_MINUTE = 60_000
    private const val MINUTES_PER_HOUR = 60
}
