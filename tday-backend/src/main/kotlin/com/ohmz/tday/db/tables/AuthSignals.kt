package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object AuthSignals : Table("AuthSignal") {
    val id = varchar("id", 30)
    val identifierHash = varchar("identifierHash", 255).uniqueIndex()
    val lastIpHash = varchar("lastIpHash", 255).nullable()
    val lastDeviceHash = varchar("lastDeviceHash", 255).nullable()
    val lastSeenAt = datetime("lastSeenAt")
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, lastSeenAt)
    }
}
