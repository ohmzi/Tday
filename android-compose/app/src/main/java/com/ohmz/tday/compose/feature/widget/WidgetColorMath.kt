package com.ohmz.tday.compose.feature.widget

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Plain ARGB arithmetic for a widget's list colours. No `android.graphics` here, so the day and
 * night adjustments can be pinned by pure JVM unit tests (the Android stubs throw on `ColorUtils`).
 *
 * Luminance is the WCAG relative luminance, the same formula `ColorUtils.calculateLuminance` uses,
 * so the thresholds below mean what the hand-tuned widget accents were measured against.
 */
internal object WidgetColorMath {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** WCAG relative luminance of an opaque-or-not ARGB colour, 0.0 (black) to 1.0 (white). */
    fun luminance(argb: Int): Double {
        fun linear(channel: Int): Double {
            val s = channel / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear((argb shr 16) and 0xFF) +
            0.7152 * linear((argb shr 8) and 0xFF) +
            0.0722 * linear(argb and 0xFF)
    }

    /** Per-channel linear blend from [from] toward [to]; [fraction] 0 keeps [from], 1 is [to]. */
    fun blend(from: Int, to: Int, fraction: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * fraction).roundToInt().coerceIn(0, 0xFF)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** [argb] with its alpha replaced by [fraction] of full. */
    fun withAlpha(argb: Int, fraction: Float): Int {
        val alpha = (fraction * 255f).roundToInt().coerceIn(0, 0xFF)
        return (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    /**
     * Night: lifts a key darker than [NIGHT_LUMINANCE_FLOOR] toward white, only as far as it takes to
     * clear the floor. A key already bright enough is returned as it is.
     */
    fun liftedForNight(color: Int): Int {
        var blendAmount = 0f
        var lifted = color
        while (blendAmount < NIGHT_LIFT_LIMIT && luminance(lifted) < NIGHT_LUMINANCE_FLOOR) {
            blendAmount += NIGHT_LIFT_STEP
            lifted = blend(color, WHITE, blendAmount)
        }
        return lifted
    }

    /**
     * Day: darkens a key brighter than [DAY_LUMINANCE_CEILING] toward black, only as far as it takes
     * to get under the ceiling. Blending toward black scales every channel by the same factor, so
     * the hue is kept. A key already dark enough (RED, BLUE, PURPLE) is returned as it is.
     */
    fun deepenedForDay(color: Int): Int {
        var blendAmount = 0f
        var deepened = color
        while (blendAmount < DAY_DEEPEN_LIMIT && luminance(deepened) > DAY_LUMINANCE_CEILING) {
            blendAmount += DAY_DEEPEN_STEP
            deepened = blend(color, BLACK, blendAmount)
        }
        return deepened
    }

    /** Night lift: the floor sits below both hand-tuned night accents the app ships. */
    const val NIGHT_LUMINANCE_FLOOR = 0.42
    const val NIGHT_LIFT_STEP = 0.02f

    /** A ceiling on the night nudge: past this the result stops being recognisably the user's colour. */
    const val NIGHT_LIFT_LIMIT = 0.60f

    /**
     * Day ceiling, set just above the brighter of the two hand-tuned day accents:
     * `tday_widget_today_accent` #6EA8E1 has luminance ≈ 0.367 and `tday_widget_floater_accent`
     * #4D8F83 ≈ 0.319. Keys under it (the dark ones, and the hue-rich mid ones) are left alone.
     */
    // Must stay in step with iOS `tdayWidgetDayLuminanceCeiling` (ios-swiftUI TdayListAccent.swift).
    const val DAY_LUMINANCE_CEILING = 0.38
    const val DAY_DEEPEN_STEP = 0.02f

    /** The most a day key is darkened: far enough to clear the ceiling for any palette key. */
    const val DAY_DEEPEN_LIMIT = 0.60f
}
