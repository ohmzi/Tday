package com.ohmz.tday.compose.feature.widget.snapshot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Which widget a snapshot belongs to, and the file it lives in under `filesDir/widget/`. */
internal enum class WidgetSnapshotKind(val fileName: String) {
    TODAY("widget-today-snapshot.json"),
    FLOATER("widget-floater-snapshot.json"),
}

/**
 * Matches [com.ohmz.tday.compose.core.network.NetworkModule.provideJson] exactly. The read side
 * (a widget's `provideGlance`) cannot reach Hilt — that is the whole point of this store — so it
 * owns a standalone copy of the same config instead of the injected instance.
 */
internal val WidgetSnapshotJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Hoisted, process-wide repaint signal — the widget-render analogue of
 * [com.ohmz.tday.compose.core.data.cache.OfflineCacheManager.cacheDataVersion], which the render
 * path can no longer collect without pulling in Hilt and the DB. Mirrors
 * [com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore]'s hoisted companion flow: handing
 * out a fresh `StateFlow` instance per read would restart `collectAsState`'s collector and
 * recompose in a loop, since it remembers keyed on the flow instance.
 */
internal object WidgetSnapshotSignal {
    private val versionMutable = MutableStateFlow(0L)
    val version: StateFlow<Long> = versionMutable.asStateFlow()
    fun bump() {
        versionMutable.value = versionMutable.value + 1L
    }
}

/**
 * Reads and writes the per-widget render snapshot as a Keystore-encrypted file under
 * `filesDir/widget/`, with no Hilt dependency — a widget's `provideGlance` constructs this
 * directly from `applicationContext`, so nothing on the render path calls `EntryPointAccessors`
 * or touches [com.ohmz.tday.compose.core.data.db.TdayDatabase].
 *
 * Not a plain file (this app's cache is encrypted at rest by policy) and not
 * `EncryptedSharedPreferences` (parses wholly into memory, no atomic multi-key swap). Not
 * `androidx.security.crypto.EncryptedFile` either: measured at ~765ms for its first cold read on a
 * Pixel 7 (Tink's keyset unwrap on top of the Keystore round trip), so this talks to
 * `AndroidKeyStore` directly instead — `AES/GCM/NoPadding`, no Tink layer, same
 * `setUserAuthenticationRequired(false)` key every other Keystore use in this app already relies
 * on to stay readable on a locked device. The 12-byte GCM IV is generated fresh per write and
 * prefixed onto the ciphertext; GCM's auth tag makes a truncated/tampered file fail to decrypt
 * rather than decrypt wrong, so [read] treats "missing" and "fails to decrypt" identically as
 * `null` — which the read path already has to handle for the fresh-install case anyway. [write]
 * deletes the target then writes fresh rather than write-to-tmp-then-rename for the same reason.
 */
internal class WidgetSnapshotStore(
    context: Context,
    private val json: Json = WidgetSnapshotJson,
) {
    private val appContext = context.applicationContext
    private val directory: File
        get() = File(appContext.filesDir, "widget").apply { mkdirs() }

    fun readToday(): WidgetSnapshot? = read(WidgetSnapshotKind.TODAY.fileName)

    fun readFloater(): WidgetSnapshot? = read(WidgetSnapshotKind.FLOATER.fileName)

    fun exists(kind: WidgetSnapshotKind): Boolean = fileFor(kind.fileName).exists()

    /** Returns true when the encrypted file was actually written. */
    fun write(kind: WidgetSnapshotKind, snapshot: WidgetSnapshot): Boolean = write(kind.fileName, snapshot)

    /**
     * Per-widget-instance variants for the list-scoped widget (widgets v3): unlike
     * [WidgetSnapshotKind], one file per `appWidgetId` rather than one shared by every instance
     * of a kind, since each placed instance can point at a different list. See
     * [com.ohmz.tday.compose.feature.widget.WidgetListSelectionStore] for what picks the
     * `appWidgetId` -> list mapping this reads and writes against.
     */
    fun readList(appWidgetId: Int): WidgetSnapshot? = read(listFileName(appWidgetId))

    fun writeList(appWidgetId: Int, snapshot: WidgetSnapshot): Boolean = write(listFileName(appWidgetId), snapshot)

    fun existsList(appWidgetId: Int): Boolean = fileFor(listFileName(appWidgetId)).exists()

    /** Called from the widget's `onDeleted`: an instance removed from the host never comes back. */
    fun deleteList(appWidgetId: Int) {
        fileFor(listFileName(appWidgetId)).delete()
    }

    private fun listFileName(appWidgetId: Int) = "widget-list-snapshot-$appWidgetId.json"

    private fun write(fileName: String, snapshot: WidgetSnapshot): Boolean {
        return runCatching {
            val bytes = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
                .toByteArray(Charsets.UTF_8)
            val target = fileFor(fileName)
            target.delete()
            target.writeBytes(encrypt(bytes))
            true
        }.getOrElse { false }
    }

    private fun read(fileName: String): WidgetSnapshot? {
        val target = fileFor(fileName)
        if (!target.exists()) return null
        val bytes = runCatching { decrypt(target.readBytes()) }.getOrNull()
        if (bytes == null) {
            // Undecryptable (a stale key, a truncated write). Delete it so `exists()` stays
            // truthful — callers use it as a cheap "do we already have something to show" check
            // without paying for a full decrypt.
            target.delete()
            return null
        }
        val snapshot = runCatching {
            json.decodeFromString(WidgetSnapshot.serializer(), bytes.toString(Charsets.UTF_8))
        }.getOrNull()
        if (snapshot == null) target.delete()
        return snapshot
    }

    private fun fileFor(fileName: String) = File(directory, fileName)

    private fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(bytes)
        return cipher.iv + ciphertext
    }

    private fun decrypt(bytes: ByteArray): ByteArray {
        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "tday_widget_snapshot_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
