package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Flat checklist steps inside a todo (R6-2). No nesting, no per-step dates —
 * ordering is the integer [position]. Steps cascade-delete with their parent
 * todo; a completed todo instead keeps a JSON snapshot in [CompletedTodos.steps].
 */
object TaskSteps : Table("task_steps") {
    val id = varchar("id", 30)
    // CASCADE is stated rather than left to Exposed's RESTRICT default, so the declaration
    // matches the constraint V20 created. This table is not in the
    // createMissingTablesAndColumns list in DatabaseConfig, so nothing reconciles it today and
    // no migration is needed; saying RESTRICT here is what would silently replace the live
    // CASCADE the moment somebody adds it to that list, which is how push_subscriptions broke.
    val todoID = varchar("todoID", 30).references(Todos.id, onDelete = ReferenceOption.CASCADE).index()
    val title = text("title")
    val completed = bool("completed").default(false)
    val position = integer("position").default(0)
    val createdAt = datetime("createdAt")

    override val primaryKey = PrimaryKey(id)
}
