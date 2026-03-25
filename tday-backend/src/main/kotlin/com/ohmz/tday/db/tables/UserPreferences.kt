package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table

object UserPreferences : Table("UserPreferences") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).uniqueIndex().references(Users.id)
    val sortBy = pgEnum<SortBy>("sortBy", "\"SortBy\"").nullable()
    val groupBy = pgEnum<GroupBy>("groupBy", "\"GroupBy\"").nullable()
    val direction = pgEnum<Direction>("direction", "\"Direction\"").nullable()

    override val primaryKey = PrimaryKey(id)
}
