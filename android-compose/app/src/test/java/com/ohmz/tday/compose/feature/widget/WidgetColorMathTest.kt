package com.ohmz.tday.compose.feature.widget

import androidx.compose.ui.graphics.toArgb
import com.ohmz.tday.compose.ui.theme.tdayListAccentColorOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WidgetColorMathTest {
    private val floaterKeys = listOf("PINK", "GOLD", "LIME", "YELLOW", "CORAL", "RED")

    private fun palette(key: String): Int = tdayListAccentColorOrNull(key)!!.toArgb()

    /** Hue in degrees, 0 until 360. Undefined for greys, which the palette has none of. */
    private fun hue(argb: Int): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            max == r -> 60 * (((g - b) / delta) % 6)
            max == g -> 60 * (((b - r) / delta) + 2)
            else -> 60 * (((r - g) / delta) + 4)
        }
        return (h + 360) % 360
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return minOf(d, 360 - d)
    }

    @Test
    fun `day glyph of every floater key sits at or under the day ceiling`() {
        floaterKeys.forEach { key ->
            val day = WidgetColorMath.deepenedForDay(palette(key))
            assertTrue(
                "$key day luminance ${WidgetColorMath.luminance(day)} above ceiling",
                WidgetColorMath.luminance(day) <= WidgetColorMath.DAY_LUMINANCE_CEILING + 1e-9,
            )
        }
    }

    @Test
    fun `day deepening keeps the hue of every floater key`() {
        floaterKeys.forEach { key ->
            val source = palette(key)
            val day = WidgetColorMath.deepenedForDay(source)
            assertTrue(
                "$key hue moved by ${hueDistance(hue(source), hue(day))}",
                hueDistance(hue(source), hue(day)) < 2.0,
            )
        }
    }

    @Test
    fun `GOLD and YELLOW are deepened under the ceiling and keep their hue`() {
        listOf("GOLD", "YELLOW").forEach { key ->
            val source = palette(key)
            val day = WidgetColorMath.deepenedForDay(source)
            assertTrue("$key should be changed by day", day != source)
            assertTrue(
                "$key day luminance ${WidgetColorMath.luminance(day)} above ceiling",
                WidgetColorMath.luminance(day) <= WidgetColorMath.DAY_LUMINANCE_CEILING + 1e-9,
            )
            assertTrue(
                "$key hue moved by ${hueDistance(hue(source), hue(day))}",
                hueDistance(hue(source), hue(day)) < 2.0,
            )
        }
    }

    @Test
    fun `PINK CORAL LIME RED BLUE and PURPLE come back unchanged by day`() {
        listOf("PINK", "CORAL", "LIME", "RED", "BLUE", "PURPLE").forEach { key ->
            assertEquals("$key", palette(key), WidgetColorMath.deepenedForDay(palette(key)))
        }
    }

    @Test
    fun `day ceiling leaves the brighter hand-tuned day accent untouched`() {
        // tday_widget_today_accent #6EA8E1 — the higher of the two hand-tuned day accents.
        val todayAccent = 0xFF6EA8E1.toInt()
        assertEquals(todayAccent, WidgetColorMath.deepenedForDay(todayAccent))
        // tday_widget_floater_accent #4D8F83 is already below the ceiling.
        val floaterAccent = 0xFF4D8F83.toInt()
        assertEquals(floaterAccent, WidgetColorMath.deepenedForDay(floaterAccent))
    }

    @Test
    fun `night lift still clears the night floor and keeps the hue`() {
        floaterKeys.forEach { key ->
            val source = palette(key)
            val night = WidgetColorMath.liftedForNight(source)
            assertTrue(
                "$key night luminance too low",
                WidgetColorMath.luminance(night) >= WidgetColorMath.NIGHT_LUMINANCE_FLOOR - 1e-9 ||
                    night == source,
            )
            assertTrue(
                "$key night hue moved",
                hueDistance(hue(source), hue(night)) < 3.0,
            )
        }
    }

    @Test
    fun `wash keeps the colour and applies the requested alpha`() {
        val wash = WidgetColorMath.withAlpha(0xFFE8BA30.toInt(), 0.20f)
        assertEquals(51, (wash ushr 24) and 0xFF)
        assertEquals(0xE8BA30, wash and 0xFFFFFF)
    }
}
