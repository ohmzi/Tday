package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object TodoInstances : Table("todo_instances") {
    val id = varchar("id", 30)
    val todoId = varchar("todoId", 30).references(Todos.id)
    val recurId = text("recurId")
    val instanceDate = datetime("instanceDate")
    val overriddenTitle = text("overriddenTitle").nullable()
    val overriddenDescription = text("overriddenDescription").nullable()
    val overriddenPriority = pgEnum<Priority>("overriddenPriority", "\"Priority\"").nullable()
    val overriddenDtstart = datetime("overriddenDtstart").nullable()
    val overriddenDurationMinutes = integer("overriddenDurationMinutes").nullable()
    val overriddenDue = datetime("overriddenDue").nullable()
    val completedAt = datetime("completedAt").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(todoId, instanceDate)
    }
}
