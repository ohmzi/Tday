package com.ohmz.tday.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object CronLogs : Table("CronLog") {
    val id = varchar("id", 30)
    val runAt = datetime("runAt")
    val success = bool("success")
    val log = text("log")

    override val primaryKey = PrimaryKey(id)
}
