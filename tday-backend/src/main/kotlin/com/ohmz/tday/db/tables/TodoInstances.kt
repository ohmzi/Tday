package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object TodoInstances : Table("todo_instances") {
    val id = varchar("id", 30)

    // An instance is an override of one occurrence of its todo and has no meaning without it, so
    // deleting the todo should take it with it. This table is in the
    // SchemaUtils.createMissingTablesAndColumns list in DatabaseConfig, so the live constraint is
    // whatever this line says: leaving onDelete off meant Exposed's RESTRICT default, and the
    // only reason deleting a todo worked at all was that every caller happened to clear the
    // instances first. V26 brings the live constraint up to the CASCADE declared here.
    val todoId = varchar("todoId", 30).references(Todos.id, onDelete = ReferenceOption.CASCADE)
    val recurId = text("recurId")
    val instanceDate = datetime("instanceDate")
    val overriddenTitle = text("overriddenTitle").nullable()
    val overriddenDescription = text("overriddenDescription").nullable()
    val overriddenPriority = pgEnum<Priority>("overriddenPriority", "\"Priority\"").nullable()
    val overriddenDue = datetime("overriddenDue").nullable()
    val completedAt = datetime("completedAt").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(todoId, instanceDate)
    }
}
