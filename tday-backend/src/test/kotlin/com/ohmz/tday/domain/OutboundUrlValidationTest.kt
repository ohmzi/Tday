package com.ohmz.tday.domain

import arrow.core.Either
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Destination rules for URLs the *server* calls on a user's behalf (webhooks, push endpoints).
 *
 * The backend sits inside a Docker network on a home LAN, so before this existed a webhook could
 * be pointed at `http://database:5432`, the backend itself, the cloud metadata address, or any
 * device on the owner's network — turning the app into a request forger with a trusted position.
 */
class OutboundUrlValidationTest {

    private fun rejected(url: String) = validateOutboundUrl(url, "url") is Either.Left
    private fun accepted(url: String) = validateOutboundUrl(url, "url") is Either.Right

    @Test
    fun `rejects loopback in every spelling`() {
        listOf(
            "http://127.0.0.1:8080/hook",
            "http://127.1.2.3/hook",
            "https://localhost/hook",
            "http://localhost:2525/hook",
            "http://[::1]:8080/hook",
            "http://[::ffff:127.0.0.1]/hook",
        ).forEach { assertTrue(rejected(it), "should reject $it") }
    }

    @Test
    fun `rejects the private ranges a home LAN actually uses`() {
        listOf(
            "http://192.168.40.69:2525/hook",
            "http://10.0.0.5/hook",
            "http://172.16.0.1/hook",
            "http://172.31.255.254/hook",
        ).forEach { assertTrue(rejected(it), "should reject $it") }
    }

    @Test
    fun `rejects cloud metadata and link-local`() {
        assertTrue(rejected("http://169.254.169.254/latest/meta-data/"))
        assertTrue(rejected("http://169.254.1.1/"))
        assertTrue(rejected("http://[fe80::1]/hook"))
    }

    @Test
    fun `rejects compose service names that only resolve inside the container network`() {
        listOf(
            "http://database:5432/",
            "http://ollama:11434/api/generate",
            "http://tday-backend:8080/api/admin/users",
        ).forEach { assertTrue(rejected(it), "should reject $it") }
    }

    @Test
    fun `rejects CGNAT unspecified multicast and IPv6 ULA`() {
        assertTrue(rejected("http://100.64.0.1/"))
        assertTrue(rejected("http://0.0.0.0/"))
        assertTrue(rejected("http://224.0.0.1/"))
        assertTrue(rejected("http://[fd00::1]/hook"))
    }

    @Test
    fun `rejects non-http schemes`() {
        listOf(
            "file:///etc/passwd",
            "gopher://example.com/",
            "ftp://example.com/",
            "javascript:alert(1)",
        ).forEach { assertTrue(rejected(it), "should reject $it") }
    }

    @Test
    fun `rejects embedded credentials`() {
        assertTrue(rejected("https://user:pass@example.com/hook"))
    }

    @Test
    fun `rejects malformed empty and overlong input`() {
        assertTrue(rejected(""))
        assertTrue(rejected("   "))
        assertTrue(rejected("not a url"))
        assertTrue(rejected("https://example.com/" + "a".repeat(MAX_OUTBOUND_URL_LENGTH)))
    }

    @Test
    fun `accepts ordinary public destinations`() {
        listOf(
            "https://hooks.example.com/services/abc123",
            "http://example.org/webhook",
            "https://updates.push.services.mozilla.com/wpush/v2/gAAAA",
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://example.com:8443/hook",
        ).forEach { assertTrue(accepted(it), "should accept $it") }
    }

    @Test
    fun `public IP literals are still allowed`() {
        // Only private/reserved space is blocked; a self-hoster pointing at a public IP is fine.
        assertTrue(accepted("https://93.184.216.34/hook"))
        assertFalse(isBlockedIpLiteral("93.184.216.34"))
    }

    @Test
    fun `isBlockedIpLiteral rejects malformed octets rather than passing them through`() {
        assertTrue(isBlockedIpLiteral("999.1.1.1"))
        assertTrue(isBlockedIpLiteral(""))
    }
}
