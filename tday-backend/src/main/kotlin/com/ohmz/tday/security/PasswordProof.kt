package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PasswordProofChallengePayload(
    val version: String,
    val algorithm: String,
    val challengeId: String,
    val saltHex: String,
    val iterations: Int,
    val expiresAt: String,
)

interface PasswordProof {
    fun normalizeEmail(value: String?): String?
    fun issueChallenge(email: String, storedPasswordHash: String?): PasswordProofChallengePayload
    fun verify(email: String, challengeId: String, proofHex: String, proofVersion: String?, storedPasswordHash: String?): Boolean
    fun consume(challengeId: String)
}

class PasswordProofImpl(
    private val config: AppConfig,
    private val passwordService: PasswordService,
) : PasswordProof {
    private val version = "1"
    private val algorithm = "pbkdf2_sha256+hmac_sha256"
    private val saltBytes = 16
    private val random = SecureRandom()

    private data class ChallengeEntry(
        val email: String,
        val saltHex: String,
        val iterations: Int,
        val expiresAtMs: Long,
    )

    private val challenges = ConcurrentHashMap<String, ChallengeEntry>()

    override fun normalizeEmail(value: String?): String? {
        if (value == null) return null
        val normalized = value.trim().lowercase()
        return normalized.ifEmpty { null }
    }

    override fun issueChallenge(email: String, storedPasswordHash: String?): PasswordProofChallengePayload {
        val normalizedEmail = normalizeEmail(email) ?: throw IllegalArgumentException("invalid_challenge_email")

        val parsedHash = passwordService.parsePasswordHash(storedPasswordHash ?: "")
        val iterations = parsedHash?.iterations ?: config.pbkdf2Iterations
        val saltHex = parsedHash?.saltHex ?: ByteArray(saltBytes).also { random.nextBytes(it) }.toHex()
        val challengeId = ByteArray(24).also { random.nextBytes(it) }.toBase64Url()
        val now = System.currentTimeMillis()
        val expiresAtMs = now + config.passwordProofChallengeTtlSec * 1000L

        pruneExpired(now)
        evictOldest()
        challenges[challengeId] = ChallengeEntry(normalizedEmail, saltHex, iterations, expiresAtMs)

        return PasswordProofChallengePayload(
            version = version,
            algorithm = algorithm,
            challengeId = challengeId,
            saltHex = saltHex,
            iterations = iterations,
            expiresAt = Instant.ofEpochMilli(expiresAtMs).toString(),
        )
    }

    override fun verify(
        email: String,
        challengeId: String,
        proofHex: String,
        proofVersion: String?,
        storedPasswordHash: String?,
    ): Boolean {
        val normalizedEmail = normalizeEmail(email) ?: return false
        val trimmedChallengeId = challengeId.trim()
        if (trimmedChallengeId.isEmpty()) return false

        if (proofVersion != null && proofVersion.trim() != version) return false

        val normalizedProof = normalizeProofHex(proofHex) ?: return false

        val challenge = challenges.remove(trimmedChallengeId) ?: return false
        if (challenge.expiresAtMs < System.currentTimeMillis()) return false
        if (challenge.email != normalizedEmail) return false

        val parsedHash = passwordService.parsePasswordHash(storedPasswordHash ?: "") ?: return false
        if (parsedHash.saltHex != challenge.saltHex || parsedHash.iterations != challenge.iterations) return false

        val hashKey = parsedHash.hashHex.hexToBytes() ?: return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hashKey, "HmacSHA256"))
        val message = "login:$trimmedChallengeId:$normalizedEmail"
        val expectedProof = mac.doFinal(message.toByteArray(Charsets.UTF_8))

        val providedProof = normalizedProof.hexToBytes() ?: return false
        if (providedProof.size != expectedProof.size) return false

        return MessageDigest.isEqual(expectedProof, providedProof)
    }

    override fun consume(challengeId: String) {
        val normalized = challengeId.trim()
        if (normalized.isNotEmpty()) challenges.remove(normalized)
    }

    private fun normalizeProofHex(raw: String): String? {
        val normalized = raw.trim().lowercase()
        if (!Regex("^[0-9a-f]+$").matches(normalized)) return null
        if (normalized.length % 2 != 0) return null
        return normalized
    }

    private fun pruneExpired(nowMs: Long) {
        challenges.entries.removeIf { it.value.expiresAtMs <= nowMs }
    }

    private fun evictOldest() {
        val max = config.passwordProofMaxActive
        while (challenges.size >= max) {
            val oldest = challenges.keys.firstOrNull() ?: break
            challenges.remove(oldest)
        }
    }
}
