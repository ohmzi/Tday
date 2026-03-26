package com.ohmz.tday.shared.routes

object ApiRoutes {
    const val ApiPrefix = "/api"

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
    }

    object List {
        const val Base = "$ApiPrefix/list"
    }

    object Completed {
        const val Base = "$ApiPrefix/completedTodo"
    }

    object Preferences {
        const val Base = "$ApiPrefix/preferences"
    }

    object AppSettings {
        const val Base = "$ApiPrefix/app-settings"
    }

    object Admin {
        const val Settings = "$ApiPrefix/admin/settings"
    }

    object Mobile {
        const val Probe = "$ApiPrefix/mobile/probe"
    }
}
