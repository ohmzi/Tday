package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        // Task titles are the primary sensitive content of a task app.
        assertTrue(svc.isSensitiveField("title"))
        assertTrue(svc.isSensitiveField("overriddenTitle"))
        assertFalse(svc.isSensitiveField("priority"))
    }

    @Test
    fun `encryptIfSensitive only encrypts sensitive fields`() {
        val svc = encryptionService()
        val encrypted = svc.encryptIfSensitive("description", "test data")
        assertTrue(svc.isEncrypted(assertNotNull(encrypted)))

        val notEncrypted = svc.encryptIfSensitive("priority", "test data")
        assertEquals("test data", notEncrypted)
    }

    @Test
    fun `title round-trips through encryptIfSensitive`() {
        val svc = encryptionService()
        for (field in listOf("title", "overriddenTitle")) {
            val plaintext = "Call the clinic about the results"
            val stored = assertNotNull(svc.encryptIfSensitive(field, plaintext))
            assertTrue(svc.isEncrypted(stored), "$field should be stored encrypted")
            assertEquals(plaintext, svc.decryptIfEncrypted(stored))
        }
    }

    @Test
    fun `decryptIfEncrypted handles null and plain text`() {
        val svc = encryptionService()
        assertNull(svc.decryptIfEncrypted(null))
        assertEquals("plain", svc.decryptIfEncrypted("plain"))
    }

    /**
     * The no-backfill property: rows written before titles were encrypted carry no
     * "enc:v1:" prefix and must keep reading back verbatim, so mixed plaintext and
     * ciphertext can coexist in the same column indefinitely.
     */
    @Test
    fun `pre-existing plaintext titles pass through unchanged`() {
        val svc = encryptionService()
        val legacyTitle = "Buy milk"
        assertEquals(legacyTitle, svc.decryptIfEncrypted(legacyTitle))
        assertEquals(legacyTitle, svc.decryptRequired(legacyTitle))
    }

    @Test
    fun `encryptIfSensitive returns input unchanged when no key is configured`() {
        val svc = encryptionService(key = null)
        assertEquals("Buy milk", svc.encryptIfSensitive("title", "Buy milk"))
        assertEquals("Buy milk", svc.encryptRequired("title", "Buy milk"))
        assertNull(svc.encryptIfSensitive("title", null))
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
