package com.ohmz.tday.compose.core.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ohmz.tday.compose.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    @Volatile
    private var runtimeServerUrl: String? = null

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasServerUrl(): Boolean = !getServerUrl().isNullOrBlank()

    fun getAppDataMode(): AppDataMode {
        val persisted = prefs.getString(KEY_APP_DATA_MODE, null)
            ?.let { raw ->
                runCatching { AppDataMode.valueOf(raw) }.getOrNull()
            }
        if (persisted != null) return persisted
        return if (!getServerUrl().isNullOrBlank()) AppDataMode.SERVER else AppDataMode.UNSET
    }

    fun isLocalMode(): Boolean = getAppDataMode() == AppDataMode.LOCAL

    fun setAppDataMode(mode: AppDataMode) {
        prefs.edit().putString(KEY_APP_DATA_MODE, mode.name).apply()
    }

    fun clearAppDataMode() {
        prefs.edit().remove(KEY_APP_DATA_MODE).apply()
    }

    fun getServerUrl(): String? {
        val inMemory = runtimeServerUrl?.takeIf { it.isNotBlank() }
        if (inMemory != null) return inMemory

        val persisted = prefs.getString(KEY_SERVER_URL, null)
            ?.takeIf { it.isNotBlank() }
        if (persisted != null) {
            runtimeServerUrl = persisted
        }
        return persisted
    }

    fun saveServerUrl(
        rawUrl: String,
        persist: Boolean = false,
    ): Result<String> {
        val normalized = normalizeServerUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid server URL"))

        runtimeServerUrl = normalized
        if (persist) {
            prefs.edit()
                .putString(KEY_SERVER_URL, normalized)
                .putString(KEY_APP_DATA_MODE, AppDataMode.SERVER.name)
                .apply()
        } else {
            prefs.edit().remove(KEY_SERVER_URL).apply()
        }
        return Result.success(normalized)
    }

    fun persistRuntimeServerUrl(): Result<String> {
        val current = getServerUrl()
            ?: return Result.failure(IllegalStateException("Server URL is not configured"))

        runtimeServerUrl = current
        prefs.edit()
            .putString(KEY_SERVER_URL, current)
            .putString(KEY_APP_DATA_MODE, AppDataMode.SERVER.name)
            .apply()
        return Result.success(current)
    }

    fun clearServerUrl() {
        runtimeServerUrl = null
        prefs.edit().remove(KEY_SERVER_URL).apply()
    }

    fun clearAllLocalData() {
        runtimeServerUrl = null
        prefs.edit().clear().apply()
    }

    fun normalizeServerUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val parsed = when {
            trimmed.startsWith("https://", ignoreCase = true) -> {
                trimmed.toHttpUrlOrNull() ?: return null
            }
            trimmed.startsWith("http://", ignoreCase = true) -> {
                val httpCandidate = trimmed.toHttpUrlOrNull() ?: return null
                if (canUseLocalHttp(httpCandidate.host)) {
                    httpCandidate
                } else {
                    return null
                }
            }
            else -> {
                val httpsCandidate = "https://$trimmed".toHttpUrlOrNull() ?: return null
                if (canUseLocalHttp(httpsCandidate.host)) {
                    "http://$trimmed".toHttpUrlOrNull() ?: return null
                } else {
                    httpsCandidate
                }
            }
        }

        val pathless = parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()

        return pathless.removeSuffix("/")
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun serverTrustKeyForUrl(rawUrl: String): String? {
        val normalized = normalizeServerUrl(rawUrl) ?: return null
        val parsed = normalized.toHttpUrlOrNull() ?: return null
        val usesDefaultPort = (parsed.scheme == "https" && parsed.port == 443) ||
            (parsed.scheme == "http" && parsed.port == 80)
        val authority = if (usesDefaultPort) parsed.host else "${parsed.host}:${parsed.port}"
        return "${parsed.scheme}://$authority"
    }

    fun getTrustedServerFingerprint(serverTrustKey: String): String? {
        return prefs.getString(fingerprintPrefKey(serverTrustKey), null)
    }

    fun saveTrustedServerFingerprint(
        serverTrustKey: String,
        fingerprint: String,
    ) {
        prefs.edit()
            .putString(fingerprintPrefKey(serverTrustKey), fingerprint)
            .apply()
    }

    fun clearTrustedServerFingerprintForUrl(rawUrl: String): Result<Unit> {
        val serverTrustKey = serverTrustKeyForUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid server URL"))

        prefs.edit()
            .remove(fingerprintPrefKey(serverTrustKey))
            .apply()
        return Result.success(Unit)
    }

    fun getLastUsername(): String? =
        prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }

    fun saveLastUsername(username: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .remove("password")
            .apply()
    }

    fun clearLastUsername() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove("password")
            .apply()
    }

    fun getCachedSessionUserRaw(): String? =
        prefs.getString(KEY_CACHED_SESSION_USER, null)?.takeIf { it.isNotBlank() }

    fun saveCachedSessionUserRaw(raw: String) {
        prefs.edit().putString(KEY_CACHED_SESSION_USER, raw).apply()
    }

    fun clearCachedSessionUser() {
        prefs.edit().remove(KEY_CACHED_SESSION_USER).apply()
    }

    fun clearListIconCache() {
        prefs.edit().remove(KEY_LIST_ICON_MAP).apply()
    }

    fun getListIcon(listId: String): String? {
        if (listId.isBlank()) return null
        val raw = prefs.getString(KEY_LIST_ICON_MAP, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            JSONObject(raw).optString(listId).ifBlank { null }
        }.getOrNull()
    }

    fun saveListIcon(listId: String, iconKey: String) {
        if (listId.isBlank() || iconKey.isBlank()) return

        val current = runCatching {
            JSONObject(prefs.getString(KEY_LIST_ICON_MAP, null).orEmpty())
        }.getOrElse { JSONObject() }

        current.put(listId, iconKey)
        prefs.edit().putString(KEY_LIST_ICON_MAP, current.toString()).apply()
    }

    fun buildAbsoluteAppUrl(path: String): String? {
        val base = getServerUrl() ?: return null
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        return "$base$normalizedPath"
    }

    fun getOfflineSyncStateRaw(): String? {
        return prefs.getString(KEY_OFFLINE_SYNC_STATE, null)
    }

    fun saveOfflineSyncStateRaw(raw: String) {
        prefs.edit().putString(KEY_OFFLINE_SYNC_STATE, raw).apply()
    }

    fun clearOfflineSyncState() {
        prefs.edit().remove(KEY_OFFLINE_SYNC_STATE).apply()
    }

    private fun fingerprintPrefKey(serverTrustKey: String): String {
        return "$KEY_CERT_FINGERPRINT_PREFIX$serverTrustKey"
    }

    private fun isLocalDevelopmentHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        if (normalizedHost == "localhost") return true
        if (normalizedHost == "10.0.2.2") return true
        if (normalizedHost.endsWith(".local")) return true
        if (normalizedHost.matches(Regex("^127\\.\\d+\\.\\d+\\.\\d+$"))) return true
        if (normalizedHost.matches(Regex("^10\\.\\d+\\.\\d+\\.\\d+$"))) return true
        if (normalizedHost.matches(Regex("^192\\.168\\.\\d+\\.\\d+$"))) return true
        return normalizedHost.matches(Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+$"))
    }

    private fun canUseLocalHttp(host: String): Boolean {
        return BuildConfig.DEBUG && isLocalDevelopmentHost(host)
    }

    private companion object {
        const val PREF_NAME = "tday_secure_config"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_CERT_FINGERPRINT_PREFIX = "cert_fp_"
        const val KEY_LIST_ICON_MAP = "list_icon_map"
        const val KEY_OFFLINE_SYNC_STATE = "offline_sync_state_v1"
        const val KEY_CACHED_SESSION_USER = "cached_session_user_v1"
        const val KEY_APP_DATA_MODE = "app_data_mode_v1"
    }
}
