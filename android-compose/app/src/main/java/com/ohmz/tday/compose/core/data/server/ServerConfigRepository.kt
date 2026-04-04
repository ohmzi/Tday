package com.ohmz.tday.compose.core.data.server

import android.util.Log
import com.ohmz.tday.compose.BuildConfig
import com.ohmz.tday.compose.core.data.SecureConfigStore
import com.ohmz.tday.compose.core.data.ServerProbeException
import com.ohmz.tday.compose.core.data.extractApiErrorMessage
import com.ohmz.tday.compose.core.data.requireApiBody
import com.ohmz.tday.compose.core.network.TdayApiService
import com.ohmz.tday.compose.core.security.ProbeDecryptor
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Response
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConfigRepository @Inject constructor(
    private val api: TdayApiService,
    private val secureConfigStore: SecureConfigStore,
) {
    fun hasServerConfigured(): Boolean = secureConfigStore.hasServerUrl()

    fun getServerUrl(): String? = secureConfigStore.getServerUrl()

    data class ProbeResult(
        val serverUrl: String,
        val versionCheck: VersionCheckResult,
        val backendVersion: String?,
    )

    suspend fun saveServerUrl(rawUrl: String): Result<String> =
        probeAndSave(rawUrl).map { it.serverUrl }

    suspend fun probeAndSave(rawUrl: String): Result<ProbeResult> = runCatching {
        val normalizedServerUrl = secureConfigStore.normalizeServerUrl(rawUrl)
            ?: throw ServerProbeException.InvalidUrl()
        val parsedServerUrl = normalizedServerUrl.toHttpUrlOrNull()
            ?: throw ServerProbeException.InvalidUrl()

        ensureSecureTransport(parsedServerUrl)

        val probeUrl = parsedServerUrl.newBuilder()
            .encodedPath(PROBE_PATH)
            .query(null)
            .fragment(null)
            .build()
            .toString()

        val probeResponse = withTimeout(PROBE_TIMEOUT_MS) {
            api.probeServer(probeUrl = probeUrl)
        }

        if (!probeResponse.isSuccessful) {
            throw IllegalStateException(
                extractApiErrorMessage(
                    probeResponse,
                    "Could not verify server. Check URL and try again.",
                ),
            )
        }

        val probeBody = probeResponse.body()
            ?: throw ServerProbeException.NotTdayServer()

        validateProbeContract(probeBody)
        verifyAndPersistServerTrust(parsedServerUrl, probeResponse)

        val saved = secureConfigStore.saveServerUrl(
            rawUrl = normalizedServerUrl,
            persist = false,
        ).getOrThrow()
        secureConfigStore.clearOfflineSyncState()

        val compatibility = probeBody.encryptedCompatibility?.let { ProbeDecryptor.decrypt(it) }
        val versionCheck = checkVersionCompatibility(compatibility)

        ProbeResult(
            serverUrl = saved,
            versionCheck = versionCheck,
            backendVersion = compatibility?.appVersion,
        )
    }

    data class VersionRecheckResult(
        val versionCheck: VersionCheckResult,
        val serverAppVersion: String?,
    )

    suspend fun recheckVersion(): VersionRecheckResult {
        val serverUrl = getServerUrl()
            ?: return VersionRecheckResult(VersionCheckResult.Compatible, null)
        val parsedServerUrl = serverUrl.toHttpUrlOrNull()
            ?: return VersionRecheckResult(VersionCheckResult.Compatible, null)
        val probeUrl = parsedServerUrl.newBuilder()
            .encodedPath(PROBE_PATH)
            .query(null)
            .fragment(null)
            .build()
            .toString()

        val probeResponse = runCatching {
            withTimeout(PROBE_TIMEOUT_MS) { api.probeServer(probeUrl = probeUrl) }
        }.getOrNull()
            ?: return VersionRecheckResult(VersionCheckResult.Compatible, null)

        val body = probeResponse.body()
            ?: return VersionRecheckResult(VersionCheckResult.Compatible, null)
        val compatibility = body.encryptedCompatibility?.let { ProbeDecryptor.decrypt(it) }
        return VersionRecheckResult(
            versionCheck = checkVersionCompatibility(compatibility),
            serverAppVersion = compatibility?.appVersion,
        )
    }

    fun resetTrustedServer(rawUrl: String): Result<Unit> {
        return secureConfigStore.clearTrustedServerFingerprintForUrl(rawUrl)
    }

    private fun validateProbeContract(probeBody: com.ohmz.tday.compose.core.model.MobileProbeResponse) {
        val serviceOk = probeBody.service.equals("tday", ignoreCase = true)
        val versionOk = probeBody.version == "1"

        if (serviceOk && versionOk) return

        Log.w(
            LOG_TAG,
            "probe_failed_contract service=${probeBody.service} version=${probeBody.version} probe=${probeBody.probe}",
        )
        throw ServerProbeException.NotTdayServer()
    }

    private fun verifyAndPersistServerTrust(
        serverUrl: HttpUrl,
        probeResponse: Response<*>,
    ) {
        if (serverUrl.scheme != "https") return

        val serverTrustKey = secureConfigStore.serverTrustKeyForUrl(serverUrl.toString())
            ?: throw ServerProbeException.InvalidUrl()

        val certificate = probeResponse.raw()
            .handshake
            ?.peerCertificates
            ?.firstOrNull() as? X509Certificate
            ?: throw IllegalStateException("TLS certificate not available for server trust check")

        val fingerprint = certificatePublicKeyFingerprint(certificate)
        val trustedFingerprint = secureConfigStore.getTrustedServerFingerprint(serverTrustKey)

        if (trustedFingerprint.isNullOrBlank()) {
            secureConfigStore.saveTrustedServerFingerprint(
                serverTrustKey = serverTrustKey,
                fingerprint = fingerprint,
            )
            return
        }

        if (!trustedFingerprint.equals(fingerprint, ignoreCase = true)) {
            throw ServerProbeException.CertificateChanged(serverTrustKey)
        }
    }

    private fun ensureSecureTransport(serverUrl: HttpUrl) {
        if (serverUrl.scheme == "https") return
        if (BuildConfig.DEBUG && isLocalDevelopmentHost(serverUrl.host)) return
        throw ServerProbeException.InsecureTransport()
    }

    private fun isLocalDevelopmentHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        if (normalizedHost == "localhost") return true
        if (normalizedHost == "10.0.2.2") return true
        if (normalizedHost.endsWith(".local")) return true
        if (normalizedHost.matches(Regex("^127\\.\\d+\\.\\d+\\.\\d+$"))) return true
        if (normalizedHost.matches(Regex("^10\\.\\d+\\.\\d+\\.\\d+$"))) return true
        if (normalizedHost.matches(Regex("^192\\.168\\.\\d+\\.\\d+$"))) return true
        return normalizedHost.matches(Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+$"))
    }

    private fun certificatePublicKeyFingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(certificate.publicKey.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private companion object {
        const val LOG_TAG = "ServerConfigRepo"
        const val PROBE_TIMEOUT_MS = 7_000L
        const val PROBE_PATH = "/api/mobile/probe"
    }
}
