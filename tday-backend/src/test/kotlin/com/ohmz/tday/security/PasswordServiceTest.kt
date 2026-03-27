package com.ohmz.tday.security

import com.ohmz.tday.config.AppConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordServiceTest {
    private val config = testAppConfig()
    private val service = PasswordServiceImpl(config)

    @Test
    fun `hashes using the modern pbkdf2 format`() {
        val hash = service.hashPassword("Strong!Pass123")
        assertTrue(hash.startsWith("pbkdf2_sha256\$"))
        val segments = hash.split("$")
        assertEquals(4, segments.size)
    }

    @Test
    fun `verifies correct password`() {
        val hash = service.hashPassword("TopSecret#99")
        val result = service.verifyPassword("TopSecret#99", hash)
        assertTrue(result.valid)
        assertFalse(result.needsRehash)
    }

    @Test
    fun `rejects incorrect password`() {
        val hash = service.hashPassword("TopSecret#99")
        val result = service.verifyPassword("WrongPassword", hash)
        assertFalse(result.valid)
    }

    @Test
    fun `each hash is unique due to random salt`() {
        val h1 = service.hashPassword("Same")
        val h2 = service.hashPassword("Same")
        assertTrue(h1 != h2)
    }

    @Test
    fun `flags legacy hashes as needing rehash`() {
        val legacySalt = "a".repeat(32)
        val legacyHash = "$legacySalt:${"b".repeat(64)}"
        val parsed = service.parsePasswordHash(legacyHash)
        assertNotNull(parsed)
        assertEquals("legacy", parsed.format)
    }

    @Test
    fun `parses modern hash correctly`() {
        val hash = service.hashPassword("Test123!")
        val parsed = service.parsePasswordHash(hash)
        assertNotNull(parsed)
        assertEquals("modern", parsed.format)
        assertEquals(config.pbkdf2Iterations, parsed.iterations)
    }

    @Test
    fun `returns null for invalid hash`() {
        assertNull(service.parsePasswordHash(""))
        assertNull(service.parsePasswordHash("totally-invalid"))
    }

    @Test
    fun `flags rehash when iterations are lower than current config`() {
        val lowIterConfig = testAppConfig(pbkdf2Iterations = 100_000)
        val lowService = PasswordServiceImpl(lowIterConfig)
        val hash = lowService.hashPassword("Password1!")

        val highIterConfig = testAppConfig(pbkdf2Iterations = 310_000)
        val highService = PasswordServiceImpl(highIterConfig)
        val result = highService.verifyPassword("Password1!", hash)
        assertTrue(result.valid)
        assertTrue(result.needsRehash)
    }
}
