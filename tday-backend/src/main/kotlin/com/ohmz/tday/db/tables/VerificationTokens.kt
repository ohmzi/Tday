package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object VerificationTokens : Table("VerificationToken") {
    val identifier = varchar("identifier", 255)
    val token = text("token")
    val expires = datetime("expires")

    override val primaryKey = PrimaryKey(identifier, token)
}
