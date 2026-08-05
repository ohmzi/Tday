package com.ohmz.tday.compose.core.network

import com.ohmz.tday.compose.core.data.SecureConfigStore
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Server-certificate trust for every OkHttp connection.
 *
 * Certificates that validate against the system (public CA) store use standard CA validation — no
 * pinning — so the owner's Cloudflare-fronted host connects with zero prompts and routine renewals
 * never false-trip. Anything the system cannot vouch for (a self-signed / privately issued
 * certificate) is refused unless it matches the pin stored for that server, or the user has just
 * approved that exact fingerprint on screen. The refusal reason is recorded per server so the setup
 * screen can tell "certificate changed" from "certificate not recognised yet" and show the
 * fingerprint being asked about.
 */
@Singleton
class ServerTrustManager @Inject constructor(
    private val secureConfigStore: SecureConfigStore,
) : X509ExtendedTrustManager() {

    private val platform: X509ExtendedTrustManager = platformTrustManager()

    /** Trust key -> the exact fingerprint the user approved, consumed by the next handshake. */
    private val enrollmentExpectations = ConcurrentHashMap<String, String>()

    /** Trust key -> the fingerprint offered by a server whose certificate was refused as unknown. */
    private val unknownFingerprints = ConcurrentHashMap<String, String>()

    /** Trust keys whose stored pin did not match the certificate presented. */
    private val mismatchedServers = ConcurrentHashMap.newKeySet<String>()

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        platform.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) =
        platform.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) =
        platform.checkClientTrusted(chain, authType, engine)

    /**
     * Reached only when the peer host is unknown, so no pin can be looked up: system validation is
     * the only answer available here.
     */
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        platform.checkServerTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        val session = (socket as? SSLSocket)?.handshakeSession
        checkServer(chain, session?.peerHost, session?.peerPort ?: -1) {
            platform.checkServerTrusted(chain, authType, socket)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        checkServer(chain, engine?.peerHost, engine?.peerPort ?: -1) {
            platform.checkServerTrusted(chain, authType, engine)
        }
    }

    /**
     * Authorises pinning one specific fingerprint for [serverTrustKey] on the next handshake.
     *
     * Call this **only** after the user has seen that fingerprint and confirmed it. It is consumed
     * by the next handshake for the server, so an approval cannot be reused, and it names the
     * expected value — a certificate swapped between the prompt and the retry still fails.
     */
    fun allowEnrollment(serverTrustKey: String, expecting: String) {
        enrollmentExpectations[serverTrustKey] = expecting
    }

    fun cancelEnrollment(serverTrustKey: String) {
        enrollmentExpectations.remove(serverTrustKey)
    }

    /** Returns (and clears) whether the last handshake with this server failed the stored pin. */
    fun consumeMismatch(serverTrustKey: String): Boolean = mismatchedServers.remove(serverTrustKey)

    /**
     * Returns (and clears) the fingerprint offered by a server whose certificate was refused for
     * being unrecognised, so the setup screen can show it and ask the user to confirm.
     */
    fun consumeUnknownFingerprint(serverTrustKey: String): String? =
        unknownFingerprints.remove(serverTrustKey)

    /**
     * True when [session]'s certificate is exactly the pin stored for [hostname]. Lets the pinned
     * self-signed case through OkHttp's hostname verifier, which such certificates routinely fail;
     * the fingerprint is the identity check in that case.
     */
    fun isPinnedSession(hostname: String, session: SSLSession): Boolean {
        val storedPin = secureConfigStore
            .getTrustedServerFingerprint(serverTrustKey(hostname, session.peerPort))
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
            ?: return false
        return storedPin.equals(publicKeyFingerprint(leaf), ignoreCase = true)
    }

    private fun checkServer(
        chain: Array<out X509Certificate>,
        host: String?,
        port: Int,
        systemCheck: () -> Unit,
    ) {
        val serverTrustKey = host?.let { serverTrustKey(it, port) }

        try {
            systemCheck()
            // A system-trusted chain is accepted without consulting the pin, but the pin is
            // deliberately NOT cleared here. This runs before OkHttp's hostname check, so an
            // on-path attacker holding any valid certificate for a host they own could otherwise
            // force a stored pin to be dropped (that connection still dies on hostname
            // verification) and downgrade the next attempt from "certificate changed" to a
            // first-run "trust this certificate?" prompt. A stale pin costs nothing: it is never
            // consulted while system validation succeeds. Clearing stays a user action
            // ("Reset trusted server").
            return
        } catch (systemFailure: CertificateException) {
            if (serverTrustKey == null) throw systemFailure

            val fingerprint = chain.firstOrNull()?.let { publicKeyFingerprint(it) }
            val decision = decideServerTrust(
                host = host,
                fingerprint = fingerprint,
                storedPin = secureConfigStore.getTrustedServerFingerprint(serverTrustKey),
                enrollmentExpecting = enrollmentExpectations.remove(serverTrustKey),
            )

            when (decision) {
                is ServerTrustDecision.Accept -> return
                is ServerTrustDecision.Enroll -> {
                    secureConfigStore.saveTrustedServerFingerprint(
                        serverTrustKey = serverTrustKey,
                        fingerprint = decision.fingerprint,
                    )
                    return
                }
                is ServerTrustDecision.RejectMismatch -> {
                    mismatchedServers.add(serverTrustKey)
                    throw CertificateException(
                        "Server certificate changed for $serverTrustKey",
                        systemFailure,
                    )
                }
                is ServerTrustDecision.RejectUnknown -> {
                    if (fingerprint != null) unknownFingerprints[serverTrustKey] = fingerprint
                    throw systemFailure
                }
                is ServerTrustDecision.RejectPublicHost -> {
                    // Deliberately record nothing: the setup screen builds its "trust this
                    // certificate?" prompt out of unknownFingerprints, and on a public host that
                    // prompt is the attack. Leaving it empty keeps the failure unrecoverable
                    // in-app, which is the old (safe) behaviour for public hosts.
                    throw systemFailure
                }
            }
        }
    }

    private companion object {
        fun platformTrustManager(): X509ExtendedTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers
                .filterIsInstance<X509ExtendedTrustManager>()
                .firstOrNull()
                ?: throw IllegalStateException("No platform X509ExtendedTrustManager available")
        }
    }
}

/**
 * Same shape as [SecureConfigStore.serverTrustKeyForUrl] so pins are looked up under one key
 * whether the caller starts from a URL or from a live socket.
 */
internal fun serverTrustKey(host: String, port: Int): String {
    val normalizedHost = host.lowercase()
    return if (port <= 0 || port == 443) "https://$normalizedHost" else "https://$normalizedHost:$port"
}

/** SHA-256 of the certificate's public key, uppercase hex — the format shown to the user. */
internal fun publicKeyFingerprint(certificate: X509Certificate): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
    return digest.joinToString(":") { "%02X".format(it) }
}
