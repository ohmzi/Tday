package com.ohmz.tday.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

object Files : Table("File") {
    val id = varchar("id", 30)
    val name = text("name")
    val url = text("url")
    val size = integer("size")
    val createdAt = datetime("createdAt")
    val userID = varchar("userID", 30).references(Users.id).index()
    val s3Key = text("s3Key")

    override val primaryKey = PrimaryKey(id)
}
