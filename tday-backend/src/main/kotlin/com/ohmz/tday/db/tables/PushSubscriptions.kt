package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object PushSubscriptions : Table("push_subscriptions") {
    val id = varchar("id", 30)

    // CASCADE is stated rather than left to Exposed's RESTRICT default. This table is in the
    // SchemaUtils.createMissingTablesAndColumns list in DatabaseConfig, which runs after Flyway
    // and rewrites any constraint whose live rule differs from the one declared here: the
    // ON DELETE CASCADE that V7 created was silently replaced with RESTRICT, and deleting an
    // account that had ever enabled notifications then failed on it. V26 realigns the live
    // constraint with this declaration.
    val userID = varchar("userID", 30).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val endpoint = text("endpoint")
    val p256dh = text("p256dh")
    val auth = varchar("auth", 64)
    // "webpush" (VAPID/encrypted, browsers + iOS) or "unifiedpush" (plain POST to the
    // distributor endpoint, Android self-hosters). Defaults to webpush via V18.
    val transport = varchar("transport", 20).default("webpush")
    val createdAt = datetime("createdAt")

    override val primaryKey = PrimaryKey(id)
}
