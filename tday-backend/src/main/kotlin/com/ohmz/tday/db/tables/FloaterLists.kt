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
    // list from producing two recreated lists -- catching the loser with a
    // unique-constraint violation, which Exposed's default transaction retry
    // (3 attempts; see Transaction.maxAttempts, not overridden anywhere in
    // this backend) re-runs from the top, where it then finds the winner's
    // row and converges instead of erroring.
    //
    // H2 -- what every other backend test runs against, TestDatabase included
    // -- cannot express a filtered/partial index and silently skips creating
    // this one at boot, so no H2-backed test can exercise that guard actually
    // firing. CompletedFloaterConcurrencyTest covers it directly, against a
    // disposable real Postgres container.
    val recreatedFromListID = varchar("recreatedFromListID", 30).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("floaterproject_userid_recreatedfromlistid", true, userID, recreatedFromListID) {
            recreatedFromListID.isNotNull()
        }
    }
}
