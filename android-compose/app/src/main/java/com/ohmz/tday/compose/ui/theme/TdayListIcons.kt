package com.ohmz.tday.compose.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.ohmz.tday.compose.R
import java.util.Locale

// List icons are Lucide glyphs shared across web/Android/iOS — see docs/ICONS.md.
// Each key maps to a path-only Lucide vector drawable (ic_lucide_*).
data class TdayListIconOption(
    val key: String,
    @DrawableRes val iconRes: Int,
)

const val TDAY_DEFAULT_LIST_ICON_KEY = "inbox"

val TdayListIconOptions = listOf(
    TdayListIconOption(TDAY_DEFAULT_LIST_ICON_KEY, R.drawable.ic_lucide_inbox),
    TdayListIconOption("sun", R.drawable.ic_lucide_sun),
    TdayListIconOption("calendar", R.drawable.ic_lucide_calendar),
    TdayListIconOption("schedule", R.drawable.ic_lucide_clock),
    TdayListIconOption("flag", R.drawable.ic_lucide_flag),
    TdayListIconOption("check", R.drawable.ic_lucide_check),
    TdayListIconOption("smile", R.drawable.ic_lucide_smile),
    TdayListIconOption("list", R.drawable.ic_lucide_list),
    TdayListIconOption("bookmark", R.drawable.ic_lucide_bookmark),
    TdayListIconOption("key", R.drawable.ic_lucide_key),
    TdayListIconOption("gift", R.drawable.ic_lucide_gift),
    TdayListIconOption("cake", R.drawable.ic_lucide_cake),
    TdayListIconOption("school", R.drawable.ic_lucide_graduation_cap),
    TdayListIconOption("bag", R.drawable.ic_lucide_backpack),
    TdayListIconOption("edit", R.drawable.ic_lucide_pencil),
    TdayListIconOption("document", R.drawable.ic_lucide_file_text),
    TdayListIconOption("book", R.drawable.ic_lucide_book),
    TdayListIconOption("work", R.drawable.ic_lucide_briefcase_business),
    TdayListIconOption("wallet", R.drawable.ic_lucide_wallet_cards),
    TdayListIconOption("money", R.drawable.ic_lucide_circle_dollar_sign),
    TdayListIconOption("fitness", R.drawable.ic_lucide_dumbbell),
    TdayListIconOption("run", R.drawable.ic_lucide_activity),
    TdayListIconOption("food", R.drawable.ic_lucide_utensils),
    TdayListIconOption("drink", R.drawable.ic_lucide_wine),
    TdayListIconOption("health", R.drawable.ic_lucide_briefcase_medical),
    TdayListIconOption("monitor", R.drawable.ic_lucide_monitor),
    TdayListIconOption("music", R.drawable.ic_lucide_music),
    TdayListIconOption("computer", R.drawable.ic_lucide_monitor),
    TdayListIconOption("game", R.drawable.ic_lucide_gamepad_2),
    TdayListIconOption("headphones", R.drawable.ic_lucide_headphones),
    TdayListIconOption("eco", R.drawable.ic_lucide_leaf),
    TdayListIconOption("pets", R.drawable.ic_lucide_paw_print),
    TdayListIconOption("child", R.drawable.ic_lucide_baby),
    TdayListIconOption("family", R.drawable.ic_lucide_users_round),
    TdayListIconOption("basket", R.drawable.ic_lucide_shopping_basket),
    TdayListIconOption("cart", R.drawable.ic_lucide_shopping_cart),
    TdayListIconOption("mall", R.drawable.ic_lucide_shopping_bag),
    TdayListIconOption("inventory", R.drawable.ic_lucide_archive),
    TdayListIconOption("soccer", R.drawable.ic_lucide_circle),
    TdayListIconOption("baseball", R.drawable.ic_lucide_circle),
    TdayListIconOption("basketball", R.drawable.ic_lucide_circle),
    TdayListIconOption("football", R.drawable.ic_lucide_circle),
    TdayListIconOption("tennis", R.drawable.ic_lucide_circle),
    TdayListIconOption("train", R.drawable.ic_lucide_train),
    TdayListIconOption("flight", R.drawable.ic_lucide_plane),
    TdayListIconOption("boat", R.drawable.ic_lucide_ship),
    TdayListIconOption("car", R.drawable.ic_lucide_car),
    TdayListIconOption("umbrella", R.drawable.ic_lucide_umbrella),
    TdayListIconOption("drop", R.drawable.ic_lucide_droplet),
    TdayListIconOption("snow", R.drawable.ic_lucide_snowflake),
    TdayListIconOption("fire", R.drawable.ic_lucide_flame),
    TdayListIconOption("tools", R.drawable.ic_lucide_hammer),
    TdayListIconOption("scissors", R.drawable.ic_lucide_scissors),
    TdayListIconOption("architecture", R.drawable.ic_lucide_landmark),
    TdayListIconOption("code", R.drawable.ic_lucide_code),
    TdayListIconOption("idea", R.drawable.ic_lucide_lightbulb),
    TdayListIconOption("chat", R.drawable.ic_lucide_message_circle),
    TdayListIconOption("alert", R.drawable.ic_lucide_triangle_alert),
    TdayListIconOption("star", R.drawable.ic_lucide_star),
    TdayListIconOption("heart", R.drawable.ic_lucide_heart),
    TdayListIconOption("circle", R.drawable.ic_lucide_circle),
    TdayListIconOption("square", R.drawable.ic_lucide_square),
    TdayListIconOption("triangle", R.drawable.ic_lucide_triangle),
    TdayListIconOption("home", R.drawable.ic_lucide_house),
    TdayListIconOption("city", R.drawable.ic_lucide_building_2),
    TdayListIconOption("bank", R.drawable.ic_lucide_landmark),
    TdayListIconOption("camera", R.drawable.ic_lucide_camera),
    TdayListIconOption("palette", R.drawable.ic_lucide_palette),
)

private val TdayListIconMap = TdayListIconOptions.associate { it.key to it.iconRes }

/** Lucide drawable resource for a list icon key (falls back to the default inbox glyph). */
@DrawableRes
fun tdayListIconResForKey(iconKey: String?): Int {
    val normalizedKey = normalizeTdayListIconKeyOrNull(iconKey)
    return normalizedKey?.let { TdayListIconMap[it] }
        ?: TdayListIconMap.getValue(TDAY_DEFAULT_LIST_ICON_KEY)
}

/** Loads the list icon as an [ImageVector] for `Icon(imageVector = ...)` call sites. */
@Composable
fun tdayListIconForKey(iconKey: String?): ImageVector =
    ImageVector.vectorResource(tdayListIconResForKey(iconKey))

fun isTdayListIconKeySupported(iconKey: String): Boolean {
    return normalizeTdayListIconKeyOrNull(iconKey) != null
}

private fun normalizeTdayListIconKeyOrNull(iconKey: String?): String? {
    val candidate = iconKey
        ?.trim()
        ?.lowercase(Locale.getDefault())
        ?: return null

    val normalized = when (candidate) {
        "briefcase" -> "work"
        "cocktail" -> "drink"
        "travel" -> "flight"
        else -> candidate
    }

    return normalized.takeIf { it in TdayListIconMap }
}
