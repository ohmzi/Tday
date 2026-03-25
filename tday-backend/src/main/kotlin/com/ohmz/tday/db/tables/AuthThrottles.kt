package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object AuthThrottles : Table("AuthThrottle") {
    val id = varchar("id", 30)
    val scope = varchar("scope", 64)
    val bucketKey = varchar("bucketKey", 255)
    val requestCount = integer("requestCount").default(0)
    val failureCount = integer("failureCount").default(0)
    val windowStart = datetime("windowStart")
    val lockUntil = datetime("lockUntil").nullable()
    val lastFailureAt = datetime("lastFailureAt").nullable()
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(scope, bucketKey)
        index(false, scope, lockUntil)
    }
}
