package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object AppConfigs : Table("AppConfig") {
    val id = integer("id")
    val aiSummaryEnabled = bool("aiSummaryEnabled").default(true)
    val updatedById = varchar("updatedById", 30).references(Users.id).nullable()
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, updatedById)
    }
}
