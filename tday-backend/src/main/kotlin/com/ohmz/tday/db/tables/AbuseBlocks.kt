package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Longer-lived path blocks for callers showing sustained abuse (see V22__abuse_blocks.sql).
 *
 * The row exists before any block does: [blockedUntil] is null while it is only accumulating
 * signals for the current window.
 */
object AbuseBlocks : Table("abuse_blocks") {
    val id = varchar("id", 30)
    val subjectHash = varchar("subject_hash", 255)
    val scope = varchar("scope", 16)
    val blockedUntil = datetime("blocked_until").nullable()
    val strikes = integer("strikes").default(0)
    val signalCount = integer("signal_count").default(0)
    val pendingSignups = integer("pending_signups").default(0)
    val windowStart = datetime("window_start")
    val reason = varchar("reason", 64).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(subjectHash, scope)
        index(false, blockedUntil)
    }
}
