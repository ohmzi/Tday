package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.math.BigInteger
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class CredentialPublicKeyDescriptor(
    val version: String,
    val algorithm: String,
    val keyId: String,
    val publicKey: String,
)

data class DecryptedCredentials(val email: String, val password: String)

data class CredentialEnvelopeInput(
    val encryptedPayload: String,
    val encryptedKey: String,
    val encryptedIv: String,
    val keyId: String? = null,
    val version: String? = null,
)

object CredentialEnvelope {
    private val logger = LoggerFactory.getLogger(CredentialEnvelope::class.java)
    private const val ENVELOPE_VERSION = "1"
    private const val ENVELOPE_ALGORITHM = "RSA-OAEP-256+A256GCM"
    private const val AES_KEY_BYTES = 32
    private const val AES_GCM_IV_BYTES = 12
    private const val AES_GCM_TAG_BITS = 128

    private val keyMaterial: KeyMaterial by lazy { loadKeyMaterial() }

    fun getPublicKeyDescriptor(): CredentialPublicKeyDescriptor {
        val km = keyMaterial
        return CredentialPublicKeyDescriptor(
            version = ENVELOPE_VERSION,
            algorithm = ENVELOPE_ALGORITHM,
            keyId = km.keyId,
            publicKey = km.publicKeySpkiDer.toBase64Url(),
        )
    }

    fun decrypt(envelope: CredentialEnvelopeInput): DecryptedCredentials {
        val km = keyMaterial
        if (envelope.version != null && envelope.version != ENVELOPE_VERSION) {
            throw IllegalArgumentException("unsupported_envelope_version")
        }
        if (envelope.keyId != null && envelope.keyId != km.keyId) {
            throw IllegalArgumentException("unknown_envelope_key")
        }

        val encryptedPayload = envelope.encryptedPayload.fromBase64Url()
        val encryptedKey = envelope.encryptedKey.fromBase64Url()
        val encryptedIv = envelope.encryptedIv.fromBase64Url()

        if (encryptedIv.size != AES_GCM_IV_BYTES) throw IllegalArgumentException("invalid_envelope_iv")
        if (encryptedPayload.size <= 16) throw IllegalArgumentException("invalid_envelope_payload")

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, km.privateKey)
        val symmetricKey = rsaCipher.doFinal(encryptedKey)

        if (symmetricKey.size != AES_KEY_BYTES) throw IllegalArgumentException("invalid_envelope_key")

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(symmetricKey, "AES"),
            GCMParameterSpec(AES_GCM_TAG_BITS, encryptedIv),
        )

        val plaintext = String(aesCipher.doFinal(encryptedPayload), Charsets.UTF_8)
        val parsed = Json.decodeFromString<CredentialPayloadJson>(plaintext)

        val email = parsed.email?.trim()?.lowercase() ?: ""
        val password = parsed.password ?: ""
        if (email.isEmpty() || password.isEmpty()) throw IllegalArgumentException("invalid_envelope_credentials")

        return DecryptedCredentials(email, password)
    }

    @Serializable
    private data class CredentialPayloadJson(val email: String? = null, val password: String? = null)

    private data class KeyMaterial(
        val keyId: String,
        val privateKey: PrivateKey,
        val publicKeySpkiDer: ByteArray,
    )

    private fun loadKeyMaterial(): KeyMaterial {
        val configuredPem = AppConfig.credentialsPrivateKeyPem
        if (!configuredPem.isNullOrBlank()) {
            val normalizedPem = configuredPem.replace("\\n", "\n")
            val keyFactory = KeyFactory.getInstance("RSA")
            val pemBody = normalizedPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(pemBody))
            val privateKey = keyFactory.generatePrivate(keySpec) as RSAPrivateKey

            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                RSAPublicKeySpec(privateKey.modulus, BigInteger.valueOf(65537)),
            )
            val publicKeyDer = publicKey.encoded

            return KeyMaterial(
                keyId = deriveKeyId(publicKeyDer),
                privateKey = privateKey,
                publicKeySpkiDer = publicKeyDer,
            )
        }

        logger.warn("[security] auth_credentials_private_key_missing using ephemeral login envelope key")
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKeyDer = keyPair.public.encoded

        return KeyMaterial(
            keyId = deriveKeyId(publicKeyDer),
            privateKey = keyPair.private,
            publicKeySpkiDer = publicKeyDer,
        )
    }

    private fun deriveKeyId(publicKeyDer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyDer)
        return digest.toBase64Url().take(24)
    }
}
