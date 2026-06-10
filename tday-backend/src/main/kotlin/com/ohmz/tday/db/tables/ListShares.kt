package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * EDITOR/VIEWER membership on a scheduled list. The owner is implicit on
 * [Lists.userID] and never has a row here.
 *
 * Deliberately no FK columns: on Prisma-era databases the parent tables can
 * live outside the schema migrations run against, so referential cleanup is
 * owned by the services (ListService.deleteMany, ListShareService,
 * AdminService.purgeUser).
 */
object ListShares : Table("list_shares") {
    val id = varchar("id", 30)
    val listID = varchar("listID", 30)
    val userID = varchar("userID", 30).index()
    val role = varchar("role", 16)
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(listID, userID)
    }
}
