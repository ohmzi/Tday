package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserApiKeys : Table("user_api_keys") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id)
    val keyHash = text("key_hash")
    val keyPreview = varchar("key_preview", 20)
    val enabled = bool("enabled").default(true)
    val lastUsedAt = datetime("last_used_at").nullable()
    val createdAt = datetime("createdAt")
    val revokedAt = datetime("revokedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}
