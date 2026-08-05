package com.ohmz.tday.compose.core.data.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Holds the SQLCipher passphrase for the offline cache.
 *
 * The passphrase is 32 random bytes generated once on first launch and kept in
 * [EncryptedSharedPreferences], so the value on disk is wrapped by an AES key that lives in the
 * Android Keystore (hardware-backed where the device provides it) and never leaves it. A file
 * dump — rooted device, `adb backup`, extracted `/data` image — therefore yields neither the
 * database contents nor the key that opens them.
 *
 * The Keystore key deliberately does **not** set `setUserAuthenticationRequired(true)`:
 * the home-screen widget renders while the device is locked, and a user-authentication-bound key
 * is unusable in that state, which would leave the widget permanently blank. The tradeoff is that
 * an attacker who obtains an *unlocked, running* device can drive the app and read the data
 * anyway. What this protects is data at rest: the powered-off or locked-and-dumped device, which
 * is the threat the owner named. The optional app lock is a separate, independent control and is
 * deliberately not wired to this key for the same widget reason.
 */
class DatabasePassphraseStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * The passphrase for the cache, creating it on first call.
     *
     * Returns a fresh array every time because SQLCipher zeroes the array it is handed; callers
     * must never share one instance between the migration and the open helper.
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val existing = preferences.getString(KEY_PASSPHRASE, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing.toByteArray(Charsets.UTF_8)

        val generated = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val encoded = encodePassphrase(generated)
        generated.fill(0)

        // commit(), not apply(): if the process dies between here and opening the database, an
        // unpersisted key would leave an encrypted file nothing can ever open again.
        preferences.edit().putString(KEY_PASSPHRASE, encoded).commit()
        return encoded.toByteArray(Charsets.UTF_8)
    }

    private companion object {
        const val PREF_NAME = "tday_database_key"
        const val KEY_PASSPHRASE = "offline_cache_passphrase_v1"
        const val PASSPHRASE_BYTES = 32
    }
}

/**
 * Renders raw key bytes as lowercase hex.
 *
 * SQLCipher takes the passphrase as a NUL-terminated C string, so raw random bytes would be
 * silently truncated at the first zero byte — roughly a 1-in-8 chance per 32-byte key of quietly
 * shortening it. Hex keeps the full 256 bits of entropy and, because the alphabet is `[0-9a-f]`,
 * makes the value safe to embed in the one SQL statement that cannot take a bound parameter (see
 * LegacyPlaintextCacheMigration).
 */
internal fun encodePassphrase(keyBytes: ByteArray): String =
    keyBytes.joinToString("") { "%02x".format(it) }

/** True when [passphrase] is non-empty lowercase hex, i.e. produced by [encodePassphrase]. */
internal fun isHexPassphrase(passphrase: String): Boolean =
    passphrase.isNotEmpty() && passphrase.all { it in '0'..'9' || it in 'a'..'f' }
