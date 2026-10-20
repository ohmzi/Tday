package com.ohmz.tday.compose.feature.widget

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.ohmz.tday.compose.ui.theme.tdayListAccentColorOrNull
import kotlin.math.roundToInt

/**
 * A chosen list's own colour, resolved for a widget render: the value to paint with in each theme,
 * and the translucent wash that sits behind the "+" in each.
 *
 * Both themes are resolved at once rather than one "current" value, because a widget is drawn by
 * the LAUNCHER's process, under the launcher's configuration — so on API 31+ the render hands both
 * over and lets the host pick (see `TaskWidgetRemoteViews.setAccentColorFilter`), which keeps the
 * tint correct through a day/night flip this app's process may never have been alive to see.
 */
internal data class WidgetListAccent(
    @ColorInt val light: Int,
    @ColorInt val night: Int,
    /** [light] at [LIGHT_WASH_ALPHA]; composited over the host's surface, not pre-blended. */
    @ColorInt val lightWash: Int,
    @ColorInt val nightWash: Int,
)

/**
 * The widget accent for a list colour KEY ("PINK", "TEAL", …) as the snapshot carries it, or null
 * when there is no usable key — an unconfigured instance, a list the cache has no colour for, or a
 * key this build does not know. Null means "use this widget KIND's own accent", which is exactly
 * what every list widget showed before list colours reached them; a colour is never invented, and
 * never inferred from anything but the key.
 *
 * Resolution goes through [tdayListAccentColorOrNull] — the app's one list-colour table, including
 * its legacy-key normalization (GREEN→LIME, GRAY→SLATE) — so a widget can never drift from the
 * colour the same list shows in the app.
 */
internal fun widgetListAccentFor(colorKey: String?): WidgetListAccent? {
    val light = tdayListAccentColorOrNull(colorKey)?.toArgb() ?: return null
    val night = liftedForNight(light)
    return WidgetListAccent(
        light = light,
        night = night,
        lightWash = light.withAlphaFraction(LIGHT_WASH_ALPHA),
        nightWash = night.withAlphaFraction(NIGHT_WASH_ALPHA),
    )
}

/**
 * The app's list palette has ONE value per key and no night variant — in-app those colours are
 * read on a themed surface, where a dark key still reads. A widget has no such guarantee: the
 * watermark is drawn at 10% alpha, and a key darker than the night widget surface (#171A20)
 * composites to nothing at all — SLATE (#3E4774) is literally darker than it, so the glyph would
 * vanish rather than merely dim.
 *
 * So in night the hue is kept and lightness is raised toward white only as far as it takes to clear
 * [NIGHT_LUMINANCE_FLOOR], which is where the two hand-tuned night accents this app already ships
 * sit (`tday_widget_today_accent` #8DC3F3 ≈ 0.51, `tday_widget_floater_accent` #7FC7B9 ≈ 0.49 — the
 * floor is deliberately below both, so the nudge is the smallest one that works). A key already
 * bright enough (GOLD, YELLOW) is returned untouched.
 */
@ColorInt
private fun liftedForNight(@ColorInt color: Int): Int {
    var blend = 0f
    var lifted = color
    while (blend < NIGHT_LIFT_LIMIT && ColorUtils.calculateLuminance(lifted) < NIGHT_LUMINANCE_FLOOR) {
        blend += NIGHT_LIFT_STEP
        lifted = ColorUtils.blendARGB(color, Color.WHITE, blend)
    }
    return lifted
}

/**
 * The wash is the accent at low alpha, NOT a pre-blended opaque colour: a widget sits on the host's
 * wallpaper, and the only surface this process can name is its own `tday_widget_surface`, which is
 * not what the pixels behind the button are. Letting the host composite keeps the button sitting on
 * whatever is actually there.
 */
@ColorInt
private fun Int.withAlphaFraction(fraction: Float): Int =
    ColorUtils.setAlphaComponent(this, (fraction * 255f).roundToInt())

/** Matches the alpha the hand-authored kind washes imply (#EAF5FF is #6EA8E1 at ~14% on white). */
private const val LIGHT_WASH_ALPHA = 0.13f

/** A touch stronger than light: the same alpha over a near-black surface reads as almost nothing. */
private const val NIGHT_WASH_ALPHA = 0.18f

private const val NIGHT_LUMINANCE_FLOOR = 0.42
private const val NIGHT_LIFT_STEP = 0.02f

/** A ceiling on the nudge: past this the result stops being recognisably the user's colour. */
private const val NIGHT_LIFT_LIMIT = 0.60f
