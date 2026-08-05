package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/** Append-only history of alerts the admin was notified about (see V23__security_alerts.sql). */
object SecurityAlerts : Table("security_alerts") {
    val id = varchar("id", 30)
    val alertType = varchar("alert_type", 64)
    val detail = text("detail")
    val suppressedCount = integer("suppressed_count").default(0)
    val pushed = bool("pushed").default(false)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, createdAt)
    }
}
