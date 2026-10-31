package com.ohmz.tday.compose.feature.widget

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.ohmz.tday.compose.ui.theme.tdayListAccentColorOrNull

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
    /** The day glyph colour, deepened from the palette key when it is too bright to read on a day surface. */
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
    val palette = tdayListAccentColorOrNull(colorKey)?.toArgb() ?: return null
    val light = WidgetColorMath.deepenedForDay(palette)
    val night = WidgetColorMath.liftedForNight(palette)
    return WidgetListAccent(
        light = light,
        night = night,
        lightWash = WidgetColorMath.withAlpha(light, LIGHT_WASH_ALPHA),
        nightWash = WidgetColorMath.withAlpha(night, NIGHT_WASH_ALPHA),
    )
}

/**
 * The wash is the accent at low alpha, NOT a pre-blended opaque colour: a widget sits on the host's
 * wallpaper, and the only surface this process can name is its own `tday_widget_surface`, which is
 * not what the pixels behind the button are. Letting the host composite keeps the button sitting on
 * whatever is actually there.
 *
 * Light wash is 0.20, not the 0.13 it was. The floater kind's own wash, #EAF6F1, is teal at about
 * 0.11 over white, and a 0.13 wash of a light key (YELLOW, LIME, GOLD, PINK) left the pill reading
 * as white on a light host. 0.20 gives the pill the same presence the built-in widgets have, while
 * still letting the host's surface show through.
 */
// Must stay in step with iOS `TodayTasksWidget.addButtonWashOpacity` (0.20) in TodayTasksWidget.swift.
private const val LIGHT_WASH_ALPHA = 0.20f

/** A touch stronger than light: the same alpha over a near-black surface reads as almost nothing. */
private const val NIGHT_WASH_ALPHA = 0.18f
