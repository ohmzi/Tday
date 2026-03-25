package com.ohmz.tday.services

import com.ohmz.tday.db.tables.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

fun UserService.updatePasswordHash(userId: String, newHash: String) {
    transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.password] = newHash
            it[Users.updatedAt] = LocalDateTime.now()
        }
    }
}
