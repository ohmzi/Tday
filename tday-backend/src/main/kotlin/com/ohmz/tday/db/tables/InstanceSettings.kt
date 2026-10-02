package com.ohmz.tday.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/**
 * One row per instance-wide setting an admin can change at runtime (see V32__instance_settings.sql).
 * A missing row means the setting is at its default.
 */
object InstanceSettings : Table("instance_settings") {
    val settingKey = varchar("key", 64)
    val settingValue = varchar("value", 255)
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(settingKey)
}
