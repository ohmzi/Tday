package com.ohmz.tday.compose.feature.widget

import com.ohmz.tday.compose.R
import com.ohmz.tday.compose.ui.theme.TdayListIconOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWatermarkGlyphsTest {

    @Test
    fun `every list glyph has a baked watermark`() {
        val missing = TdayListIconOptions.filter { it.iconRes !in WidgetWatermarkForGlyph }.map { it.key }
        assertTrue("re-run scripts/widget-watermarks.py for: $missing", missing.isEmpty())
    }

    @Test
    fun `pseudo list glyphs have baked watermarks`() {
        listOf(R.drawable.ic_lucide_calendar_clock, R.drawable.ic_lucide_alarm_clock).forEach {
            assertTrue(it in WidgetWatermarkForGlyph)
        }
    }

    @Test
    fun `a baked watermark is drawn at full alpha like the Today mark`() {
        val watermark = glyphWatermark(R.drawable.ic_lucide_shopping_cart, R.color.tday_widget_floater_accent, null)
        assertEquals(R.drawable.widget_watermark_shopping_cart, watermark.drawable)
        assertEquals(TaskWidgetWatermark.OPAQUE_ALPHA, watermark.alpha)
    }
}
