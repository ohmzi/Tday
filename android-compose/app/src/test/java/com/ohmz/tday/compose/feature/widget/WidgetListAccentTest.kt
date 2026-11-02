package com.ohmz.tday.compose.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetListAccentTest {

    @Test
    fun `washes are opaque so the pill renders the same under any tint mode`() {
        listOf("ORANGE", "PINK", "TEAL", "BLUE", "YELLOW", "PURPLE").forEach { key ->
            val accent = requireNotNull(widgetListAccentFor(key)) { key }
            assertEquals("$key light wash alpha", 0xFF, (accent.lightWash ushr 24) and 0xFF)
            assertEquals("$key night wash alpha", 0xFF, (accent.nightWash ushr 24) and 0xFF)
        }
    }

    @Test
    fun `night wash stays dark and light wash stays light`() {
        listOf("ORANGE", "PINK", "TEAL", "BLUE", "YELLOW", "PURPLE").forEach { key ->
            val accent = widgetListAccentFor(key)!!
            assertTrue("$key night wash too bright", WidgetColorMath.luminance(accent.nightWash) < 0.08)
            assertTrue("$key light wash too dark", WidgetColorMath.luminance(accent.lightWash) > 0.6)
        }
    }

    @Test
    fun `surface constants match tday_widget_surface in colors xml`() {
        assertEquals(surfaceColor("values"), DAY_WIDGET_SURFACE)
        assertEquals(surfaceColor("values-night"), NIGHT_WIDGET_SURFACE)
    }

    private fun surfaceColor(folder: String): Int {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .first { File(it, "app/src/main/res").isDirectory }
        val xml = File(root, "app/src/main/res/$folder/colors.xml").readText()
        val hex = Regex("""name="tday_widget_surface">#([0-9A-Fa-f]{8})<""").find(xml)!!.groupValues[1]
        return hex.toLong(16).toInt()
    }
}
