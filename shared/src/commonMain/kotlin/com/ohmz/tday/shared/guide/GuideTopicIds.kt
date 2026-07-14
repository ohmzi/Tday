package com.ohmz.tday.shared.guide

/**
 * Stable topic-id constants so contextual "?" buttons and deep links on every
 * platform reference typed ids instead of magic strings. Android gets these
 * compile-checked for free; the web and iOS receive the same ids in the exported
 * artifacts. Keep in sync with [GuideCatalog].
 */
object GuideTopicIds {
    const val ROOT_FEEDS = "root-feeds"
    const val CREATE_TASK = "create-task"
    const val NLP_DATE_SYNTAX = "nlp-date-syntax"
    const val CALENDAR_VIEWS = "calendar-views"
    const val PRIORITIES = "priorities"
    const val PIN_TASK = "pin-task"
    const val SWIPE_ACTIONS = "swipe-actions"
    const val LONG_PRESS_ACTIONS = "long-press-actions"
    const val DRAG_REORDER = "drag-reorder"
    const val FLOATERS_VS_TODOS = "floaters-vs-todos"
    const val PROMOTE_AND_FLOAT = "promote-and-float"
    const val QUICK_DEFER = "quick-defer"
    const val DAY_DONE = "day-done"
    const val FLOATER_LISTS = "floater-lists"
    const val SCHEDULED_LISTS = "scheduled-lists"
    const val COMPLETED_HISTORY = "completed-history"
    const val OVERDUE_VIEW = "overdue-view"
    const val MORNING_SWEEP = "morning-sweep"
    const val SEARCH_TASKS = "search-tasks"
    const val RECURRENCE_PRESETS = "recurrence-presets"
    const val REMINDER_SNOOZE = "reminder-snooze"
    const val DAY_AHEAD = "day-ahead"
    const val REMINDERS = "reminders"
    const val PUSH_NOTIFICATIONS = "push-notifications"
    const val HOME_WIDGET = "home-widget"
    const val WIDGET_QUICK_ADD = "widget-quick-add"
    const val INTERACTIVE_WIDGETS = "interactive-widgets"
    const val CARPLAY = "carplay"
    const val ANDROID_CAR = "android-car"
    const val LOCAL_MODE = "local-mode"
    const val SERVER_MODE = "server-mode"
    const val OFFLINE_SYNC = "offline-sync"
    const val IN_APP_UPDATE = "in-app-update"
    const val API_KEY_HOMARR = "api-key-homarr"
    const val AI_SUMMARY = "ai-summary"
    const val KEYBOARD_SUBMIT = "keyboard-submit"
}
