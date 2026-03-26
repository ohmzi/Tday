package com.ohmz.tday.services

import java.util.concurrent.ConcurrentHashMap

data class CacheEntry<T>(val data: T, val expiresAt: Long)

interface CacheService {
    fun <T> get(key: String): T?
    fun <T> set(key: String, data: T, ttlMs: Long = 60_000L)
    fun invalidateForUser(userId: String)
    fun invalidateUserEndpoint(userId: String, endpoint: String)
    fun clear()
    fun cacheKey(userId: String, endpoint: String, params: Map<String, String>? = null): String
    fun invalidateTodoCaches(userId: String)
    fun invalidateListCaches(userId: String)
    fun invalidateCompletedCaches(userId: String)
}

class CacheServiceImpl : CacheService {
    private val defaultTtlMs = 60_000L
    private val cleanupIntervalMs = 5 * 60_000L
    private val store = ConcurrentHashMap<String, CacheEntry<Any?>>()
    private var lastCleanup = System.currentTimeMillis()

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? {
        lazyCleanup()
        val entry = store[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(key)
            return null
        }
        return entry.data as? T
    }

    override fun <T> set(key: String, data: T, ttlMs: Long) {
        store[key] = CacheEntry(data, System.currentTimeMillis() + ttlMs)
    }

    override fun invalidateForUser(userId: String) {
        val prefix = "$userId:"
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    override fun invalidateUserEndpoint(userId: String, endpoint: String) {
        val prefix = "$userId:$endpoint"
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    override fun clear() = store.clear()

    override fun cacheKey(userId: String, endpoint: String, params: Map<String, String>?): String {
        if (params.isNullOrEmpty()) return "$userId:$endpoint"
        val sorted = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$userId:$endpoint:$sorted"
    }

    override fun invalidateTodoCaches(userId: String) {
        invalidateUserEndpoint(userId, "todo")
        invalidateUserEndpoint(userId, "completedTodo")
    }

    override fun invalidateListCaches(userId: String) {
        invalidateUserEndpoint(userId, "list")
        invalidateUserEndpoint(userId, "todo")
    }

    override fun invalidateCompletedCaches(userId: String) {
        invalidateUserEndpoint(userId, "completedTodo")
    }

    private fun lazyCleanup() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup < cleanupIntervalMs) return
        lastCleanup = now
        store.entries.removeIf { now > it.value.expiresAt }
    }
}
