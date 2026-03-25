package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Files : Table("File") {
    val id = varchar("id", 30)
    val name = text("name")
    val url = text("url")
    val size = integer("size")
    val createdAt = datetime("createdAt")
    val userID = varchar("userID", 30).references(Users.id)
    val s3Key = text("s3Key")

    override val primaryKey = PrimaryKey(id)
}
