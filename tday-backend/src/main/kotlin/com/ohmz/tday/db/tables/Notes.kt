package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Notes : Table("Note") {
    val id = varchar("id", 30)
    val name = text("name")
    val content = text("content").nullable()
    val createdAt = datetime("createdAt")
    val userID = varchar("userID", 30).references(Users.id).index()

    override val primaryKey = PrimaryKey(id)
}
