package com.ohmz.tday.compose.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent [CookieStore] backed by [EncryptedSharedPreferences] (AES-256-GCM).
 * Cookies survive process death so the NextAuth JWT session cookie is retained
 * across app launches. Expired cookies are automatically pruned on every read.
 */
@Singleton
class EncryptedCookieStore @Inject constructor(
    @ApplicationContext context: Context,
) : CookieStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val lock = Any()

    override fun add(uri: URI?, cookie: HttpCookie?) {
        if (cookie == null) return
        synchronized(lock) {
            val key = cookieKey(uri, cookie)
            prefs.edit().putString(key, serializeCookie(uri, cookie)).apply()
        }
    }

    override fun get(uri: URI?): List<HttpCookie> {
        if (uri == null) return emptyList()
        synchronized(lock) {
            pruneExpired()
            return allEntries()
                .filter { matchesUri(it.uri, uri) }
                .map { it.cookie }
        }
    }

    override fun getCookies(): List<HttpCookie> {
        synchronized(lock) {
            pruneExpired()
            return allEntries().map { it.cookie }
        }
    }

    override fun getURIs(): List<URI> {
        synchronized(lock) {
            pruneExpired()
            return allEntries().mapNotNull { it.uri }.distinct()
        }
    }

    override fun remove(uri: URI?, cookie: HttpCookie?): Boolean {
        if (cookie == null) return false
        synchronized(lock) {
            val key = cookieKey(uri, cookie)
            if (!prefs.contains(key)) return false
            prefs.edit().remove(key).apply()
            return true
        }
    }

    override fun removeAll(): Boolean {
        synchronized(lock) {
            val keys = prefs.all.keys.filter { it.startsWith(COOKIE_PREFIX) }
            if (keys.isEmpty()) return false
            val editor = prefs.edit()
            keys.forEach { editor.remove(it) }
            editor.apply()
            return true
        }
    }

    private fun allEntries(): List<CookieEntry> =
        prefs.all.entries
            .filter { it.key.startsWith(COOKIE_PREFIX) }
            .mapNotNull { deserializeCookie(it.value as? String ?: return@mapNotNull null) }

    private fun pruneExpired() {
        val keysToRemove = prefs.all.entries
            .filter { it.key.startsWith(COOKIE_PREFIX) }
            .filter { entry ->
                val parsed = deserializeCookie(entry.value as? String ?: return@filter true)
                parsed == null || parsed.cookie.hasExpired()
            }
            .map { it.key }

        if (keysToRemove.isNotEmpty()) {
            val editor = prefs.edit()
            keysToRemove.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    private fun cookieKey(uri: URI?, cookie: HttpCookie): String {
        val domain = (cookie.domain ?: uri?.host ?: "unknown").lowercase()
        val path = cookie.path ?: "/"
        val name = cookie.name ?: ""
        return "$COOKIE_PREFIX${domain}\u001D${path}\u001D$name"
    }

    private fun serializeCookie(uri: URI?, cookie: HttpCookie): String {
        val now = System.currentTimeMillis() / 1000
        val expiresAt = if (cookie.maxAge >= 0) now + cookie.maxAge else -1L
        return listOf(
            cookie.name.orEmpty(),
            cookie.value.orEmpty(),
            cookie.domain.orEmpty(),
            cookie.path ?: "/",
            expiresAt.toString(),
            cookie.secure.toString(),
            cookie.isHttpOnly.toString(),
            uri?.toString().orEmpty(),
        ).joinToString(FIELD_SEP)
    }

    private data class CookieEntry(val cookie: HttpCookie, val uri: URI?)

    private fun deserializeCookie(raw: String): CookieEntry? {
        val parts = raw.split(FIELD_SEP)
        if (parts.size < 7) return null

        val expiresAt = parts[4].toLongOrNull() ?: -1L
        val cookie = HttpCookie(parts[0], parts[1]).apply {
            domain = parts[2].ifBlank { null }
            path = parts[3]
            secure = parts[5].toBooleanStrictOrNull() ?: false
            isHttpOnly = parts[6].toBooleanStrictOrNull() ?: false
            maxAge = if (expiresAt >= 0) {
                val remaining = expiresAt - System.currentTimeMillis() / 1000
                if (remaining > 0) remaining else 0
            } else {
                -1
            }
        }

        val uri = parts.getOrNull(7)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(it) }.getOrNull() }

        return CookieEntry(cookie, uri)
    }

    private fun matchesUri(cookieUri: URI?, requestUri: URI): Boolean {
        if (cookieUri == null) return true
        return cookieUri.host.equals(requestUri.host, ignoreCase = true)
    }

    private companion object {
        const val PREFS_NAME = "tday_cookie_store"
        const val COOKIE_PREFIX = "ck_"
        const val FIELD_SEP = "\u001F"
    }
}
