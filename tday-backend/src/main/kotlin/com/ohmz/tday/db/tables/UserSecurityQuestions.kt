package com.ohmz.tday.db.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserSecurityQuestions : Table("user_security_questions") {
    val id = varchar("id", 30)
    // CASCADE is stated rather than left to Exposed's RESTRICT default, so the declaration
    // matches the constraint V12 created. This table is not in the
    // createMissingTablesAndColumns list in DatabaseConfig, so nothing reconciles it today and
    // no migration is needed; saying RESTRICT here is what would silently replace the live
    // CASCADE the moment somebody adds it to that list, which is how push_subscriptions broke.
    val userID = varchar("userID", 30).references(Users.id, onDelete = ReferenceOption.CASCADE)
    val questionId = integer("question_id")
    val answerHash = text("answer_hash")
    val createdAt = datetime("createdAt")
    val updatedAt = datetime("updatedAt")

    override val primaryKey = PrimaryKey(id)
}
