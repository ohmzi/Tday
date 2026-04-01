package com.ohmz.tday.security

import com.ohmz.tday.db.tables.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

interface SessionControl {
    suspend fun revokeUserSessions(userId: String)
}

class SessionControlImpl(
    private val authUserCache: AuthUserCache,
) : SessionControl {
    override suspend fun revokeUserSessions(userId: String) {
        if (userId.isBlank()) return
        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq userId }) {
                with(SqlExpressionBuilder) {
                    it[Users.tokenVersion] = Users.tokenVersion + 1
                }
            }
        }
        authUserCache.invalidate(userId)
    }
}
