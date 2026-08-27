package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object WebhookSubscriptions : Table("webhook_subscriptions") {
    val id = varchar("id", 30)
    // CASCADE is stated rather than left to Exposed's RESTRICT default, so the declaration
    // matches the constraint V17 created. This table is not in the
    // createMissingTablesAndColumns list in DatabaseConfig, so nothing reconciles it today and
    // no migration is needed; saying RESTRICT here is what would silently replace the live
    // CASCADE the moment somebody adds it to that list, which is how push_subscriptions broke.
    val userID = varchar("userID", 30).references(Users.id, onDelete = ReferenceOption.CASCADE)
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
