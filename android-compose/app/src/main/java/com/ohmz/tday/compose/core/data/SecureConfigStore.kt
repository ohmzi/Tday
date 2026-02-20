package com.ohmz.tday.compose.core.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SavedCredentials(
    val email: String,
    val password: String,
)

@Singleton
class SecureConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
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

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun saveServerUrl(rawUrl: String): Result<String> {
        val normalized = normalizeServerUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid server URL"))

        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        return Result.success(normalized)
    }

    fun getSavedCredentials(): SavedCredentials? {
        val email = prefs.getString(KEY_EMAIL, null).orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        if (email.isBlank() || password.isBlank()) return null
        return SavedCredentials(email = email, password = password)
    }

    fun saveCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    fun buildAbsoluteAppUrl(path: String): String? {
        val base = getServerUrl() ?: return null
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        return "$base$normalizedPath"
    }

    private fun normalizeServerUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        val pathless = parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()

        return pathless.removeSuffix("/")
    }

    private companion object {
        const val PREF_NAME = "tday_secure_config"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
    }
}
