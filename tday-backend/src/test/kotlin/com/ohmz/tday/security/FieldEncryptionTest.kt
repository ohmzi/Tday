package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldEncryptionTest {
    private val testKeyBytes = ByteArray(32) { it.toByte() }
    private val testKeyBase64 = Base64.getEncoder().encodeToString(testKeyBytes)

    private fun encryptionService(
        keyId: String = "primary",
        key: String? = testKeyBase64,
        aad: String? = "tday:v1",
    ): FieldEncryptionImpl {
        val config = testAppConfig(
            dataEncryptionKeyId = keyId,
            dataEncryptionKey = key,
            dataEncryptionAad = aad,
        )
        return FieldEncryptionImpl(config)
    }

    @Test
    fun `encrypts and decrypts text round-trip`() {
        val svc = encryptionService()
        val plaintext = "Hello, sensitive data!"
        val encrypted = svc.encrypt(plaintext)

        assertTrue(svc.isEncrypted(encrypted))
        assertEquals(plaintext, svc.decrypt(encrypted))
    }

    @Test
    fun `returns empty string unchanged`() {
        val svc = encryptionService()
        assertEquals("", svc.encrypt(""))
    }

    @Test
    fun `does not double-encrypt`() {
        val svc = encryptionService()
        val encrypted = svc.encrypt("secret")
        val doubleEncrypted = svc.encrypt(encrypted)
        assertEquals(encrypted, doubleEncrypted)
    }

    @Test
    fun `identifies sensitive fields`() {
        val svc = encryptionService()
        assertTrue(svc.isSensitiveField("description"))
        assertTrue(svc.isSensitiveField("content"))
        assertFalse(svc.isSensitiveField("title"))
    }

    @Test
    fun `encryptIfSensitive only encrypts sensitive fields`() {
        val svc = encryptionService()
        val encrypted = svc.encryptIfSensitive("description", "test data")
        assertTrue(svc.isEncrypted(encrypted!!))

        val notEncrypted = svc.encryptIfSensitive("title", "test data")
        assertEquals("test data", notEncrypted)
    }

    @Test
    fun `decryptIfEncrypted handles null and plain text`() {
        val svc = encryptionService()
        assertNull(svc.decryptIfEncrypted(null))
        assertEquals("plain", svc.decryptIfEncrypted("plain"))
    }

    @Test
    fun `isConfigured returns false when no key is set`() {
        val svc = encryptionService(key = null)
        assertFalse(svc.isConfigured())
    }

    @Test
    fun `each encryption produces unique ciphertext`() {
        val svc = encryptionService()
        val e1 = svc.encrypt("same input")
        val e2 = svc.encrypt("same input")
        assertTrue(e1 != e2)
        assertEquals(svc.decrypt(e1), svc.decrypt(e2))
    }
}
