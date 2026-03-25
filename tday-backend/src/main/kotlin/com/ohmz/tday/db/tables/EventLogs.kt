package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object EventLogs : Table("eventLog") {
    val id = varchar("id", 30)
    val capturedTime = datetime("capturedTime")
    val eventName = text("eventName")
    val log = text("log")

    override val primaryKey = PrimaryKey(id)
}
