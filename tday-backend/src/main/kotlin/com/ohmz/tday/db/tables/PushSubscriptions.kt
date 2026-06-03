package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object PushSubscriptions : Table("push_subscriptions") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id)
    val endpoint = text("endpoint")
    val p256dh = text("p256dh")
    val auth = varchar("auth", 64)
    val createdAt = datetime("createdAt")

    override val primaryKey = PrimaryKey(id)
}
