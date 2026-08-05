package com.ohmz.tday.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Throttle *keying* rules. These are the security-relevant half of AuthThrottleImpl and the only
 * half that can be tested here — everything below buildSubjectKeys needs Postgres, and this repo
 * has no test database.
 *
 * The bug being guarded: sign-in used to accrue failures on a bucket keyed by username alone, so
 * anyone on the internet who knew the owner's username could keep the owner's own account locked
 * indefinitely with one wrong password per backoff window.
 */
class AuthThrottleSubjectsTest {
    // Identity "hash" keeps assertions readable; the real HMAC is exercised by ClientSignals.
    private val identityHash: (String) -> String = { it }

    private fun subjects(
        action: ThrottleAction,
        ip: String = "203.0.113.10",
        deviceHint: String? = null,
        identifier: String? = "owner",
    ) = buildSubjectKeys(action, ip, deviceHint, identifier, identityHash)

    @Test
    fun `sign-in never creates a lockable bucket keyed on the account alone`() {
        val lockable = subjects(ThrottleAction.credentials)
            .filter { it.dimension in LOCKABLE_DIMENSIONS }

        assertFalse(
            lockable.any { it.dimension == ThrottleDimension.username },
            "a username-only bucket in the lockout path is a remote account-lockout DoS",
        )
        assertTrue(lockable.any { it.dimension == ThrottleDimension.ipUsername })
    }

    @Test
    fun `same account from two IPs lands in different sign-in buckets`() {
        val fromHome = subjects(ThrottleAction.credentials, ip = "198.51.100.7")
            .single { it.dimension == ThrottleDimension.ipUsername }
        val fromAttacker = subjects(ThrottleAction.credentials, ip = "203.0.113.99")
            .single { it.dimension == ThrottleDimension.ipUsername }

        assertEquals(fromHome.scope, fromAttacker.scope)
        assertNotEquals(
            fromHome.bucketKey,
            fromAttacker.bucketKey,
            "an attacker's failures must not accumulate against the owner's own bucket",
        )
    }

    @Test
    fun `same account from the same IP is one bucket`() {
        val first = subjects(ThrottleAction.credentials, ip = "198.51.100.7")
            .single { it.dimension == ThrottleDimension.ipUsername }
        val second = subjects(ThrottleAction.credentials, ip = "198.51.100.7")
            .single { it.dimension == ThrottleDimension.ipUsername }

        assertEquals(first.bucketKey, second.bucketKey)
    }

    @Test
    fun `different accounts from one IP are different buckets`() {
        val owner = subjects(ThrottleAction.credentials, identifier = "owner")
            .single { it.dimension == ThrottleDimension.ipUsername }
        val other = subjects(ThrottleAction.credentials, identifier = "someone-else")
            .single { it.dimension == ThrottleDimension.ipUsername }

        assertNotEquals(owner.bucketKey, other.bucketKey)
    }

    @Test
    fun `non-sign-in actions keep the plain username bucket`() {
        // Only `credentials` reaches recordFailure, so `register` cannot produce a lockout and
        // keeps the account-scoped quota that makes signup abuse expensive.
        val registerDimensions = subjects(ThrottleAction.register).map { it.dimension }

        assertTrue(ThrottleDimension.username in registerDimensions)
        assertFalse(ThrottleDimension.ipUsername in registerDimensions)
    }

    @Test
    fun `csrf never keys on an identifier`() {
        val dimensions = subjects(ThrottleAction.csrf, identifier = "owner").map { it.dimension }

        assertEquals(listOf(ThrottleDimension.ip), dimensions)
    }

    @Test
    fun `ip bucket is always present and device bucket only when hinted`() {
        val withoutDevice = subjects(ThrottleAction.credentials).map { it.dimension }
        assertTrue(ThrottleDimension.ip in withoutDevice)
        assertFalse(ThrottleDimension.device in withoutDevice)

        val withDevice = subjects(ThrottleAction.credentials, deviceHint = "device-abc").map { it.dimension }
        assertTrue(ThrottleDimension.device in withDevice)
    }

    @Test
    fun `a missing identifier still yields an ip bucket`() {
        val dimensions = subjects(ThrottleAction.credentials, identifier = null).map { it.dimension }

        assertEquals(listOf(ThrottleDimension.ip), dimensions)
    }

    @Test
    fun `the account-wide ceiling is not a lockable dimension`() {
        // The ceiling is enforced as a quota under CREDENTIALS_ACCOUNT_SCOPE with the `username`
        // dimension. If `username` ever became lockable, that quota would silently turn back into
        // the account-lockout DoS this change removed.
        assertFalse(ThrottleDimension.username in LOCKABLE_DIMENSIONS)
        assertTrue(CREDENTIALS_ACCOUNT_SCOPE.startsWith("credentials_account"))
    }

    @Test
    fun `scope strings stay stable for the buckets that did not change`() {
        val ipBucket = subjects(ThrottleAction.credentials).single { it.dimension == ThrottleDimension.ip }
        assertEquals("credentials:ip", ipBucket.scope)

        val ipUsernameBucket = subjects(ThrottleAction.credentials)
            .single { it.dimension == ThrottleDimension.ipUsername }
        assertEquals("credentials:ipUsername", ipUsernameBucket.scope)
    }

    @Test
    fun `blocked sign-in buckets report a connection-scoped reason code`() {
        assertEquals("auth_limit_ip", reasonCodeFor(ThrottleDimension.ipUsername))
        assertEquals("auth_limit_ip", reasonCodeFor(ThrottleDimension.ip))
        assertEquals("auth_limit_ip", reasonCodeFor(ThrottleDimension.device))
        assertEquals("auth_limit_username", reasonCodeFor(ThrottleDimension.username))
    }
}
