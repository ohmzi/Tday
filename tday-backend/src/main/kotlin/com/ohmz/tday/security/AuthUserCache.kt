package com.ohmz.tday.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

data class AuthCachedUser(
    val role: String,
    val approvalStatus: String,
    val tokenVersion: Int,
    val timeZone: String?,
    val requirePasswordChange: Boolean = false,
    val requireSecurityQuestions: Boolean = false,
)

class AuthUserCache(private val ttlMs: Long = 30_000L) {
    private val cache = ConcurrentHashMap<String, Pair<Long, AuthCachedUser>>()

    // One in-flight load per user, so a burst of parallel requests that all miss at once
    // (a mobile sync fires several GETs together) share a single query. The load runs in
    // its own scope so one caller being cancelled never fails the callers waiting on it.
    private val inFlight = ConcurrentHashMap<String, Deferred<AuthCachedUser?>>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // Later callers must not join a load that started before this invalidation.
        inFlight.remove(userId)
    }

    /**
     * Cache hit, else [load] the user once no matter how many callers miss concurrently,
     * [put] a non-null result, and return it. TTL, [put] and [invalidate] behave as before;
     * a load invalidated while running still answers its own waiters but does not repopulate
     * the cache (its row may predate the change that invalidated it).
     */
    suspend fun getOrLoad(userId: String, load: suspend () -> AuthCachedUser?): AuthCachedUser? {
        get(userId)?.let { return it }
        inFlight[userId]?.let { return it.await() }

        val candidate = loadScope.async(start = CoroutineStart.LAZY) {
            val loaded = load()
            if (loaded != null && inFlight[userId] === coroutineContext[Job]) put(userId, loaded)
            loaded
        }
        val existing = inFlight.putIfAbsent(userId, candidate)
        if (existing != null) {
            candidate.cancel()
            return existing.await()
        }
        candidate.invokeOnCompletion { inFlight.remove(userId, candidate) }
        candidate.start()
        return candidate.await()
    }
}
