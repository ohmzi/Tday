package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * EDITOR/VIEWER membership on a floater list. The owner is implicit on
 * [FloaterLists.userID] and never has a row here.
 */
object FloaterListShares : Table("floater_list_shares") {
    val id = varchar("id", 30)
    val listID = varchar("listID", 30).references(FloaterLists.id)
    val userID = varchar("userID", 30).references(Users.id).index()
    val role = varchar("role", 16)
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(listID, userID)
    }
}
