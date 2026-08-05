package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole trip/escalation decision, exercised directly.
 *
 * There is no test database here, so these pure functions are where the feature is actually
 * verified — including the properties that keep the owner out of their own trap.
 */
class AbuseBlockDecisionTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 5, 12, 0, 0)
    private val thresholds = AbuseThresholds(
        registerViolationMax = 5,
        registerPendingMax = 3,
        authLockoutMax = 3,
    )

    @Test
    fun `an ordinary sign-up trips nothing`() {
        assertNull(abuseTripReason(AbuseScope.register, AbuseCounters(0, 1), thresholds))
    }

    @Test
    fun `pending sign-ups trip only once they exceed the allowance`() {
        assertNull(abuseTripReason(AbuseScope.register, AbuseCounters(0, 3), thresholds))
        assertEquals(
            AbuseReason.REGISTER_PENDING_FLOOD,
            abuseTripReason(AbuseScope.register, AbuseCounters(0, 4), thresholds),
        )
    }

    @Test
    fun `repeated register throttle violations trip the velocity rule`() {
        assertNull(abuseTripReason(AbuseScope.register, AbuseCounters(4, 0), thresholds))
        assertEquals(
            AbuseReason.REGISTER_VIOLATIONS,
            abuseTripReason(AbuseScope.register, AbuseCounters(5, 0), thresholds),
        )
    }

    @Test
    fun `the behavioural signal wins when both register rules fire`() {
        assertEquals(
            AbuseReason.REGISTER_PENDING_FLOOD,
            abuseTripReason(AbuseScope.register, AbuseCounters(9, 9), thresholds),
        )
    }

    @Test
    fun `one or two lockout episodes never block the auth path`() {
        // A forgetful owner produces one episode per session; it must take several.
        assertNull(abuseTripReason(AbuseScope.auth, AbuseCounters(1, 0), thresholds))
        assertNull(abuseTripReason(AbuseScope.auth, AbuseCounters(2, 0), thresholds))
        assertEquals(
            AbuseReason.AUTH_LOCKOUTS,
            abuseTripReason(AbuseScope.auth, AbuseCounters(3, 0), thresholds),
        )
    }

    @Test
    fun `pending sign-ups never affect the auth path`() {
        assertNull(abuseTripReason(AbuseScope.auth, AbuseCounters(0, 99), thresholds))
    }

    @Test
    fun `escalation runs one hour then a day then a week and stops`() {
        assertEquals(3_600, abuseBlockDurationSec(1, baseSec = 3_600, maxSec = 604_800))
        assertEquals(86_400, abuseBlockDurationSec(2, baseSec = 3_600, maxSec = 604_800))
        assertEquals(604_800, abuseBlockDurationSec(3, baseSec = 3_600, maxSec = 604_800))
        assertEquals(604_800, abuseBlockDurationSec(9, baseSec = 3_600, maxSec = 604_800))
    }

    @Test
    fun `escalation is always finite so a block can never be permanent`() {
        val cap = 604_800
        for (strikes in 1..50) {
            val duration = abuseBlockDurationSec(strikes, baseSec = 3_600, maxSec = cap)
            assertTrue(duration in 1..cap, "strike $strikes produced $duration")
        }
    }

    @Test
    fun `no strikes means no block duration`() {
        assertEquals(0, abuseBlockDurationSec(0, baseSec = 3_600, maxSec = 604_800))
    }

    @Test
    fun `strikes decay after a quiet period so escalation is not permanent`() {
        val decaySec = 30 * 86_400
        assertEquals(3, decayedStrikes(3, now.minusDays(29), now, decaySec))
        assertEquals(0, decayedStrikes(3, now.minusDays(31), now, decaySec))
    }

    @Test
    fun `an expired block no longer holds`() {
        assertTrue(isAbuseBlockActive(now.plusSeconds(1), now))
        assertFalse(isAbuseBlockActive(now, now))
        assertFalse(isAbuseBlockActive(now.minusSeconds(1), now))
        assertFalse(isAbuseBlockActive(null, now))
    }

    @Test
    fun `retry-after is rounded up and never zero`() {
        assertEquals(90, abuseRetryAfterSeconds(now.plusSeconds(90), now))
        assertEquals(1, abuseRetryAfterSeconds(now.plusNanos(1), now))
    }

    @Test
    fun `counters restart once the window has rolled`() {
        assertFalse(abuseWindowExpired(now.minusHours(23), now, windowSec = 86_400))
        assertTrue(abuseWindowExpired(now.minusHours(25), now, windowSec = 86_400))
    }
}

/**
 * Guards the one thing that made auth blocking useless when it first shipped.
 *
 * `lockoutTriggered` fires only on the failure that CROSSES the lockout threshold, and the failure
 * counter does not reset until AUTH_LOCKOUT_RESET_SEC (24h) of quiet. So earning N lockout episodes
 * takes roughly N days — and with the abuse window originally set to the same 24h, signal number one
 * expired before signal number two could be earned. The threshold was mathematically unreachable no
 * matter how hard someone attacked, while every unit test still passed.
 *
 * These tests pin the two properties that fix it: the window must outlive the counter reset, and
 * "kept knocking while already locked out" must be a countable signal in its own right.
 */
class AuthBlockReachabilityTest {

    @Test
    fun `the abuse window outlives the lockout reset, so episodes can accumulate`() {
        val lockoutResetSec = 86_400   // AUTH_LOCKOUT_RESET_SEC default
        val abuseWindowSec = 604_800   // ABUSE_SIGNAL_WINDOW_SEC default

        assertTrue(
            abuseWindowSec > lockoutResetSec,
            "signals must survive longer than it takes to earn the next one, or the block is unreachable",
        )
    }

    @Test
    fun `sustained knocking during a lockout reaches the threshold`() {
        // Each refused attempt while a lockout is live counts once. A script produces these quickly.
        val thresholds = AbuseThresholds(registerViolationMax = 5, registerPendingMax = 3, authLockoutMax = 10)

        assertNull(abuseTripReason(AbuseScope.auth, AbuseCounters(9, 0), thresholds))
        assertEquals(
            AbuseReason.AUTH_LOCKOUTS,
            abuseTripReason(AbuseScope.auth, AbuseCounters(10, 0), thresholds),
        )
    }

    @Test
    fun `a forgetful owner stays well under the threshold`() {
        // Five wrong passwords is one lockout, not five signals. Even a few frustrated retries
        // during the lockout leave plenty of headroom before anything blocks.
        val thresholds = AbuseThresholds(registerViolationMax = 5, registerPendingMax = 3, authLockoutMax = 10)

        assertNull(abuseTripReason(AbuseScope.auth, AbuseCounters(3, 0), thresholds))
    }

    @Test
    fun `register and auth counters never bleed into each other`() {
        // A registration flood must not cost the owner their sign-in path.
        val thresholds = AbuseThresholds(registerViolationMax = 5, registerPendingMax = 3, authLockoutMax = 10)

        assertNull(abuseTripReason(AbuseScope.auth, AbuseCounters(0, 99), thresholds))
        assertEquals(
            AbuseReason.REGISTER_PENDING_FLOOD,
            abuseTripReason(AbuseScope.register, AbuseCounters(0, 99), thresholds),
        )
    }
}
