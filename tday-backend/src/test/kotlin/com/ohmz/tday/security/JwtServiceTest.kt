package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtServiceTest {
    private val config = testAppConfig()
    private val service = JwtServiceImpl(config)

    @Test
    fun `encode and decode round-trip preserves claims`() {
        val claims = JwtUserClaims(
            id = "user-123",
            name = "Test User",
            email = "test@example.com",
            role = "USER",
            approvalStatus = "APPROVED",
            tokenVersion = 1,
            timeZone = "America/New_York",
        )

        val token = service.encode(claims)
        assertNotNull(token)

        val decoded = service.decode(token)
        assertNotNull(decoded)
        assertEquals("user-123", decoded.id)
        assertEquals("Test User", decoded.name)
        assertEquals("test@example.com", decoded.email)
        assertEquals("USER", decoded.role)
        assertEquals("APPROVED", decoded.approvalStatus)
        assertEquals(1, decoded.tokenVersion)
        assertEquals("America/New_York", decoded.timeZone)
    }

    @Test
    fun `decode returns null for invalid token`() {
        assertNull(service.decode("garbage.token.data"))
    }

    @Test
    fun `decode returns null for empty string`() {
        assertNull(service.decode(""))
    }

    @Test
    fun `tokens from different secrets are not interchangeable`() {
        val otherConfig = testAppConfig(authSecret = "completely-different-secret-key-1234567890")
        val otherService = JwtServiceImpl(otherConfig)

        val token = service.encode(JwtUserClaims(id = "user-1"))
        val decoded = otherService.decode(token)
        assertNull(decoded)
    }
}
