package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialEnvelopeTest {
    private val config = testAppConfig()
    private val envelope = CredentialEnvelopeImpl(config)

    @Test
    fun `getPublicKeyDescriptor returns valid key descriptor`() {
        val descriptor = envelope.getPublicKeyDescriptor()
        assertEquals("1", descriptor.version)
        assertEquals("RSA-OAEP-256+A256GCM", descriptor.algorithm)
        assertNotNull(descriptor.keyId)
        assertTrue(descriptor.publicKey.isNotEmpty())
    }

    @Test
    fun `decrypts a valid envelope round-trip`() {
        val descriptor = envelope.getPublicKeyDescriptor()
        val publicKeyDer = descriptor.publicKey.fromBase64Url()

        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyDer))

        val symmetricKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

        val payload = """{"email":"test@example.com","password":"Secret#123"}"""

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(symmetricKey, "AES"), GCMParameterSpec(128, iv))
        val encryptedPayload = aesCipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedKey = rsaCipher.doFinal(symmetricKey)

        val input = CredentialEnvelopeInput(
            encryptedPayload = encryptedPayload.toBase64Url(),
            encryptedKey = encryptedKey.toBase64Url(),
            encryptedIv = iv.toBase64Url(),
            keyId = descriptor.keyId,
            version = "1",
        )

        val result = envelope.decrypt(input)
        assertEquals("test@example.com", result.email)
        assertEquals("Secret#123", result.password)
    }
}
