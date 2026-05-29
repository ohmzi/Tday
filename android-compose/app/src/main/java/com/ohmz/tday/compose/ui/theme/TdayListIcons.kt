package com.ohmz.tday.compose.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Square
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

data class TdayListIconOption(
    val key: String,
    val icon: ImageVector,
)

const val TDAY_DEFAULT_LIST_ICON_KEY = "inbox"

val TdayListIconOptions = listOf(
    TdayListIconOption(TDAY_DEFAULT_LIST_ICON_KEY, Icons.Rounded.Inbox),
    TdayListIconOption("sun", Icons.Rounded.WbSunny),
    TdayListIconOption("calendar", Icons.Rounded.CalendarToday),
    TdayListIconOption("schedule", Icons.Rounded.Schedule),
    TdayListIconOption("flag", Icons.Rounded.Flag),
    TdayListIconOption("check", Icons.Rounded.Check),
    TdayListIconOption("smile", Icons.Rounded.Mood),
    TdayListIconOption("list", Icons.AutoMirrored.Rounded.List),
    TdayListIconOption("bookmark", Icons.Rounded.Bookmark),
    TdayListIconOption("key", Icons.Rounded.Key),
    TdayListIconOption("gift", Icons.Rounded.CardGiftcard),
    TdayListIconOption("cake", Icons.Rounded.Cake),
    TdayListIconOption("school", Icons.Rounded.School),
    TdayListIconOption("bag", Icons.Rounded.Backpack),
    TdayListIconOption("edit", Icons.Rounded.Edit),
    TdayListIconOption("document", Icons.Rounded.Description),
    TdayListIconOption("book", Icons.AutoMirrored.Rounded.MenuBook),
    TdayListIconOption("work", Icons.Rounded.Work),
    TdayListIconOption("wallet", Icons.Rounded.AccountBalanceWallet),
    TdayListIconOption("money", Icons.Rounded.Payments),
    TdayListIconOption("fitness", Icons.Rounded.FitnessCenter),
    TdayListIconOption("run", Icons.AutoMirrored.Rounded.DirectionsRun),
    TdayListIconOption("food", Icons.Rounded.Restaurant),
    TdayListIconOption("drink", Icons.Rounded.LocalBar),
    TdayListIconOption("health", Icons.Rounded.Medication),
    TdayListIconOption("monitor", Icons.Rounded.DesktopWindows),
    TdayListIconOption("music", Icons.Rounded.MusicNote),
    TdayListIconOption("computer", Icons.Rounded.Computer),
    TdayListIconOption("game", Icons.Rounded.SportsEsports),
    TdayListIconOption("headphones", Icons.Rounded.Headphones),
    TdayListIconOption("eco", Icons.Rounded.Eco),
    TdayListIconOption("pets", Icons.Rounded.Pets),
    TdayListIconOption("child", Icons.Rounded.ChildCare),
    TdayListIconOption("family", Icons.Rounded.FamilyRestroom),
    TdayListIconOption("basket", Icons.Rounded.ShoppingBasket),
    TdayListIconOption("cart", Icons.Rounded.ShoppingCart),
    TdayListIconOption("mall", Icons.Rounded.LocalMall),
    TdayListIconOption("inventory", Icons.Rounded.Inventory),
    TdayListIconOption("soccer", Icons.Rounded.SportsSoccer),
    TdayListIconOption("baseball", Icons.Rounded.SportsBaseball),
    TdayListIconOption("basketball", Icons.Rounded.SportsBasketball),
    TdayListIconOption("football", Icons.Rounded.SportsFootball),
    TdayListIconOption("tennis", Icons.Rounded.SportsTennis),
    TdayListIconOption("train", Icons.Rounded.Train),
    TdayListIconOption("flight", Icons.Rounded.Flight),
    TdayListIconOption("boat", Icons.Rounded.DirectionsBoat),
    TdayListIconOption("car", Icons.Rounded.DirectionsCar),
    TdayListIconOption("umbrella", Icons.Rounded.BeachAccess),
    TdayListIconOption("drop", Icons.Rounded.WaterDrop),
    TdayListIconOption("snow", Icons.Rounded.AcUnit),
    TdayListIconOption("fire", Icons.Rounded.Whatshot),
    TdayListIconOption("tools", Icons.Rounded.Build),
    TdayListIconOption("scissors", Icons.Rounded.ContentCut),
    TdayListIconOption("architecture", Icons.Rounded.Architecture),
    TdayListIconOption("code", Icons.Rounded.Code),
    TdayListIconOption("idea", Icons.Rounded.Lightbulb),
    TdayListIconOption("chat", Icons.Rounded.ChatBubbleOutline),
    TdayListIconOption("alert", Icons.Rounded.PriorityHigh),
    TdayListIconOption("star", Icons.Rounded.Star),
    TdayListIconOption("heart", Icons.Rounded.Favorite),
    TdayListIconOption("circle", Icons.Rounded.Circle),
    TdayListIconOption("square", Icons.Rounded.Square),
    TdayListIconOption("triangle", Icons.Rounded.ChangeHistory),
    TdayListIconOption("home", Icons.Rounded.Home),
    TdayListIconOption("city", Icons.Rounded.LocationCity),
    TdayListIconOption("bank", Icons.Rounded.AccountBalance),
    TdayListIconOption("camera", Icons.Rounded.CameraAlt),
    TdayListIconOption("palette", Icons.Rounded.Palette),
)

private val TdayListIconMap = TdayListIconOptions.associate { it.key to it.icon }

fun tdayListIconForKey(iconKey: String?): ImageVector {
    val normalizedKey = normalizeTdayListIconKeyOrNull(iconKey)
    return normalizedKey?.let { TdayListIconMap[it] }
        ?: TdayListIconMap.getValue(TDAY_DEFAULT_LIST_ICON_KEY)
}

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
