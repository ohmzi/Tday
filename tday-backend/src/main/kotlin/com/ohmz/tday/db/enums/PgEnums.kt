package com.ohmz.tday.db.enums

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

class PGEnum<T : Enum<T>>(enumTypeName: String, enumValue: T?) : PGobject() {
    init {
        type = enumTypeName
        value = enumValue?.name
    }
}

inline fun <reified T : Enum<T>> Table.pgEnum(
    columnName: String,
    pgTypeName: String,
): Column<T> = customEnumeration(
    columnName,
    pgTypeName,
    { value -> enumValueOf<T>(value as String) },
    { PGEnum(pgTypeName, it) },
)

enum class UserRole { ADMIN, USER }
enum class ApprovalStatus { APPROVED, PENDING }
enum class SortBy { dtstart, due, duration, priority }
enum class GroupBy {
    dtstart, due, duration, priority, rrule, project;

    companion object {
        fun fromPrisma(value: String): GroupBy = when (value) {
            "list" -> project
            else -> valueOf(value)
        }

        fun toPrisma(value: GroupBy): String = when (value) {
            project -> "list"
            else -> value.name
        }
    }
}
enum class Direction { Ascending, Descending }
enum class Priority { Low, Medium, High }
enum class RepeatInterval { daily, weekly, monthly, weekdays }

enum class ListColor {
    RED, ORANGE, YELLOW, LIME, BLUE, PURPLE, PINK, TEAL,
    CORAL, GOLD, DEEP_BLUE, ROSE, LIGHT_RED, BRICK, SLATE
}
