package com.ohmz.tday.services

import java.util.concurrent.ConcurrentHashMap

data class CacheEntry<T>(val data: T, val expiresAt: Long)

interface CacheService {
    fun <T> get(key: String): T?
    fun <T> set(key: String, data: T, ttlMs: Long)
    fun invalidateForUser(userId: String)
    fun cacheKey(userId: String, endpoint: String, params: Map<String, String>? = null): String
}

class CacheServiceImpl : CacheService {
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

    override fun cacheKey(userId: String, endpoint: String, params: Map<String, String>?): String {
        if (params.isNullOrEmpty()) return "$userId:$endpoint"
        val sorted = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$userId:$endpoint:$sorted"
    }

    private fun lazyCleanup() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup < cleanupIntervalMs) return
        lastCleanup = now
        store.entries.removeIf { now > it.value.expiresAt }
    }
}
