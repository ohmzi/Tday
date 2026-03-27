package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
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

interface CredentialEnvelope {
    fun getPublicKeyDescriptor(): CredentialPublicKeyDescriptor
    fun decrypt(envelope: CredentialEnvelopeInput): DecryptedCredentials
}

class CredentialEnvelopeImpl(private val config: AppConfig) : CredentialEnvelope {
    private val logger = LoggerFactory.getLogger(CredentialEnvelopeImpl::class.java)
    private val envelopeVersion = "1"
    private val envelopeAlgorithm = "RSA-OAEP-256+A256GCM"
    private val aesKeyBytes = 32
    private val aesGcmIvBytes = 12
    private val aesGcmTagBits = 128

    private data class KeyMaterial(
        val keyId: String,
        val privateKey: PrivateKey,
        val publicKeySpkiDer: ByteArray,
    )

    private val keyMaterial: KeyMaterial by lazy { loadKeyMaterial() }

    override fun getPublicKeyDescriptor(): CredentialPublicKeyDescriptor {
        val km = keyMaterial
        return CredentialPublicKeyDescriptor(
            version = envelopeVersion,
            algorithm = envelopeAlgorithm,
            keyId = km.keyId,
            publicKey = km.publicKeySpkiDer.toBase64Url(),
        )
    }

    override fun decrypt(envelope: CredentialEnvelopeInput): DecryptedCredentials {
        val km = keyMaterial
        if (envelope.version != null && envelope.version != envelopeVersion) {
            throw IllegalArgumentException("unsupported_envelope_version")
        }
        if (envelope.keyId != null && envelope.keyId != km.keyId) {
            throw IllegalArgumentException("unknown_envelope_key")
        }

        val encryptedPayload = envelope.encryptedPayload.fromBase64Url()
        val encryptedKey = envelope.encryptedKey.fromBase64Url()
        val encryptedIv = envelope.encryptedIv.fromBase64Url()

        if (encryptedIv.size != aesGcmIvBytes) throw IllegalArgumentException("invalid_envelope_iv")
        if (encryptedPayload.size <= 16) throw IllegalArgumentException("invalid_envelope_payload")

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaepParams = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        rsaCipher.init(Cipher.DECRYPT_MODE, km.privateKey, oaepParams)
        val symmetricKey = rsaCipher.doFinal(encryptedKey)

        if (symmetricKey.size != aesKeyBytes) throw IllegalArgumentException("invalid_envelope_key")

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(symmetricKey, "AES"),
            GCMParameterSpec(aesGcmTagBits, encryptedIv),
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

    private fun loadKeyMaterial(): KeyMaterial {
        val configuredPem = config.credentialsPrivateKeyPem
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
