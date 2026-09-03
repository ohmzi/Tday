package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.ReferenceOption
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

    // SET NULL, not RESTRICT (Exposed's default for an unspecified onDelete):
    // a deleted list must not take its completion history down with it, but
    // the row this points at is gone, so the reference itself has to go too.
    // See db/migration/V27 for the paired live-constraint change and
    // originalListID below, which is what survives the detach.
    val listID = varchar("projectID", 30)
        .references(FloaterLists.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
        .index()
    val listName = varchar("projectName", 255).nullable()
    val listColor = varchar("projectColor", 32).nullable()

    // Unconstrained on purpose: once the source list is deleted, listID above
    // is nulled by the FK action above, but this copy survives untouched. It
    // is what lets FloaterService.uncompleteFloater() find-or-create the
    // recreated list and converge a second undo from the same deleted list
    // onto the one already recreated, instead of duplicating it -- see
    // FloaterLists.recreatedFromListID.
    val originalListID = varchar("originalListID", 30).nullable()

    override val primaryKey = PrimaryKey(id)
}
