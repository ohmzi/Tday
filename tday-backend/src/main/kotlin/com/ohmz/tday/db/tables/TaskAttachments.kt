package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Pictures attached to a task. Both task types are supported, and because scheduled tasks and
 * floaters are separate entities, exactly one of [todoID] / [floaterID] is set on any row — the
 * migration enforces that with a CHECK constraint.
 *
 * Only metadata lives here. The bytes are on disk under the configured attachment directory,
 * addressed by [storageKey]; keeping images out of Postgres keeps `pg_dump` a reasonable size.
 * The row is the record of truth, so deleting it is what makes an attachment gone.
 */
object TaskAttachments : Table("task_attachments") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id).index()
    val todoID = varchar("todoID", 30).references(Todos.id).nullable().index()
    /**
     * No `.references(Floaters.id)`: `floaters` is Exposed-managed and does not exist when Flyway
     * runs, so the migration cannot carry that FK (see V24). Cleanup is owned by the services.
     */
    val floaterID = varchar("floaterID", 30).nullable().index()

    /** User-supplied, encrypted at rest like every other piece of user text. */
    val fileName = text("fileName")
    val contentType = varchar("contentType", 100)
    val sizeBytes = long("sizeBytes")
    val width = integer("width").nullable()
    val height = integer("height").nullable()

    /** Path relative to the attachment root. Server-generated; never a client filename. */
    val storageKey = text("storageKey")
    val thumbnailKey = text("thumbnailKey").nullable()
    val createdAt = datetime("createdAt")

    override val primaryKey = PrimaryKey(id)
}
