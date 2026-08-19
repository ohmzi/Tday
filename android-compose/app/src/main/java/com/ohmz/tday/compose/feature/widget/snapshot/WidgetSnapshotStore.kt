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
 * Reads and writes the per-widget render snapshot as a Keystore-encrypted file, deliberately with
 * no Hilt dependency — a widget's `provideGlance` constructs this directly from
 * `applicationContext` exactly the way it already constructs
 * [com.ohmz.tday.compose.core.data.AppSecurityPreferenceStore]. That is the structural fix for the
 * 20s post-reboot blank window: nothing on the render path may call `EntryPointAccessors`, because
 * that is what drags in [com.ohmz.tday.compose.core.data.db.TdayDatabase] and its ~9.5s cold
 * SQLCipher open.
 *
 * **Storage choice.** Not a plain file: `docs/security/SECURITY_POSTURE.md` documents the Android
 * cache as encrypted at rest, and `LegacyPlaintextCacheMigration` exists specifically to remove a
 * plaintext task-title file from disk — shipping a new one here would be a posture regression, not
 * a tradeoff. Not `EncryptedSharedPreferences`: two widgets' worth of up to 50 rows is tens of KB
 * of XML value that SharedPreferences parses wholly into memory on first access, and offers no
 * atomic multi-key swap; that shape is exactly what `OfflineCacheManager`'s Room migration replaced.
 *
 * **Not `androidx.security.crypto.EncryptedFile` either — measured, not assumed.** The plan this
 * store was built from called for exactly this check: instrument the cold read and swap to a
 * hand-rolled Keystore cipher if it came in over 150ms. On a post-reboot Pixel 7 the first
 * `EncryptedFile.openFileInput()` this process ever makes costs ~765ms — Tink's keyset unwrap on
 * top of the Keystore round trip, not the Keystore round trip itself — against a ~1s total budget
 * from process start to painted content. So this talks to `AndroidKeyStore` directly: one
 * `KeyStore.getInstance("AndroidKeyStore")` + `getKey`/`generateKey`, then `Cipher` in AES/GCM
 * directly over the file bytes, no Tink keyset layer. Same key alias, `setUserAuthenticationRequired(false)`
 * like every other Keystore key this app already uses, so it stays readable on a locked device —
 * the same property `EncryptedFile` was chosen for in the first place, just without the extra
 * unwrap. The 12-byte GCM IV is generated fresh per write and stored as a prefix on the ciphertext;
 * GCM's authentication tag makes a truncated or tampered file fail to decrypt rather than decrypt
 * to a wrong-but-plausible result, so [read] can still treat "fails to decrypt" and "missing" as
 * the same `null` outcome.
 *
 * **Write safety without atomic rename.** Same reasoning as the `EncryptedFile` version this
 * replaced: this deletes the target then writes fresh rather than write-to-tmp-then-`renameTo`.
 * The two failure windows that leaves — a reader arriving after the delete but before the write
 * completes, or a process death mid-write leaving a truncated ciphertext — are both already
 * first-class outcomes here: [read] treats "missing" and "fails to decrypt" identically, as `null`,
 * and the read path (LOADING + [WidgetHydrateWorker]) already has to handle "no snapshot yet" for
 * the fresh-install case.
 */
internal class WidgetSnapshotStore(
    context: Context,
    private val json: Json = WidgetSnapshotJson,
) {
    private val appContext = context.applicationContext
    private val directory: File
        get() = File(appContext.filesDir, "widget").apply { mkdirs() }

    fun readToday(): WidgetSnapshot? = read(WidgetSnapshotKind.TODAY)

    fun readFloater(): WidgetSnapshot? = read(WidgetSnapshotKind.FLOATER)

    fun exists(kind: WidgetSnapshotKind): Boolean = fileFor(kind).exists()

    /** Returns true when the encrypted file was actually written. */
    fun write(kind: WidgetSnapshotKind, snapshot: WidgetSnapshot): Boolean {
        return runCatching {
            val bytes = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
                .toByteArray(Charsets.UTF_8)
            val target = fileFor(kind)
            target.delete()
            target.writeBytes(encrypt(bytes))
            true
        }.getOrElse { false }
    }

    private fun read(kind: WidgetSnapshotKind): WidgetSnapshot? {
        val target = fileFor(kind)
        if (!target.exists()) return null
        val bytes = WidgetTiming.time("WidgetSnapshotStore.read(${kind.name}) decrypt") {
            runCatching { decrypt(target.readBytes()) }.getOrNull()
        } ?: return null
        val snapshot = WidgetTiming.time("WidgetSnapshotStore.read(${kind.name}) json decode") {
            runCatching {
                json.decodeFromString(WidgetSnapshot.serializer(), bytes.toString(Charsets.UTF_8))
            }.getOrNull()
        } ?: return null
        // A downgrade after a newer build wrote a file with fields this reader doesn't know how
        // to interpret. Treat it as missing rather than silently rendering a half-decoded row.
        return snapshot.takeIf { it.isSupported() }
    }

    private fun fileFor(kind: WidgetSnapshotKind) = File(directory, kind.fileName)

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
