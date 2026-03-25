package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object CronLogs : Table("CronLog") {
    val id = varchar("id", 30)
    val runAt = datetime("runAt")
    val success = bool("success")
    val log = text("log")

    override val primaryKey = PrimaryKey(id)
}
