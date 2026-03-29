package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import com.ohmz.tday.db.tables.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

interface SessionControl {
    fun sessionMaxAgeSeconds(): Int
    suspend fun revokeUserSessions(userId: String)
}

class SessionControlImpl(private val config: AppConfig) : SessionControl {
    override fun sessionMaxAgeSeconds(): Int = config.sessionMaxAgeSec

    override suspend fun revokeUserSessions(userId: String) {
        if (userId.isBlank()) return
        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq userId }) {
                with(SqlExpressionBuilder) {
                    it[Users.tokenVersion] = Users.tokenVersion + 1
                }
            }
        }
    }
}
