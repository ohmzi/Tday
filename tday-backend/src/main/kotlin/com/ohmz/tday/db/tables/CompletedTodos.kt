package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal

object CompletedTodos : Table("CompletedTodo") {
    val id = varchar("id", 30)
    val originalTodoID = varchar("originalTodoID", 30)
    val title = text("title")
    val description = text("description").nullable()
    val priority = pgEnum<Priority>("priority", "\"Priority\"")
    val completedAt = datetime("completedAt")
    val dtstart = datetime("dtstart")
    val due = datetime("due")
    val completedOnTime = bool("completedOnTime")
    val daysToComplete = decimal("daysToComplete", 10, 2)
    val rrule = text("rrule").nullable()
    val userID = varchar("userID", 30).references(Users.id).index()
    val instanceDate = datetime("instanceDate").nullable()
    val listName = varchar("projectName", 255).nullable()
    val listColor = varchar("projectColor", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}
