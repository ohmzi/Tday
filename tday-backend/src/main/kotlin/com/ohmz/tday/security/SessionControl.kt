package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

interface SessionControl {
    fun sessionMaxAgeSeconds(): Int
    fun revokeUserSessions(userId: String)
}

class SessionControlImpl(private val config: AppConfig) : SessionControl {
    override fun sessionMaxAgeSeconds(): Int = config.sessionMaxAgeSec

    override fun revokeUserSessions(userId: String) {
        if (userId.isBlank()) return
        transaction {
            Users.update({ Users.id eq userId }) {
                with(SqlExpressionBuilder) {
                    it[Users.tokenVersion] = Users.tokenVersion + 1
                }
            }
        }
    }
}
