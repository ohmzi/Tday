package com.ohmz.tday.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object VerificationTokens : Table("VerificationToken") {
    val identifier = varchar("identifier", 255)
    val token = text("token")
    val expires = datetime("expires")

    override val primaryKey = PrimaryKey(identifier, token)
}
