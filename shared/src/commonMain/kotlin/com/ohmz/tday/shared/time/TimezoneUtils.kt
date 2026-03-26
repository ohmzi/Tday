package com.ohmz.tday.shared.time

import kotlinx.datetime.TimeZone

object TimezoneUtils {
    fun isValidIanaTimeZone(timeZone: String): Boolean {
        return timeZone in TimeZone.availableZoneIds
    }

    fun resolveTimeZone(preferred: String?, fallback: String = "UTC"): String {
        val normalized = preferred?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return fallback
        }
        return if (isValidIanaTimeZone(normalized)) normalized else fallback
    }
}
