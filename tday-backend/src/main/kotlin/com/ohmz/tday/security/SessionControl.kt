package com.ohmz.tday.security

import com.ohmz.tday.db.tables.Users
import com.ohmz.tday.services.UserApiKeyService
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

interface SessionControl {
    /**
     * Invalidates all of the user's sessions by bumping their tokenVersion.
     *
     * API keys carry no tokenVersion, so they are NOT affected by a session bump.
     * Credential-rotation events (password change, admin reset) must pass
     * [revokeApiKeys] = true so the full-account API credential is invalidated
     * alongside the password; a plain logout leaves the key intact.
     */
    suspend fun revokeUserSessions(userId: String, revokeApiKeys: Boolean = false)
}

class SessionControlImpl(
    private val authUserCache: AuthUserCache,
    private val userApiKeyService: UserApiKeyService,
) : SessionControl {
    override suspend fun revokeUserSessions(userId: String, revokeApiKeys: Boolean) {
        if (userId.isBlank()) return
        newSuspendedTransaction(Dispatchers.IO) {
            Users.update({ Users.id eq userId }) {
                with(SqlExpressionBuilder) {
                    it[Users.tokenVersion] = Users.tokenVersion + 1
                }
            }
        }
        if (revokeApiKeys) {
            userApiKeyService.revoke(userId)
        }
        authUserCache.invalidate(userId)
    }
}
