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
     * Long-lived credentials carry no tokenVersion, so they are NOT affected by a session bump.
     * Credential-rotation events (password change, admin reset, security-question recovery) must
     * pass [revokeApiKeys] = true, which revokes *every* standing credential — the full-account
     * API key and the no-auth calendar feed URL — alongside the password. A plain logout leaves
     * both intact.
     */
    suspend fun revokeUserSessions(userId: String, revokeApiKeys: Boolean = false)
}

class SessionControlImpl(
    private val authUserCache: AuthUserCache,
    private val userApiKeyService: UserApiKeyService,
    private val calendarFeedService: CalendarFeedRevoker,
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
            // The ICS feed URL authenticates on its own and survived password changes, so a
            // leaked feed kept exposing every task title after the owner thought they had cut
            // access off. There is no rotate-in-place: the user re-adds the feed afterwards.
            calendarFeedService.revoke(userId)
        }
        authUserCache.invalidate(userId)
    }
}

/**
 * The slice of CalendarFeedService that SessionControl needs. Narrowing it here keeps the
 * dependency one-directional and makes SessionControlImpl constructible in tests.
 */
fun interface CalendarFeedRevoker {
    suspend fun revoke(userId: String)
}
