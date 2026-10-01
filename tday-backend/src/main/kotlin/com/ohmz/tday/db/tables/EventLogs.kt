package com.ohmz.tday.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object EventLogs : Table("eventLog") {
    val id = varchar("id", 30)
    val capturedTime = datetime("capturedTime")
    val eventName = text("eventName")
    val log = text("log")

    override val primaryKey = PrimaryKey(id)
}
