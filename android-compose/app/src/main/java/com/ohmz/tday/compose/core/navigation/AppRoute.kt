package com.ohmz.tday.compose.core.navigation

import android.net.Uri

sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object ServerSetup : AppRoute("server-setup")
    data object Login : AppRoute("login")
    data object ForgotPassword : AppRoute("forgot-password")
    data object ScheduledTaskHome : AppRoute("home")
    data object FloaterTaskHome : AppRoute("floater")
    data object TodayTodos : AppRoute("todos/today")
    data object CreateTodayTodo : AppRoute("todos/create?target={target}")
    data object OverdueTodos : AppRoute("todos/overdue")
    data object ScheduledTodos : AppRoute("todos/scheduled")
    data object AllTodos : AppRoute("todos/all?highlightTodoId={highlightTodoId}") {
        fun create(highlightTodoId: String? = null): String {
            return if (highlightTodoId.isNullOrBlank()) {
                "todos/all"
            } else {
                "todos/all?highlightTodoId=${Uri.encode(highlightTodoId)}"
            }
        }
    }
    data object PriorityTodos : AppRoute("todos/priority")
    data object ListTodos : AppRoute("todos/list/{listId}/{listName}") {
        fun create(listId: String, listName: String): String {
            return "todos/list/$listId/${Uri.encode(listName)}"
        }
    }
    data object FloaterListTodos : AppRoute("floater/list/{listId}/{listName}") {
        fun create(listId: String, listName: String): String {
            return "floater/list/$listId/${Uri.encode(listName)}"
        }
    }

    data object Completed : AppRoute("completed?scope={scope}") {
        /**
         * The route with no tab named — read off the pattern rather than spelled
         * a second time, so the two cannot drift.
         */
        private val unscoped: String = route.substringBefore('?')

        /**
         * The completion history, optionally opened on one of its two tabs.
         *
         * The scope rides as a query parameter for the reason `AllTodos`'s
         * highlight does: the argument is optional, its `navArgument` declares a
         * null default, so the bare `"completed"` still matches this pattern and
         * every arrival that names no tab — the deep link, a shortcut, a
         * notification — opens the first one.
         */
        fun create(scope: CompletedScope? = null): String =
            if (scope == CompletedScope.Floater) "$unscoped?scope=${CompletedScope.Floater.wire}" else unscoped
    }
    data object Calendar : AppRoute("calendar")
    data object Car : AppRoute("car")
    data object Settings : AppRoute("settings")
    data object LatestRelease : AppRoute("latest-release")
    data object MorningSweep : AppRoute("morning-sweep")
    data object HelpGuide : AppRoute("help-guide?topic={topic}") {
        fun create(topic: String? = null): String {
            return if (topic.isNullOrBlank()) "help-guide" else "help-guide?topic=${Uri.encode(topic)}"
        }
    }
}
