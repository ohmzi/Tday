package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserSecurityQuestions : Table("user_security_questions") {
    val id = varchar("id", 30)
    val userID = varchar("userID", 30).references(Users.id)
    val questionId = integer("question_id")
    val answerHash = text("answer_hash")
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)
}
