package com.ohmz.tday.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMIN,
    USER,
}

@Serializable
enum class ApprovalStatus {
    APPROVED,
    PENDING,
}

@Serializable
enum class SortBy {
    due,
    priority,
}

@Serializable
enum class GroupBy {
    due,
    priority,
    rrule,
    project;

    companion object {
        // Unknown/future values fall back to `due` rather than throwing
        // IllegalArgumentException (valueOf) — a persisted or client-sent string we
        // don't recognize should degrade gracefully, not crash preference loading.
        fun fromApi(value: String): GroupBy = when (value) {
            "list" -> project
            else -> entries.firstOrNull { it.name == value } ?: due
        }

        fun toApi(value: GroupBy): String = when (value) {
            project -> "list"
            else -> value.name
        }
    }
}

@Serializable
enum class Direction {
    Ascending,
    Descending,
}

@Serializable
enum class Priority {
    Low,
    Medium,
    High;

    companion object {
        /** Non-throwing parse for the raw `priority` strings carried in DTOs. */
        fun fromApiOrDefault(value: String?, default: Priority = Low): Priority =
            entries.firstOrNull { it.name == value } ?: default
    }
}

@Serializable
enum class RepeatInterval {
    daily,
    weekly,
    monthly,
    weekdays,
}

@Serializable
enum class ListColor {
    RED,
    ORANGE,
    YELLOW,
    LIME,
    BLUE,
    PURPLE,
    PINK,
    TEAL,
    CORAL,
    GOLD,
    DEEP_BLUE,
    ROSE,
    LIGHT_RED,
    BRICK,
    SLATE;

    companion object {
        /** Non-throwing parse for the raw `color` strings carried in DTOs. */
        fun fromApiOrNull(value: String?): ListColor? =
            entries.firstOrNull { it.name == value }
    }
}
