package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Lists : Table("Project") {
    val id = varchar("id", 30)
    val name = text("name")
    val color = pgEnum<ListColor>("color", "\"ProjectColor\"").nullable()
    val iconKey = varchar("iconKey", 64).nullable()
    val userID = varchar("userID", 30).references(Users.id).index()
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)
}
