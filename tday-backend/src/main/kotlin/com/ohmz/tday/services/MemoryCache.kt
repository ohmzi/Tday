package com.ohmz.tday.services

import java.util.concurrent.ConcurrentHashMap

data class CacheEntry<T>(val data: T, val expiresAt: Long)

object MemoryCache {
    private const val DEFAULT_TTL_MS = 60_000L
    private const val CLEANUP_INTERVAL_MS = 5 * 60_000L
    private val store = ConcurrentHashMap<String, CacheEntry<Any?>>()
    private var lastCleanup = System.currentTimeMillis()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        lazyCleanup()
        val entry = store[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(key)
            return null
        }
        return entry.data as? T
    }

    fun <T> set(key: String, data: T, ttlMs: Long = DEFAULT_TTL_MS) {
        store[key] = CacheEntry(data, System.currentTimeMillis() + ttlMs)
    }

    fun invalidateForUser(userId: String) {
        val prefix = "$userId:"
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    fun invalidateUserEndpoint(userId: String, endpoint: String) {
        val prefix = "$userId:$endpoint"
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    fun clear() = store.clear()

    fun cacheKey(userId: String, endpoint: String, params: Map<String, String>? = null): String {
        if (params.isNullOrEmpty()) return "$userId:$endpoint"
        val sorted = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$userId:$endpoint:$sorted"
    }

    fun invalidateTodoCaches(userId: String) {
        invalidateUserEndpoint(userId, "todo")
        invalidateUserEndpoint(userId, "completedTodo")
    }

    fun invalidateListCaches(userId: String) {
        invalidateUserEndpoint(userId, "list")
        invalidateUserEndpoint(userId, "todo")
    }

    fun invalidateCompletedCaches(userId: String) {
        invalidateUserEndpoint(userId, "completedTodo")
    }

    private fun lazyCleanup() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return
        lastCleanup = now
        store.entries.removeIf { now > it.value.expiresAt }
    }
}
