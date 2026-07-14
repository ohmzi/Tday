package com.ohmz.tday.shared.routes

object ApiRoutes {
    const val ApiPrefix = "/api"

    object Auth {
        const val Base = "$ApiPrefix/auth"
        const val Csrf = "$Base/csrf"
        const val Register = "$Base/register"
        const val LoginChallenge = "$Base/login-challenge"
        const val CredentialsKey = "$Base/credentials-key"
        const val CredentialsCallback = "$Base/callback/credentials"
        const val Session = "$Base/session"
        const val Logout = "$Base/logout"
    }

    object Todo {
        const val Base = "$ApiPrefix/todo"
        const val Complete = "$Base/complete"
        const val Uncomplete = "$Base/uncomplete"
        const val Prioritize = "$Base/prioritize"
        const val Reorder = "$Base/reorder"
        const val Instance = "$Base/instance"
        const val Overdue = "$Base/overdue"
        const val Nlp = "$Base/nlp"
        const val Summary = "$Base/summary"

        fun demote(todoId: String) = "$Base/$todoId/demote"
    }

    object List {
        const val Base = "$ApiPrefix/list"

        fun members(listId: String) = "$Base/$listId/members"
        fun leave(listId: String) = "$Base/$listId/leave"
    }

    object Floater {
        const val Base = "$ApiPrefix/floater"
        const val Complete = "$Base/complete"
        const val Uncomplete = "$Base/uncomplete"
        const val Prioritize = "$Base/prioritize"
        const val Reorder = "$Base/reorder"

        fun promote(floaterId: String) = "$Base/$floaterId/promote"
    }

    object FloaterList {
        const val Base = "$ApiPrefix/floaterList"

        fun members(listId: String) = "$Base/$listId/members"
        fun leave(listId: String) = "$Base/$listId/leave"
    }

    object Completed {
        const val Base = "$ApiPrefix/completedTodo"
    }

    object CompletedFloater {
        const val Base = "$ApiPrefix/completedFloater"
    }

    object Preferences {
        const val Base = "$ApiPrefix/preferences"
    }

    object Export {
        /** GET — the full portable bundle for the authenticated user. */
        const val Base = "$ApiPrefix/export"

        /** POST — restore/merge a bundle (supports dryRun preview). */
        const val Import = "$ApiPrefix/import"
    }

    object User {
        const val Base = "$ApiPrefix/user"
        const val Profile = "$Base/profile"
        const val ChangePassword = "$Base/change-password"
        const val Search = "$Base/search"
    }

    object Timezone {
        const val Base = "$ApiPrefix/timezone"
    }

    object AppSettings {
        const val Base = "$ApiPrefix/app-settings"
    }

    object Admin {
        const val Users = "$ApiPrefix/admin/users"
    }

    object Mobile {
        const val Probe = "$ApiPrefix/mobile/probe"
    }
}
