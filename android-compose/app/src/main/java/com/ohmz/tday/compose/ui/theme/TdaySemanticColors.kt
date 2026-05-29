package com.ohmz.tday.compose.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale

data class TdayListColorOption(
    val key: String,
    val color: Color,
)

const val TDAY_DEFAULT_LIST_COLOR_KEY = "PINK"

val TdayPriorityHigh = Color(0xFFFF3B30)
val TdayPriorityMedium = Color(0xFFFF9500)
val TdayPriorityLow = Color(0xFF007AFF)
val TdayFloaterAccent = Color(0xFF4D8F83)
val TdayTodoModeTodayAccent = Color(0xFF5C9FE7)
val TdayTodoModeOverdueAccent = Color(0xFFDA7661)
val TdayTodoModeScheduledAccent = Color(0xFFF29F38)
val TdayTodoModeAllAccent = Color(0xFF5E6878)
val TdayTodoModePriorityAccent = Color(0xFFE65E52)
val TdayCompletedTitleAccent = TdayTodoModeAllAccent

val TdayListColorOptions = listOf(
    TdayListColorOption("PINK", Color(0xFFC987A5)),
    TdayListColorOption("GOLD", Color(0xFFC7AA63)),
    TdayListColorOption("DEEP_BLUE", Color(0xFF6F86C6)),
    TdayListColorOption("CORAL", Color(0xFFD39A82)),
    TdayListColorOption("TEAL", Color(0xFF67AAA7)),
    TdayListColorOption("SLATE", Color(0xFF7F8996)),
    TdayListColorOption("BLUE", Color(0xFF6F9FCE)),
    TdayListColorOption("PURPLE", Color(0xFF9A86CF)),
    TdayListColorOption("ROSE", Color(0xFFC98299)),
    TdayListColorOption("LIGHT_RED", Color(0xFFD58D8D)),
    TdayListColorOption("BRICK", Color(0xFFAD786E)),
    TdayListColorOption("YELLOW", Color(0xFFCFB866)),
    TdayListColorOption("LIME", Color(0xFF8DBB73)),
    TdayListColorOption("ORANGE", Color(0xFFD69B63)),
    TdayListColorOption("RED", Color(0xFFD97873)),
)

private val TdayListColorMap = TdayListColorOptions.associate { it.key to it.color }

fun tdayPriorityColor(priority: String): Color {
    return when (priority.trim().lowercase(Locale.getDefault())) {
        "high", "urgent", "important" -> TdayPriorityHigh
        "medium" -> TdayPriorityMedium
        else -> TdayPriorityLow
    }
}

fun tdayListAccentColor(colorKey: String?): Color {
    return tdayListAccentColorOrNull(colorKey)
        ?: TdayListColorMap.getValue(TDAY_DEFAULT_LIST_COLOR_KEY)
}

fun tdayListAccentColorOrNull(colorKey: String?): Color? {
    val normalizedKey = normalizeTdayListColorKeyOrNull(colorKey) ?: return null
    return TdayListColorMap[normalizedKey]
}

fun normalizeTdayListColorKey(colorKey: String?): String {
    return normalizeTdayListColorKeyOrNull(colorKey) ?: TDAY_DEFAULT_LIST_COLOR_KEY
}

fun isTdayListColorKeySupported(colorKey: String): Boolean {
    return normalizeTdayListColorKeyOrNull(colorKey) != null
}

private fun normalizeTdayListColorKeyOrNull(colorKey: String?): String? {
    val candidate = colorKey
        ?.trim()
        ?.uppercase(Locale.getDefault())
        ?: return null

    val normalized = when (candidate) {
        "GREEN" -> "LIME"
        "GRAY" -> "SLATE"
        else -> candidate
    }

    return normalized.takeIf { it in TdayListColorMap }
}
