package com.ohmz.tday.db.enums

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

typealias UserRole = com.ohmz.tday.shared.model.UserRole
typealias ApprovalStatus = com.ohmz.tday.shared.model.ApprovalStatus
typealias SortBy = com.ohmz.tday.shared.model.SortBy
typealias GroupBy = com.ohmz.tday.shared.model.GroupBy
typealias Direction = com.ohmz.tday.shared.model.Direction
typealias Priority = com.ohmz.tday.shared.model.Priority
typealias RepeatInterval = com.ohmz.tday.shared.model.RepeatInterval
typealias ListColor = com.ohmz.tday.shared.model.ListColor

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
