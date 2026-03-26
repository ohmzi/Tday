package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface FieldEncryption {
    fun isConfigured(): Boolean
    fun encrypt(plaintext: String): String
    fun decrypt(raw: String): String
    fun isSensitiveField(fieldName: String): Boolean
    fun isEncrypted(value: String): Boolean
    fun encryptIfSensitive(fieldName: String, value: String?): String?
    fun decryptIfEncrypted(value: String?): String?
}

class FieldEncryptionImpl(private val config: AppConfig) : FieldEncryption {
    private val encPrefix = "enc:v1"
    private val ivLengthBytes = 12
    private val keyLengthBytes = 32
    private val tagLengthBits = 128
    private val sensitiveFields = setOf("description", "content", "overriddenDescription")
    private val random = SecureRandom()

    private val keyring: Map<String, ByteArray> by lazy { parseKeyring() }
    private val activeKeyId: String? by lazy {
        val explicitId = config.dataEncryptionKeyId
        if (keyring.containsKey(explicitId)) explicitId
        else keyring.keys.firstOrNull()
    }

    override fun isConfigured(): Boolean = activeKeyId != null

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty() || isEncrypted(plaintext)) return plaintext
        val keyId = activeKeyId ?: return plaintext
        val keyBytes = keyring[keyId] ?: return plaintext

        val iv = ByteArray(ivLengthBytes).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(tagLengthBits, iv))

        val aad = config.dataEncryptionAad
        if (!aad.isNullOrBlank()) {
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        }

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "$encPrefix:$keyId:${iv.toBase64Url()}:${ciphertext.toBase64Url()}"
    }

    override fun decrypt(raw: String): String {
        if (raw.isEmpty() || !isEncrypted(raw)) return raw
        val parts = raw.split(":")
        if (parts.size != 5) throw IllegalArgumentException("Malformed encrypted payload")
        val (prefix, version, keyId, ivRaw, cipherRaw) = parts
        if (prefix != "enc" || version != "v1") throw IllegalArgumentException("Unsupported encrypted payload")

        val keyBytes = keyring[keyId]
            ?: throw IllegalStateException("Missing field encryption key for key id \"$keyId\"")

        val iv = ivRaw.fromBase64Url()
        val ciphertext = cipherRaw.fromBase64Url()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagLengthBits, iv))

        val aad = config.dataEncryptionAad
        if (!aad.isNullOrBlank()) {
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        }

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    override fun isSensitiveField(fieldName: String): Boolean = fieldName in sensitiveFields

    override fun isEncrypted(value: String): Boolean = value.startsWith("$encPrefix:")

    override fun encryptIfSensitive(fieldName: String, value: String?): String? {
        if (value == null || !isConfigured() || !isSensitiveField(fieldName)) return value
        return encrypt(value)
    }

    override fun decryptIfEncrypted(value: String?): String? {
        if (value == null) return null
        if (!isEncrypted(value)) return value
        return decrypt(value)
    }

    private fun parseKeyring(): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()

        val keySetRaw = config.dataEncryptionKeys
        if (!keySetRaw.isNullOrBlank()) {
            for (entry in keySetRaw.split(",")) {
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) continue
                val sep = trimmed.indexOf(':')
                if (sep <= 0 || sep >= trimmed.length - 1) continue
                val kid = trimmed.substring(0, sep).trim()
                val keyRaw = trimmed.substring(sep + 1).trim()
                if (kid.isNotEmpty()) {
                    map[kid] = parseKeyMaterial(keyRaw)
                }
            }
        }

        val explicitKey = config.dataEncryptionKey
        if (!explicitKey.isNullOrBlank()) {
            map[config.dataEncryptionKeyId] = parseKeyMaterial(explicitKey)
        }

        return map
    }

    private fun parseKeyMaterial(raw: String): ByteArray {
        val normalized = raw.trim()
        if (normalized.isEmpty()) throw IllegalArgumentException("Field encryption key cannot be empty")

        val asBase64 = try {
            Base64.getDecoder().decode(normalized)
        } catch (_: Exception) {
            null
        }
        if (asBase64 != null && asBase64.size == keyLengthBytes) return asBase64

        if (Regex("^[0-9a-fA-F]{64}$").matches(normalized)) {
            val asHex = normalized.hexToBytes()
            if (asHex != null && asHex.size == keyLengthBytes) return asHex
        }

        throw IllegalArgumentException("Invalid field encryption key. Expected 32-byte base64 or 64-char hex.")
    }
}

fun ByteArray.toBase64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

fun String.fromBase64Url(): ByteArray =
    Base64.getUrlDecoder().decode(this)
