package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ColumnType
import java.sql.Timestamp
import java.time.LocalDateTime

class TimestampArrayColumnType : ColumnType<List<LocalDateTime>>() {
    override fun sqlType(): String = "timestamp(3)[]"

    override fun valueFromDB(value: Any): List<LocalDateTime> {
        return when (value) {
            is java.sql.Array -> {
                val array = value.array
                when (array) {
                    is Array<*> -> array.mapNotNull { element ->
                        when (element) {
                            is Timestamp -> element.toLocalDateTime()
                            is LocalDateTime -> element
                            else -> null
                        }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    override fun notNullValueToDB(value: List<LocalDateTime>): Any {
        return value.map { Timestamp.valueOf(it) }.toTypedArray()
    }

    override fun nonNullValueToString(value: List<LocalDateTime>): String {
        return value.joinToString(",", "ARRAY[", "]::timestamp(3)[]") { ts ->
            "'${Timestamp.valueOf(ts)}'"
        }
    }
}
