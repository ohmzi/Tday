package com.ohmz.tday.compose.core.network

/** The outcome for a server certificate the system trust store could not verify. */
sealed interface ServerTrustDecision {
    /** Matches the pin already stored for this server. */
    data object Accept : ServerTrustDecision

    /** The user approved this exact fingerprint; store it and proceed. */
    data class Enroll(val fingerprint: String) : ServerTrustDecision

    /** A pin exists and this certificate is not it — the server changed, or someone is in the middle. */
    data object RejectMismatch : ServerTrustDecision

    /** No pin, no approval, or no usable fingerprint. Refuse and let the UI offer enrollment. */
    data object RejectUnknown : ServerTrustDecision

    /**
     * The host is publicly routable, so a certificate the public CAs will not vouch for is simply
     * wrong. Refuse and — unlike [RejectUnknown] — surface nothing the UI could turn into a
     * "trust this certificate" prompt.
     */
    data object RejectPublicHost : ServerTrustDecision
}

/**
 * Decides whether a certificate the system CA store rejected may still be trusted.
 *
 * Pure so the rules can be unit-tested without fabricating an X509 chain. Mirrors the iOS
 * `NetworkConfiguration.decideTrust`: nothing is trusted unless it matches a stored pin, or the
 * user has just approved this exact fingerprint on screen. There is deliberately no
 * trust-on-first-use and no automatic re-enrollment on mismatch — either would silently accept
 * the certificate an on-path attacker presents.
 *
 * Enrollment is further limited to private/LAN [host]s. Self-signed certificates are a LAN-only
 * situation (the owner's box on their own network); a publicly routable host that fails public CA
 * validation is either misconfigured or being intercepted. Offering a tappable "trust this
 * certificate" prompt there would hand an attacker on hostile wifi the one thing they need, so
 * public hosts get [ServerTrustDecision.RejectPublicHost] with no way through. An already-stored
 * pin is still honoured for any host: it was established out of band and an on-path attacker
 * cannot match it, so it stays a fail-closed comparison rather than a prompt.
 */
fun decideServerTrust(
    host: String?,
    fingerprint: String?,
    storedPin: String?,
    enrollmentExpecting: String?,
): ServerTrustDecision {
    // A certificate whose fingerprint cannot be derived can never be pinned or compared, so it
    // must never be accepted.
    if (fingerprint.isNullOrBlank()) return ServerTrustDecision.RejectUnknown

    if (!storedPin.isNullOrBlank()) {
        return if (storedPin.equals(fingerprint, ignoreCase = true)) {
            ServerTrustDecision.Accept
        } else {
            ServerTrustDecision.RejectMismatch
        }
    }

    // Fail closed on an unresolvable host: without knowing where we are, assume the hostile case.
    if (!isPrivateNetworkHost(host)) return ServerTrustDecision.RejectPublicHost

    if (!enrollmentExpecting.isNullOrBlank() && enrollmentExpecting.equals(fingerprint, ignoreCase = true)) {
        return ServerTrustDecision.Enroll(fingerprint)
    }

    return ServerTrustDecision.RejectUnknown
}

/**
 * True when [host] can only be reached from the local network, so a privately issued certificate
 * is a legitimate setup rather than a sign of interception.
 *
 * Covers RFC1918 IPv4, loopback, the emulator's host alias, IPv4 link-local, mDNS `.local` names,
 * and the IPv6 equivalents (loopback, `fc00::/7` unique-local, `fe80::/10` link-local). Anything
 * else — including every DNS name that is not `.local` — is treated as publicly routable.
 */
fun isPrivateNetworkHost(host: String?): Boolean {
    val normalized = host?.trim()?.trim('[', ']')?.lowercase()?.takeIf { it.isNotBlank() }
        ?: return false

    if (normalized == "localhost") return true
    // The Android emulator reaches the developer machine's loopback through this alias.
    if (normalized == "10.0.2.2") return true
    if (normalized == "local" || normalized.endsWith(".local")) return true

    if (normalized.contains(':')) return isPrivateIpv6Host(normalized)

    val octets = normalized.split('.')
    if (octets.size != 4) return false
    val parsed = octets.map { it.toIntOrNull() ?: return false }
    if (parsed.any { it !in 0..255 }) return false

    val (first, second) = parsed
    return when {
        first == 127 -> true
        first == 10 -> true
        first == 192 && second == 168 -> true
        first == 172 && second in 16..31 -> true
        // RFC3927 link-local (169.254/16): unroutable, so LAN-only by definition.
        first == 169 && second == 254 -> true
        else -> false
    }
}

private fun isPrivateIpv6Host(host: String): Boolean {
    // Drop any zone index ("fe80::1%wlan0") before classifying.
    val address = host.substringBefore('%')
    if (address == "::1") return true
    // Loopback also arrives fully expanded from SSLSession.getPeerHost().
    if (address.split(':').filter { it.isNotEmpty() }.let { groups ->
            groups.size == 8 && groups.dropLast(1).all { it.toIntOrNull(16) == 0 } &&
                groups.last().toIntOrNull(16) == 1
        }
    ) {
        return true
    }

    val firstGroup = address.substringBefore(':').takeIf { it.isNotBlank() } ?: return false
    val value = firstGroup.toIntOrNull(16) ?: return false
    // fc00::/7 unique-local and fe80::/10 link-local.
    if (value shr 9 == 0x7e) return true
    return value and 0xffc0 == 0xfe80
}
