package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object CalendarFeedTokens : Table("calendar_feed_tokens") {
    val id = varchar("id", 30)
    // CASCADE is stated rather than left to Exposed's RESTRICT default, so the declaration
    // matches the constraint V16 created. This table is not in the
    // createMissingTablesAndColumns list in DatabaseConfig, so nothing reconciles it today and
    // no migration is needed; saying RESTRICT here is what would silently replace the live
    // CASCADE the moment somebody adds it to that list, which is how push_subscriptions broke.
    val userID = varchar("userID", 30).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = text("token_hash")
    val tokenPreview = varchar("token_preview", 20)
    val enabled = bool("enabled").default(true)
    val lastUsedAt = datetime("last_used_at").nullable()
    val createdAt = datetime("createdAt")
    val revokedAt = datetime("revokedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}
