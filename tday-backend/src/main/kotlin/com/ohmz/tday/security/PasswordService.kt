package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordHashMetadata(
    val format: String,
    val iterations: Int,
    val saltHex: String,
    val hashHex: String,
)

data class PasswordVerification(
    val valid: Boolean,
    val needsRehash: Boolean,
)

interface PasswordService {
    fun hashPassword(plainTextPassword: String): String
    fun verifyPassword(plainTextPassword: String, storedHash: String): PasswordVerification
    fun parsePasswordHash(storedHash: String): PasswordHashMetadata?
}

class PasswordServiceImpl(private val config: AppConfig) : PasswordService {
    private val hashAlgorithmId = "pbkdf2_sha256"
    private val legacyIterations = 10_000
    private val saltSizeBytes = 16
    private val derivedKeyLengthBits = 256
    private val random = SecureRandom()
    private val modernPrefix = hashAlgorithmId + "$"

    override fun hashPassword(plainTextPassword: String): String {
        val iterations = config.pbkdf2Iterations
        val salt = ByteArray(saltSizeBytes).also { random.nextBytes(it) }
        val saltHex = salt.toHex()
        val hashHex = deriveHash(plainTextPassword, salt, iterations)
        return "$hashAlgorithmId\$$iterations\$$saltHex\$$hashHex"
    }

    override fun verifyPassword(plainTextPassword: String, storedHash: String): PasswordVerification {
        val parsed = parsePasswordHash(storedHash)
            ?: return PasswordVerification(valid = false, needsRehash = false)

        val saltBytes = parsed.saltHex.hexToBytes()
            ?: return PasswordVerification(valid = false, needsRehash = false)

        val calculatedHash = deriveHash(plainTextPassword, saltBytes, parsed.iterations)
        val calcBytes = calculatedHash.hexToBytes()
        val storedBytes = parsed.hashHex.hexToBytes()

        if (calcBytes == null || storedBytes == null) {
            return PasswordVerification(valid = false, needsRehash = false)
        }

        val valid = timingSafeEquals(calcBytes, storedBytes)
        if (!valid) return PasswordVerification(valid = false, needsRehash = false)

        val needsRehash = parsed.format == "legacy" || parsed.iterations < config.pbkdf2Iterations
        return PasswordVerification(valid = true, needsRehash = needsRehash)
    }

    override fun parsePasswordHash(storedHash: String): PasswordHashMetadata? {
        val trimmed = storedHash.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith(modernPrefix)) {
            val segments = trimmed.split("$")
            if (segments.size != 4) return null
            val algorithm = segments[0]
            val iterationsRaw = segments[1]
            val saltHex = segments[2]
            val hashHex = segments[3]
            if (algorithm != hashAlgorithmId) return null
            val iterations = iterationsRaw.toIntOrNull() ?: return null
            if (iterations <= 0) return null
            if (!saltHex.isHex() || !hashHex.isHex()) return null
            return PasswordHashMetadata("modern", iterations, saltHex, hashHex)
        }

        if (":" in trimmed) {
            val idx = trimmed.indexOf(':')
            val saltHex = trimmed.substring(0, idx)
            val hashHex = trimmed.substring(idx + 1)
            if (saltHex.isEmpty() || hashHex.isEmpty()) return null
            if (!saltHex.isHex() || !hashHex.isHex()) return null
            return PasswordHashMetadata("legacy", legacyIterations, saltHex, hashHex)
        }

        return null
    }

    private fun deriveHash(password: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, derivedKeyLengthBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.toHex()
    }

    private fun timingSafeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.size != b.size) return false
        var mismatch = 0
        for (i in a.indices) {
            mismatch = mismatch or (a[i].toInt() xor b[i].toInt())
        }
        return mismatch == 0
    }
}
