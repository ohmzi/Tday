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
    /** [light] at [LIGHT_WASH_ALPHA] pre-blended over the day widget surface, so fully opaque. */
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
        lightWash = WidgetColorMath.blend(DAY_WIDGET_SURFACE, light, LIGHT_WASH_ALPHA),
        nightWash = WidgetColorMath.blend(NIGHT_WIDGET_SURFACE, night, NIGHT_WASH_ALPHA),
    )
}

/**
 * The wash is the accent pre-blended over the widget's own card colour into an OPAQUE colour, the
 * same kind of value the Today and Floater kinds ship as `tday_widget_*accent_wash`. The pill sits
 * on that card (`widget_preview_background`, filled with `tday_widget_surface`), never on the
 * wallpaper, so the card is the only thing behind it.
 *
 * It used to be the accent at low alpha, relying on the pill ImageView's `src_in` tintMode to carry
 * that alpha through. Some launchers draw the tint with the ImageView default (src_atop), which
 * keeps the white pill opaque and left it cream in dark mode. An opaque tint looks the same under
 * either mode.
 *
 * Light wash is 0.20, not the 0.13 it was. The floater kind's own wash, #EAF6F1, is teal at about
 * 0.11 over white, and a 0.13 wash of a light key (YELLOW, LIME, GOLD, PINK) left the pill reading
 * as white on a light host. 0.20 gives the pill the same presence the built-in widgets have.
 */
// Must stay in step with iOS `TodayTasksWidget.addButtonWashOpacity` (0.20) in TodayTasksWidget.swift.
private const val LIGHT_WASH_ALPHA = 0.20f

/** A touch under light: the night accent is lifted toward white, so 0.18 of it already reads clearly on the dark card. */
private const val NIGHT_WASH_ALPHA = 0.18f

/** `tday_widget_surface` in values/ and values-night/; `WidgetListAccentTest` pins them to colors.xml. */
internal const val DAY_WIDGET_SURFACE = 0xFFFFFFFF.toInt()
internal const val NIGHT_WIDGET_SURFACE = 0xFF171A20.toInt()
