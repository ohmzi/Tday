package com.ohmz.tday.db.tables

import com.ohmz.tday.db.enums.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object FloaterLists : Table("FloaterProject") {
    val id = varchar("id", 30)
    val name = text("name")
    val color = pgEnum<ListColor>("color", "\"ProjectColor\"").nullable()
    val iconKey = varchar("iconKey", 64).nullable()
    val userID = varchar("userID", 30).references(Users.id).index()
    val reusable = bool("reusable").default(false)
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    // Set only when this list was recreated by FloaterService.uncompleteFloater()
    // after its original was deleted -- names the original (now-gone) list's
    // id. Not a foreign key: the row it names no longer exists by the time
    // this is ever set. The partial unique index below is the find-or-create
    // guard: it is what actually stops two undos racing on the same deleted
    // list from producing two recreated lists.
    val recreatedFromListID = varchar("recreatedFromListID", 30).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("floaterproject_userid_recreatedfromlistid", true, userID, recreatedFromListID) {
            recreatedFromListID.isNotNull()
        }
    }
}
