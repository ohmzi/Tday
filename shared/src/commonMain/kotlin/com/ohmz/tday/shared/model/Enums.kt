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
        fun fromApi(value: String): GroupBy = when (value) {
            "list" -> project
            else -> valueOf(value)
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
    High,
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
    SLATE,
}
