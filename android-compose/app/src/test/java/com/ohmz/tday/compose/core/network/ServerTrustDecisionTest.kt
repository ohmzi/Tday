package com.ohmz.tday.compose.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerTrustDecisionTest {

    @Test
    fun `stored pin matching the certificate accepts`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_A,
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.Accept, decision)
    }

    @Test
    fun `stored pin is compared case-insensitively`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_A.lowercase(),
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.Accept, decision)
    }

    @Test
    fun `stored pin not matching the certificate refuses instead of re-enrolling`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_B,
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.RejectMismatch, decision)
    }

    @Test
    fun `a stored pin wins over any pending approval`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_B,
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = FINGERPRINT_B,
        )

        assertEquals(ServerTrustDecision.RejectMismatch, decision)
    }

    @Test
    fun `unknown certificate with no pin and no approval refuses`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_A,
            storedPin = null,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.RejectUnknown, decision)
    }

    @Test
    fun `certificate the user confirmed enrolls`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_A,
            storedPin = null,
            enrollmentExpecting = FINGERPRINT_A,
        )

        assertEquals(ServerTrustDecision.Enroll(FINGERPRINT_A), decision)
    }

    @Test
    fun `certificate swapped after the user confirmed still refuses`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = FINGERPRINT_B,
            storedPin = null,
            enrollmentExpecting = FINGERPRINT_A,
        )

        assertEquals(ServerTrustDecision.RejectUnknown, decision)
    }

    @Test
    fun `certificate without a derivable fingerprint refuses`() {
        val decision = decideServerTrust(
            host = LAN_HOST,
            fingerprint = null,
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = FINGERPRINT_A,
        )

        assertEquals(ServerTrustDecision.RejectUnknown, decision)
    }

    // --- Enrollment is LAN-only -------------------------------------------------------------

    @Test
    fun `public host never gets the option to trust an unverifiable certificate`() {
        val decision = decideServerTrust(
            host = "tday.example.com",
            fingerprint = FINGERPRINT_A,
            storedPin = null,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.RejectPublicHost, decision)
    }

    @Test
    fun `approving a public host's certificate still refuses to enroll it`() {
        val decision = decideServerTrust(
            host = "tday.example.com",
            fingerprint = FINGERPRINT_A,
            storedPin = null,
            enrollmentExpecting = FINGERPRINT_A,
        )

        assertEquals(ServerTrustDecision.RejectPublicHost, decision)
    }

    @Test
    fun `an unknown host is treated as public`() {
        val decision = decideServerTrust(
            host = null,
            fingerprint = FINGERPRINT_A,
            storedPin = null,
            enrollmentExpecting = FINGERPRINT_A,
        )

        assertEquals(ServerTrustDecision.RejectPublicHost, decision)
    }

    @Test
    fun `a pin stored for a public host is still honoured`() {
        val decision = decideServerTrust(
            host = "tday.example.com",
            fingerprint = FINGERPRINT_A,
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.Accept, decision)
    }

    @Test
    fun `a public host presenting the wrong certificate reports a mismatch`() {
        val decision = decideServerTrust(
            host = "tday.example.com",
            fingerprint = FINGERPRINT_B,
            storedPin = FINGERPRINT_A,
            enrollmentExpecting = null,
        )

        assertEquals(ServerTrustDecision.RejectMismatch, decision)
    }

    @Test
    fun `every private host shape may enroll a user-approved certificate`() {
        val privateHosts = listOf(
            "10.0.0.4",
            "10.0.2.2",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.10",
            "127.0.0.1",
            "localhost",
            "LOCALHOST",
            "tday-box.local",
            "169.254.10.20",
            "::1",
            "0:0:0:0:0:0:0:1",
            "[::1]",
            "fd12:3456:789a::1",
            "fc00::1",
            "fe80::1%wlan0",
        )

        privateHosts.forEach { host ->
            val decision = decideServerTrust(
                host = host,
                fingerprint = FINGERPRINT_A,
                storedPin = null,
                enrollmentExpecting = FINGERPRINT_A,
            )

            assertEquals(host, ServerTrustDecision.Enroll(FINGERPRINT_A), decision)
        }
    }

    @Test
    fun `hosts that only look private are still public`() {
        val publicHosts = listOf(
            // Not RFC1918 despite the leading octets.
            "172.15.0.1",
            "172.32.0.1",
            "192.169.1.1",
            "11.0.0.1",
            // A public name that merely embeds a private-looking label.
            "10.0.0.1.attacker.com",
            "localhost.attacker.com",
            "notlocal",
            "evil.local.attacker.com",
            "tday.example.com",
            // Public IPv6 and an IPv4-shaped string that is not an address.
            "2001:db8::1",
            "999.1.1.1",
            "10.0.0",
        )

        publicHosts.forEach { host ->
            val decision = decideServerTrust(
                host = host,
                fingerprint = FINGERPRINT_A,
                storedPin = null,
                enrollmentExpecting = FINGERPRINT_A,
            )

            assertEquals(host, ServerTrustDecision.RejectPublicHost, decision)
        }
    }

    private companion object {
        const val FINGERPRINT_A = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        const val FINGERPRINT_B = "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
        const val LAN_HOST = "192.168.1.50"
    }
}
