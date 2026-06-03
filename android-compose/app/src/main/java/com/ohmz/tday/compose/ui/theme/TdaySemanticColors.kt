package com.ohmz.tday.compose.ui.theme

import androidx.compose.ui.graphics.Color
import com.ohmz.tday.compose.ui.priority.isImportantPriority
import com.ohmz.tday.compose.ui.priority.isUrgentPriority
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
val TdayTitleIconDayAccent = Color(0xFFF4C542)
val TdayTitleIconNightAccent = Color(0xFFA8B8E8)
val TdayTaskCompleteAccent = Color(0xFF6FBF86)
val TdaySwipeEditBackground = Color(0xFF4C7DDE)
val TdaySwipeDeleteBackground = Color(0xFFFF453A)
val TdayStatusSuccess = Color(0xFF4CAF50)

val TdayListColorOptions = listOf(
    TdayListColorOption("PINK", Color(0xFFE05299)),
    TdayListColorOption("GOLD", Color(0xFFE8A530)),
    TdayListColorOption("DEEP_BLUE", Color(0xFF3C9ADD)),
    TdayListColorOption("CORAL", Color(0xFFE6664C)),
    TdayListColorOption("TEAL", Color(0xFF2EB8AC)),
    TdayListColorOption("SLATE", Color(0xFF3E4774)),
    TdayListColorOption("BLUE", Color(0xFF6EA8E1)),
    TdayListColorOption("PURPLE", Color(0xFF7D67B6)),
    TdayListColorOption("ROSE", Color(0xFFD1617D)),
    TdayListColorOption("LIGHT_RED", Color(0xFFE06C6C)),
    TdayListColorOption("BRICK", Color(0xFFC64C39)),
    TdayListColorOption("YELLOW", Color(0xFFE8BA30)),
    TdayListColorOption("LIME", Color(0xFF46B963)),
    TdayListColorOption("ORANGE", Color(0xFFE28736)),
    TdayListColorOption("RED", Color(0xFFDF3A3A)),
)

private val TdayListColorMap = TdayListColorOptions.associate { it.key to it.color }

fun tdayPriorityColor(priority: String): Color {
    return when {
        isUrgentPriority(priority) -> TdayPriorityHigh
        isImportantPriority(priority) -> TdayPriorityMedium
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
