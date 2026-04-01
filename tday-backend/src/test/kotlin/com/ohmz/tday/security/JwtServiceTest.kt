package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtServiceTest {
    @Test
    fun `encode and decode round-trip preserves claims`() {
        val issuedAt = Instant.parse("2026-04-01T12:00:00Z")
        val config = testAppConfig(sessionMaxAgeSec = 3_600)
        val service = JwtServiceImpl(config, fixedClock(issuedAt))
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
        assertEquals(issuedAt.epochSecond, decoded.sessionStartedAtEpochSec)
        assertEquals(issuedAt.plusSeconds(config.sessionMaxAgeSec.toLong()).epochSecond, decoded.expiresAtEpochSec)
    }

    @Test
    fun `decode returns null for invalid token`() {
        val service = JwtServiceImpl(testAppConfig())
        assertNull(service.decode("garbage.token.data"))
    }

    @Test
    fun `decode returns null for empty string`() {
        val service = JwtServiceImpl(testAppConfig())
        assertNull(service.decode(""))
    }

    @Test
    fun `tokens from different secrets are not interchangeable`() {
        val config = testAppConfig()
        val service = JwtServiceImpl(config)
        val otherConfig = testAppConfig(authSecret = "completely-different-secret-key-1234567890")
        val otherService = JwtServiceImpl(otherConfig)

        val token = service.encode(JwtUserClaims(id = "user-1"))
        val decoded = otherService.decode(token)
        assertNull(decoded)
    }

    @Test
    fun `decode returns null when token is past idle expiry`() {
        val issuedAt = Instant.parse("2026-04-01T08:00:00Z")
        val config = testAppConfig(sessionMaxAgeSec = 60)
        val issuingService = JwtServiceImpl(config, fixedClock(issuedAt))
        val token = issuingService.encode(JwtUserClaims(id = "user-1"))

        val expiredService = JwtServiceImpl(
            config,
            Clock.offset(fixedClock(issuedAt), Duration.ofSeconds(61)),
        )

        assertNull(expiredService.decode(token))
    }

    @Test
    fun `renewed tokens preserve original session start time`() {
        val issuedAt = Instant.parse("2026-04-01T09:00:00Z")
        val config = testAppConfig(sessionMaxAgeSec = 3_600)
        val issuingService = JwtServiceImpl(config, fixedClock(issuedAt))
        val originalToken = issuingService.encode(JwtUserClaims(id = "user-1"))
        val originalClaims = issuingService.decode(originalToken)
        assertNotNull(originalClaims)

        val renewalService = JwtServiceImpl(
            config,
            Clock.offset(fixedClock(issuedAt), Duration.ofMinutes(30)),
        )
        val renewedToken = renewalService.encode(originalClaims)
        val renewedClaims = renewalService.decode(renewedToken)

        assertNotNull(renewedClaims)
        assertEquals(originalClaims.sessionStartedAtEpochSec, renewedClaims.sessionStartedAtEpochSec)
        assertTrue((renewedClaims.expiresAtEpochSec ?: 0) > (originalClaims.expiresAtEpochSec ?: 0))
    }

    private fun fixedClock(instant: Instant): Clock =
        Clock.fixed(instant, ZoneOffset.UTC)
}
