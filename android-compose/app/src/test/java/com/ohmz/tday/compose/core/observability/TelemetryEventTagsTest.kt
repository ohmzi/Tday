package com.ohmz.tday.compose.core.observability

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.TimeZone

class TelemetryEventTagsTest {
    @After
    fun forgetTheWorkspace() = TelemetryEventTags.recordWorkspace(null)

    @Test
    fun `spells the utc offset the way the faq promises`() {
        val hour = 3_600_000
        assertEquals("UTC", TelemetryEventTags.utcOffsetTag(0))
        assertEquals("UTC+2", TelemetryEventTags.utcOffsetTag(2 * hour))
        assertEquals("UTC-5", TelemetryEventTags.utcOffsetTag(-5 * hour))
        assertEquals("UTC+5:30", TelemetryEventTags.utcOffsetTag(5 * hour + 30 * 60_000))
        assertEquals("UTC-3:30", TelemetryEventTags.utcOffsetTag(-(3 * hour + 30 * 60_000)))
        assertEquals("UTC+5:45", TelemetryEventTags.utcOffsetTag(5 * hour + 45 * 60_000))
        assertEquals("UTC+14", TelemetryEventTags.utcOffsetTag(14 * hour))
    }

    @Test
    fun `keeps only the language of a locale`() {
        assertEquals("pt", TelemetryEventTags.languageTag(java.util.Locale("PT", "BR")))
        assertEquals("ja", TelemetryEventTags.languageTag(java.util.Locale.JAPAN))
        assertEquals("und", TelemetryEventTags.languageTag(java.util.Locale.ROOT))
    }

    @Test
    fun `builds the tag set from the current context and never the zone id`() {
        val built = TelemetryEventTags.build(
            appVersion = "0.8.0",
            mode = TelemetryWorkspaceMode.LOCAL,
            timeZone = TimeZone.getTimeZone("Asia/Kolkata"),
            locale = java.util.Locale("hi", "IN"),
            nowMs = 1_700_000_000_000L,
        )

        assertEquals(
            mapOf(
                "client" to "android",
                "app_version" to "0.8.0",
                "mode" to "local",
                "tz_offset" to "UTC+5:30",
                "locale_lang" to "hi",
            ),
            built,
        )
        assertFalse(built.values.any { it.contains("Kolkata") || it.contains("IN") })
    }

    @Test
    fun `omits the mode until the workspace is known`() {
        val built = TelemetryEventTags.build(appVersion = "0.8.0", mode = null)

        assertFalse("mode" in built)
    }

    @Test
    fun `follows the workspace the app reports, and forgets it`() {
        TelemetryEventTags.recordWorkspace(TelemetryWorkspaceMode.SERVER)
        assertEquals("server", TelemetryEventTags.current(appVersion = "0.8.0")["mode"])

        TelemetryEventTags.recordWorkspace(null)
        assertFalse("mode" in TelemetryEventTags.current(appVersion = "0.8.0"))
    }
}
