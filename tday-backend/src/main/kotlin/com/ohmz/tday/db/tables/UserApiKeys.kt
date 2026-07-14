package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserApiKeys : Table("user_api_keys") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id)
    val keyHash = text("key_hash")
    val keyPreview = varchar("key_preview", 20)
    val enabled = bool("enabled").default(true)
    val label = varchar("label", 60).nullable()
    // Access scope: "READ" (read-only integrations, e.g. dashboards) or "FULL"
    // (unrestricted). Existing rows default to FULL via the V15 migration.
    val scope = varchar("scope", 10).default("FULL")
    val expiresAt = datetime("expires_at").nullable()
    val lastUsedAt = datetime("last_used_at").nullable()
    val createdAt = datetime("createdAt")
    val revokedAt = datetime("revokedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}
