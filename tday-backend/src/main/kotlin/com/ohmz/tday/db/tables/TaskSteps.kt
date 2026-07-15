package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Flat checklist steps inside a todo (R6-2). No nesting, no per-step dates —
 * ordering is the integer [position]. Steps cascade-delete with their parent
 * todo; a completed todo instead keeps a JSON snapshot in [CompletedTodos.steps].
 */
object TaskSteps : Table("task_steps") {
    val id = varchar("id", 30)
    val todoID = varchar("todoID", 30).references(Todos.id).index()
    val title = text("title")
    val completed = bool("completed").default(false)
    val position = integer("position").default(0)
    val createdAt = datetime("createdAt")

    override val primaryKey = PrimaryKey(id)
}
