package com.ohmz.tday.security

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.crypto.DirectDecrypter
import com.nimbusds.jose.crypto.DirectEncrypter
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import com.ohmz.tday.config.AppConfig
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID

data class JwtUserClaims(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val approvalStatus: String? = null,
    val tokenVersion: Int? = null,
    val timeZone: String? = null,
    val sessionStartedAtEpochSec: Long? = null,
    val expiresAtEpochSec: Long? = null,
)

interface JwtService {
    fun decode(token: String): JwtUserClaims?
    fun encode(claims: JwtUserClaims): String
    fun currentEpochSeconds(): Long
}

class JwtServiceImpl(
    private val config: AppConfig,
    private val clock: Clock = Clock.systemUTC(),
) : JwtService {
    private val encryptionKey: ByteArray by lazy { deriveEncryptionKey(config.authSecret) }

    override fun decode(token: String): JwtUserClaims? {
        return try {
            val jwe = EncryptedJWT.parse(token)
            val decrypter = DirectDecrypter(encryptionKey)
            jwe.decrypt(decrypter)
            val claims = jwe.jwtClaimsSet

            val exp = claims.expirationTime
            if (exp == null) return null

            val now = Instant.now(clock)
            if (exp.toInstant().isBefore(now)) {
                return null
            }

            val issuedAtEpochSec = claims.issueTime?.toInstant()?.epochSecond
            val sessionStartedAtEpochSec = claims.longClaim("sessionStartedAt")
                ?: issuedAtEpochSec
                ?: now.epochSecond

            JwtUserClaims(
                id = claims.getStringClaim("id") ?: claims.subject ?: return null,
                name = claims.getStringClaim("name"),
                email = claims.getStringClaim("email"),
                role = claims.getStringClaim("role"),
                approvalStatus = claims.getStringClaim("approvalStatus"),
                tokenVersion = claims.getIntegerClaim("tokenVersion"),
                timeZone = claims.getStringClaim("timeZone"),
                sessionStartedAtEpochSec = sessionStartedAtEpochSec,
                expiresAtEpochSec = exp.toInstant().epochSecond,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun encode(claims: JwtUserClaims): String {
        val now = Instant.now(clock)
        val sessionStartedAtEpochSec = claims.sessionStartedAtEpochSec ?: now.epochSecond
        val exp = now.plusSeconds(config.sessionMaxAgeSec.toLong())

        val jwtClaims = JWTClaimsSet.Builder()
            .subject(claims.id)
            .claim("id", claims.id)
            .claim("sessionStartedAt", sessionStartedAtEpochSec)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .jwtID(UUID.randomUUID().toString())

        claims.name?.let { jwtClaims.claim("name", it) }
        claims.email?.let { jwtClaims.claim("email", it) }
        claims.role?.let { jwtClaims.claim("role", it) }
        claims.approvalStatus?.let { jwtClaims.claim("approvalStatus", it) }
        claims.tokenVersion?.let { jwtClaims.claim("tokenVersion", it) }
        claims.timeZone?.let { jwtClaims.claim("timeZone", it) }

        val header = JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256CBC_HS512)
            .contentType("JWT")
            .build()

        val jwe = EncryptedJWT(header, jwtClaims.build())
        jwe.encrypt(DirectEncrypter(encryptionKey))
        return jwe.serialize()
    }

    override fun currentEpochSeconds(): Long = Instant.now(clock).epochSecond

    private fun JWTClaimsSet.longClaim(name: String): Long? {
        val value = getClaim(name) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun deriveEncryptionKey(secret: String): ByteArray {
        val ikm = secret.toByteArray(Charsets.UTF_8)
        val salt = ByteArray(0)
        val info = "Auth.js Generated Encryption Key".toByteArray(Charsets.UTF_8)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))

        val key = ByteArray(64)
        hkdf.generateBytes(key, 0, 64)
        return key
    }
}
