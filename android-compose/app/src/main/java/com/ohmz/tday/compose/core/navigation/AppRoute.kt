package com.ohmz.tday.compose.core.navigation

import android.net.Uri

sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object ServerSetup : AppRoute("server-setup")
    data object Login : AppRoute("login")
    data object Home : AppRoute("home")
    data object TodayTodos : AppRoute("todos/today")
    data object ScheduledTodos : AppRoute("todos/scheduled")
    data object AllTodos : AppRoute("todos/all")
    data object FlaggedTodos : AppRoute("todos/flagged")
    data object ListTodos : AppRoute("todos/list/{listId}/{listName}") {
        fun create(listId: String, listName: String): String {
            return "todos/list/$listId/${Uri.encode(listName)}"
        }
    }

    data object Completed : AppRoute("completed")
    data object Notes : AppRoute("notes")
    data object Calendar : AppRoute("calendar")
    data object Settings : AppRoute("settings")
}
