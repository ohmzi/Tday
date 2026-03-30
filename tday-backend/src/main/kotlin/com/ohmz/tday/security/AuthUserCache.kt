package com.ohmz.tday.security

import java.util.concurrent.ConcurrentHashMap

data class AuthCachedUser(
    val role: String,
    val approvalStatus: String,
    val tokenVersion: Int,
    val timeZone: String?,
)

class AuthUserCache(private val ttlMs: Long = 30_000L) {
    private val cache = ConcurrentHashMap<String, Pair<Long, AuthCachedUser>>()

    fun get(userId: String): AuthCachedUser? {
        val entry = cache[userId] ?: return null
        val (timestamp, user) = entry
        if (System.currentTimeMillis() - timestamp > ttlMs) {
            cache.remove(userId)
            return null
        }
        return user
    }

    fun put(userId: String, user: AuthCachedUser) {
        cache[userId] = System.currentTimeMillis() to user
    }

    fun invalidate(userId: String) {
        cache.remove(userId)
    }
}
