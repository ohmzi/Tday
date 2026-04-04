package com.ohmz.tday.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ProbeEncryption(private val keyBase64Url: String) {
    private val ivLengthBytes = 12
    private val tagLengthBits = 128
    private val random = SecureRandom()
    private val keyBytes: ByteArray = keyBase64Url.fromBase64Url()

    init {
        require(keyBytes.size == 32) {
            "TDAY_PROBE_ENCRYPTION_KEY must decode to exactly 32 bytes"
        }
    }

    fun encrypt(jsonPayload: String): String {
        val iv = ByteArray(ivLengthBytes).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(tagLengthBits, iv))
        val ciphertext = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))
        return (iv + ciphertext).toBase64Url()
    }
}
