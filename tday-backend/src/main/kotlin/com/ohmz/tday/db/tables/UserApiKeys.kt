package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserApiKeys : Table("user_api_keys") {
    val id = varchar("id", 30)
    // CASCADE is stated rather than left to Exposed's RESTRICT default, so the declaration
    // matches the constraint V9 created. This table is not in the
    // createMissingTablesAndColumns list in DatabaseConfig, so nothing reconciles it today and
    // no migration is needed; saying RESTRICT here is what would silently replace the live
    // CASCADE the moment somebody adds it to that list, which is how push_subscriptions broke.
    val userID = varchar("userID", 30).references(Users.id, onDelete = ReferenceOption.CASCADE)
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
