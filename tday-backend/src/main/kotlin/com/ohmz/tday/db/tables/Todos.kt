package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Todos : Table("todos") {
    val id = varchar("id", 30)
    val title = text("title")
    val description = text("description").nullable()
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")
    val userID = varchar("userID", 30).references(Users.id).index()
    val pinned = bool("pinned").default(false)
    val order = integer("order").autoIncrement()
    val priority = pgEnum<Priority>("priority", "\"Priority\"")
    val dtstart = datetime("dtstart")
    val due = datetime("due")
    val exdates = registerColumn<List<LocalDateTime>>("exdates", TimestampArrayColumnType())
    val durationMinutes = integer("durationMinutes").default(30)
    val rrule = text("rrule").nullable()
    val timeZone = varchar("timeZone", 64).default("UTC")
    val completed = bool("completed").default(false)
    val listID = varchar("projectID", 30).references(Lists.id).nullable().index()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userID, listID)
    }
}
