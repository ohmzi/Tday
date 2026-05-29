package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object CompletedFloaters : Table("completedfloaters") {
    val id = varchar("id", 30)
    val originalFloaterID = varchar("originalFloaterID", 30)
    val title = text("title")
    val description = text("description").nullable()
    val priority = pgEnum<Priority>("priority", "\"Priority\"")
    val completedAt = datetime("completedAt")
    val daysToComplete = decimal("daysToComplete", 10, 2)
    val userID = varchar("userID", 30).references(Users.id).index()
    val listID = varchar("projectID", 30).references(FloaterLists.id).nullable().index()
    val listName = varchar("projectName", 255).nullable()
    val listColor = varchar("projectColor", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}
