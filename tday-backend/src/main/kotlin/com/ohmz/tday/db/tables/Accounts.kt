package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Accounts : Table("Account") {
    val userId = varchar("userId", 30).references(Users.id).index()
    val type = varchar("type", 64)
    val provider = varchar("provider", 64)
    val providerAccountId = varchar("providerAccountId", 255)
    val refreshToken = text("refresh_token").nullable()
    val accessToken = text("access_token").nullable()
    val expiresAt = integer("expires_at").nullable()
    val tokenType = varchar("token_type", 64).nullable()
    val scope = text("scope").nullable()
    val idToken = text("id_token").nullable()
    val sessionState = text("session_state").nullable()
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(provider, providerAccountId)
}
