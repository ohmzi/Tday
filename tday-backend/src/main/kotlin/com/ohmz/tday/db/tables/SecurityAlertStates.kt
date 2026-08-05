package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * One row per alert type holding the coalescing clock.
 *
 * Persisted rather than in-memory so a container restart cannot reset a cooldown — a restart
 * loop would otherwise become an alert loop.
 */
object SecurityAlertStates : Table("security_alert_state") {
    val alertType = varchar("alert_type", 64)
    val lastSentAt = datetime("last_sent_at").nullable()
    val pendingCount = integer("pending_count").default(0)
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(alertType)
}
