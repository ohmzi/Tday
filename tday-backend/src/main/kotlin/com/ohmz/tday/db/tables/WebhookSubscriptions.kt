package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object WebhookSubscriptions : Table("webhook_subscriptions") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id)
    val url = text("url")
    val secret = text("secret")
    val eventFilter = text("event_filter").nullable()
    val enabled = bool("enabled").default(true)
    val consecutiveFailures = integer("consecutive_failures").default(0)
    val lastStatus = integer("last_status").nullable()
    val lastAttemptAt = datetime("last_attempt_at").nullable()
    val createdAt = datetime("createdAt")

    override val primaryKey = PrimaryKey(id)
}
