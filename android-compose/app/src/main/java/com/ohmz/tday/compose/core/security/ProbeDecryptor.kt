package com.ohmz.tday.compose.core.security

import android.util.Base64
import com.ohmz.tday.compose.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class ProbeCompatibilityPayload(
    val appVersion: String,
    val updateRequired: Boolean,
)

object ProbeDecryptor {
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
    private val json = Json { ignoreUnknownKeys = true }

    fun decrypt(encryptedBase64Url: String): ProbeCompatibilityPayload? {
        val keyString = BuildConfig.PROBE_ENCRYPTION_KEY
        if (keyString.isBlank()) return null

        return runCatching {
            val keyBytes = base64UrlDecode(keyString)
            val blob = base64UrlDecode(encryptedBase64Url)
            require(blob.size > IV_LENGTH)

            val iv = blob.copyOfRange(0, IV_LENGTH)
            val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            val plaintext = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            json.decodeFromString<ProbeCompatibilityPayload>(plaintext)
        }.getOrNull()
    }

    private fun base64UrlDecode(input: String): ByteArray =
        Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
