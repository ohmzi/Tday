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
    dtstart,
    due,
    duration,
    priority,
}

@Serializable
enum class GroupBy {
    dtstart,
    due,
    duration,
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

        fun fromPrisma(value: String): GroupBy = fromApi(value)

        fun toPrisma(value: GroupBy): String = toApi(value)
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
