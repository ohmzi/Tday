package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasswordProofTest {
    private val config = testAppConfig(passwordProofChallengeTtlSec = 120, passwordProofMaxActive = 100)
    private val passwordService = PasswordServiceImpl(config)
    private val proof = PasswordProofImpl(config, passwordService)

    @Test
    fun `issues a challenge with expected fields`() {
        val storedHash = passwordService.hashPassword("TopSecret#123")
        val challenge = proof.issueChallenge("user@example.com", storedHash)

        assertNotNull(challenge.challengeId)
        assertNotNull(challenge.saltHex)
        assertTrue(challenge.iterations > 0)
        assertNotNull(challenge.expiresAt)
    }

    @Test
    fun `verifies a valid proof and rejects replay`() {
        val email = "proof.user@example.com"
        val password = "TopSecret#123"
        val storedHash = passwordService.hashPassword(password)
        val parsed = passwordService.parsePasswordHash(storedHash)!!

        val challenge = proof.issueChallenge(email, storedHash)

        val hashKey = parsed.hashHex.hexToBytes()!!
        val message = "login:${challenge.challengeId}:${email.trim().lowercase()}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hashKey, "HmacSHA256"))
        val proofHex = mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHex()

        val valid = proof.verify(email, challenge.challengeId, proofHex, "1", storedHash)
        assertTrue(valid)

        val replay = proof.verify(email, challenge.challengeId, proofHex, "1", storedHash)
        assertFalse(replay)
    }

    @Test
    fun `rejects wrong proof`() {
        val email = "wrong@example.com"
        val storedHash = passwordService.hashPassword("Correct#123")
        val challenge = proof.issueChallenge(email, storedHash)

        val badProof = "a".repeat(64)
        val result = proof.verify(email, challenge.challengeId, badProof, "1", storedHash)
        assertFalse(result)
    }

    @Test
    fun `consume removes the challenge`() {
        val storedHash = passwordService.hashPassword("Test")
        val challenge = proof.issueChallenge("consume@example.com", storedHash)
        proof.consume(challenge.challengeId)

        val parsed = passwordService.parsePasswordHash(storedHash)!!
        val hashKey = parsed.hashHex.hexToBytes()!!
        val message = "login:${challenge.challengeId}:consume@example.com"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hashKey, "HmacSHA256"))
        val proofHex = mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHex()

        val result = proof.verify("consume@example.com", challenge.challengeId, proofHex, "1", storedHash)
        assertFalse(result)
    }

    @Test
    fun `rejects wrong version`() {
        val email = "ver@example.com"
        val storedHash = passwordService.hashPassword("Test")
        val challenge = proof.issueChallenge(email, storedHash)

        val result = proof.verify(email, challenge.challengeId, "aabb", "999", storedHash)
        assertFalse(result)
    }
}
