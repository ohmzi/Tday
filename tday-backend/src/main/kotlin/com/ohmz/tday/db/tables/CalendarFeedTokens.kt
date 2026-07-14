package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object CalendarFeedTokens : Table("calendar_feed_tokens") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id)
    val tokenHash = text("token_hash")
    val tokenPreview = varchar("token_preview", 20)
    val enabled = bool("enabled").default(true)
    val lastUsedAt = datetime("last_used_at").nullable()
    val createdAt = datetime("createdAt")
    val revokedAt = datetime("revokedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}
